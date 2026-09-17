package org.pms.silverocean.service.insurance;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.InsuranceGuestAccessRepo;
import org.pms.silverocean.database.pms.entities.InsuranceGuestAccess;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.helpdesk.HelpDeskRateLimiter;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.security.EncryptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import static org.pms.silverocean.service.insurance.InsuranceModels.*;

@Service
@RequiredArgsConstructor
public class InsuranceGuestAccessService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    private final InsuranceGuestAccessRepo accessRepo;
    private final InsuranceOperationsService operations;
    private final HelpDeskRateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionService encryptionService;
    private final NotificationService notifications;
    private final I18NService i18n;
    private final UserDao userDao;

    @Value("${app.public-url:https://slickhood.com}") private String publicUrl;
    @Value("${app.insurance.guest.otp-minutes:10}") private long otpMinutes;
    @Value("${app.insurance.guest.access-days:30}") private long accessDays;

    @Transactional("pmsDBTransactionManager")
    public GuestAccessChallenge requestAccess(GuestAccessRequest request, String remoteAddress) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        rateLimiter.check(NotificationService.digest("insurance-guest-email:" + email), 3);
        rateLimiter.check(NotificationService.digest("insurance-guest-ip:" + StringUtils.defaultString(remoteAddress)), 10);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        InsuranceGuestAccess access = new InsuranceGuestAccess();
        access.setChallengeId(UUID.randomUUID().toString());
        access.setFullName(request.fullName().trim());
        access.setEmail(email);
        access.setPhone(request.phone().trim());
        access.setOtpHash(passwordEncoder.encode(code));
        access.setOtpExpiresAt(LocalDateTime.now().plusMinutes(otpMinutes));
        access.setOtpAttempts(0);
        access.setCreatedBy(null);
        access.setActive(true);
        accessRepo.save(access);

        String body = String.format(i18n.getLocalizedMessage(NotificationType.EMAIL_OTP.getBody()),
                HtmlUtils.htmlEscape(code), access.getOtpExpiresAt().atZone(NAIROBI).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm z")));
        notifications.sendNotification(new NotificationDTO(body, email, NotificationType.EMAIL_OTP));
        return new GuestAccessChallenge(access.getChallengeId(),
                "If the details are valid, a six-digit verification code has been sent.");
    }

    @Transactional("pmsDBTransactionManager")
    public GuestAccessView verify(GuestAccessVerifyRequest request) {
        InsuranceGuestAccess access = accessRepo.findChallengeForUpdate(request.challengeId()).orElseThrow(this::invalidAccess);
        if (access.getVerifiedAt() != null || access.getOtpExpiresAt().isBefore(LocalDateTime.now())
                || access.getOtpAttempts() >= MAX_OTP_ATTEMPTS) {
            access.setActive(false);
            accessRepo.save(access);
            throw invalidAccess();
        }
        access.setOtpAttempts(access.getOtpAttempts() + 1);
        if (!passwordEncoder.matches(request.code(), access.getOtpHash())) {
            if (access.getOtpAttempts() >= MAX_OTP_ATTEMPTS) access.setActive(false);
            accessRepo.save(access);
            throw invalidAccess();
        }
        String token = randomToken();
        access.setVerifiedAt(LocalDateTime.now());
        access.setAccessTokenHash(hash(token));
        access.setEncryptedAccessToken(encryptionService.encrypt(token));
        access.setAccessExpiresAt(LocalDateTime.now().plusDays(accessDays));
        access.setOtpHash("VERIFIED");
        accessRepo.save(access);
        return new GuestAccessView(token, access.getAccessExpiresAt(), access.getCaseId());
    }

    public GuestCaseView caseStatus(String token) {
        InsuranceGuestAccess access = verified(token);
        if (access.getCaseId() == null) return new GuestCaseView(null, true);
        return new GuestCaseView(operations.guestCase(access.getCaseId(), access.getId()), true);
    }

    @Transactional("pmsDBTransactionManager")
    public GuestCaseView createCase(String token, CaseRequest request) {
        InsuranceGuestAccess access = verifiedForUpdate(token);
        if (access.getCaseId() != null) throw invalid();
        CaseRequest trusted = new CaseRequest(request.productCode(), access.getFullName(), access.getEmail(), access.getPhone(),
                request.subjectType(), request.subjectDescription(), request.sumInsured(), request.currency(),
                request.coverStartDate(), request.riskDetails(), request.proposalData(), request.consent());
        CaseView created = operations.createGuest(trusted, access.getId());
        access.setCaseId(created.id());
        accessRepo.save(access);
        sendPortalLink(access, created.reference());
        return new GuestCaseView(created, true);
    }

    public MarineIdfOcrView extractMarineIdf(String token, MultipartFile file) throws IOException {
        InsuranceGuestAccess access = verified(token);
        return operations.extractMarineIdfForGuest(file, access.getId());
    }

    @Transactional("pmsDBTransactionManager")
    public DocumentView uploadProposal(String token, long caseId, MultipartFile file) throws IOException {
        InsuranceGuestAccess access = verifiedForCase(token, caseId);
        return operations.uploadGuestProposal(caseId, access.getId(), file);
    }

    @Transactional("pmsDBTransactionManager")
    public GuestCaseView selectQuote(String token, long caseId, SelectQuoteRequest request) {
        InsuranceGuestAccess access = verifiedForCase(token, caseId);
        return new GuestCaseView(operations.selectGuestQuote(caseId, access.getId(), request), true);
    }

    @Transactional("pmsDBTransactionManager")
    public CaseView claim(ClaimGuestCaseRequest request, long userId) {
        InsuranceGuestAccess access = verifiedForUpdate(request.accessToken());
        if (access.getCaseId() == null || access.getClaimedAt() != null) throw invalidAccess();
        String accountEmail = userDao.findById(userId).map(user -> user.getEmail().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(this::invalidAccess);
        if (!accountEmail.equals(access.getEmail())) throw invalidAccess();
        CaseView claimed = operations.claimGuestCase(access.getCaseId(), access.getId(), userId, access.getEmail());
        access.setClaimedAt(LocalDateTime.now());
        access.setClaimedByUserId(userId);
        access.setActive(false);
        accessRepo.save(access);
        return claimed;
    }

    private InsuranceGuestAccess verifiedForCase(String token, long caseId) {
        InsuranceGuestAccess access = verified(token);
        if (!Objects.equals(access.getCaseId(), caseId)) throw invalidAccess();
        return access;
    }

    private InsuranceGuestAccess verified(String token) {
        validateTokenShape(token);
        return requireCurrent(accessRepo.findByAccessTokenHashAndActiveTrue(hash(token)).orElseThrow(this::invalidAccess));
    }

    private InsuranceGuestAccess verifiedForUpdate(String token) {
        validateTokenShape(token);
        return requireCurrent(accessRepo.findAccessForUpdate(hash(token)).orElseThrow(this::invalidAccess));
    }

    private void validateTokenShape(String token) {
        if (StringUtils.isBlank(token) || token.length() < 32 || token.length() > 200) throw invalidAccess();
    }

    private InsuranceGuestAccess requireCurrent(InsuranceGuestAccess access) {
        if (access.getVerifiedAt() == null || access.getAccessExpiresAt() == null
                || access.getAccessExpiresAt().isBefore(LocalDateTime.now()) || access.getClaimedAt() != null) throw invalidAccess();
        return access;
    }

    private void sendPortalLink(InsuranceGuestAccess access, String reference) {
        String token = encryptionService.decrypt(access.getEncryptedAccessToken()).decryptedValue();
        // Keep the bearer token in the URL fragment. Fragments are not sent to the
        // web server, reverse proxy, analytics pipeline, or referrer header.
        String link = StringUtils.removeEnd(publicUrl, "/") + "/insurance#access=" + token;
        String expires = access.getAccessExpiresAt().atZone(NAIROBI).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm z"));
        String body = String.format(i18n.getLocalizedMessage(NotificationType.INSURANCE_GUEST_ACCESS_EMAIL.getBody()),
                HtmlUtils.htmlEscape(reference), HtmlUtils.htmlEscape(link), HtmlUtils.htmlEscape(expires));
        notifications.queueEmailAndInApp(access.getEmail(), NotificationType.INSURANCE_GUEST_ACCESS_EMAIL, body,
                "INSURANCE_GUEST_REQUEST", "Your guest insurance request is ready to review securely.");
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private PMSCustomException invalidAccess() { return new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS); }
    private PMSCustomException invalid() { return new PMSCustomException(ResponseCode.INVALID_FIELD_DATA); }
}
