package org.pms.silverocean.service.auth;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.RegistrationChannel;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.EmailPasswordDTO;
import org.pms.silverocean.controller.wrappers.LoginResponseDTO;
import org.pms.silverocean.controller.wrappers.RegistrationDTO;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.affiliate.AffiliateService;
import org.pms.silverocean.service.auth.roles.RoleService;
import org.pms.silverocean.service.auth.totp.TotpService;
import org.pms.silverocean.service.auth.totp.TotpServiceFactory;
import org.pms.silverocean.service.auth.totp.impl.OtpType;
import org.pms.silverocean.service.geolocation.GeoLocationResponse;
import org.pms.silverocean.service.geolocation.GeoLocationService;
import org.pms.silverocean.service.kyc.AccountStatus;
import org.pms.silverocean.service.users.ProfileType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
public class UserAuthenticationService {
    static final Duration REFRESH_TOKEN_REPLAY_WINDOW = Duration.ofSeconds(60);
    private static final int MIN_REFRESH_REQUEST_ID_LENGTH = 32;
    private static final int MAX_REFRESH_REQUEST_ID_LENGTH = 128;

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final GeoLocationService geolocationService;
    private final RoleService roleService;
    private final GoogleAuthService googleAuthService;

    private final I18NService i18NService;

    private final LoginAttemptService loginAttemptService;

    private final JwtService jwtService;

    private final TotpService totpService;
    private final AffiliateService affiliateService;
    private final RefreshTokenReplayService refreshTokenReplayService;
    private final Set<String> signUpCache = Collections.synchronizedSet(new HashSet<>());

    @Autowired
    public UserAuthenticationService(UserDao userDao, PasswordEncoder passwordEncoder, GeoLocationService geolocationService, RoleService roleService, GoogleAuthService googleAuthService, I18NService i18NService, LoginAttemptService loginAttemptService, JwtService jwtService, TotpServiceFactory totpServiceFactory, AffiliateService affiliateService, RefreshTokenReplayService refreshTokenReplayService) {
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
        this.geolocationService = geolocationService;
        this.roleService = roleService;
        this.googleAuthService = googleAuthService;
        this.i18NService = i18NService;
        this.loginAttemptService = loginAttemptService;
        this.jwtService = jwtService;
        this.totpService = totpServiceFactory.getService(OtpType.EMAIL).orElseThrow();
        this.affiliateService = affiliateService;
        this.refreshTokenReplayService = refreshTokenReplayService;
    }

    public ResponseDTO register(RegistrationDTO registrationDTO, String ipAddress) {
        String normalizedEmail = StringUtils.trimToEmpty(registrationDTO.getEmail()).toLowerCase(Locale.ROOT);
        registrationDTO.setEmail(normalizedEmail);
        recoverEmailBoundInvitation(registrationDTO, normalizedEmail);
        if (accountType(registrationDTO.getProfileType()) == ProfileType.COMPANY
                && StringUtils.isBlank(registrationDTO.getOrganizationName())) {
            return new ResponseDTO(false, ResponseCode.REGISTRATION_FAILED.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.REGISTRATION_FAILED));
        }
        Optional<Users> checkIfUserExists;
        try {
            if (!signUpCache.add(normalizedEmail)) {
                return new ResponseDTO(false, ResponseCode.DUPLICATE_USER_DETAILS.getCode(), i18NService.getLocalizedMessage(ResponseCode.DUPLICATE_USER_DETAILS));
            }
            checkIfUserExists = userDao.findByEmail(normalizedEmail);
        } catch (Exception e) {
            log.error("Failed running SQL to find user", e);
            signUpCache.remove(normalizedEmail);
            return new ResponseDTO(false, ResponseCode.LOGIN_ERROR.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOGIN_ERROR));
        }
        if (checkIfUserExists.isPresent()) {
            signUpCache.remove(normalizedEmail);
            Users existingUser = checkIfUserExists.get();
            if (!existingUser.isActive()
                    && !existingUser.isEmailVerified()
                    && passwordEncoder.matches(registrationDTO.getPassword(), existingUser.getPassword())) {
                if (StringUtils.isNotBlank(registrationDTO.getToken()) && existingUser.getInviteId() == null) {
                    roleService.assignRoleFromInvite(registrationDTO.getToken(), existingUser);
                }
                return sendRegistrationVerification(normalizedEmail);
            }
            return new ResponseDTO(false, ResponseCode.DUPLICATE_USER_DETAILS.getCode(), i18NService.getLocalizedMessage(ResponseCode.DUPLICATE_USER_DETAILS));
        }
        if (registrationDTO.getRoleId() == null && StringUtils.isBlank(registrationDTO.getToken())) {
            log.error("Local Register; could not register new user, missing role id");
            signUpCache.remove(normalizedEmail);
            return new ResponseDTO(false, ResponseCode.REGISTRATION_FAILED.getCode(), i18NService.getLocalizedMessage(ResponseCode.REGISTRATION_FAILED));
        }
        if (StringUtils.isNotBlank(registrationDTO.getReferralCode())) affiliateService.resolve(registrationDTO.getReferralCode());
        Users user = Users.builder().email(normalizedEmail)
                .password(passwordEncoder.encode(registrationDTO.getPassword()))
                .registrationIP(ipAddress)
                .source(RegistrationChannel.LOCAL.name())
                .locale(LocaleContextHolder.getLocale().getLanguage())
                .accountStatus(AccountStatus.PENDING_EMAIL_VERIFICATION.name())
                .profileType(accountType(registrationDTO.getProfileType()).name())
                .organizationName(organizationName(registrationDTO.getProfileType(), registrationDTO.getOrganizationName()))
                .fullName(registrationDTO.getFullName().trim()).build();
        try {
            setLocationDetailsBasedOnIP(user);
            saveAndAssignRole(registrationDTO.getRoleId(), user, registrationDTO.getToken());
            if (StringUtils.isNotBlank(registrationDTO.getReferralCode())) {
                try {
                    affiliateService.attributeRegistration(registrationDTO.getReferralCode(), user.getId(), registrationDTO.getReferralCampaign());
                } catch (Exception attributionError) {
                    log.warn("User {} registered, but referral attribution could not be completed", user.getId(), attributionError);
                }
            }
        } catch (Exception e) {
            log.error("Could not register new user", e);
            signUpCache.remove(normalizedEmail);
            if (e instanceof PMSCustomException) {
                throw (PMSCustomException) e;
            }
            return new ResponseDTO(false, ResponseCode.REGISTRATION_FAILED.getCode(), i18NService.getLocalizedMessage(ResponseCode.REGISTRATION_FAILED));
        }
        try {
            return sendRegistrationVerification(normalizedEmail);
        } finally {
            signUpCache.remove(normalizedEmail);
        }
    }

    private ResponseDTO sendRegistrationVerification(String email) {
        try {
            String message = totpService.generateOTPCode(email);
            return new ResponseDTO(true, ResponseCode.EMAIL_OTP_GENERATED.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.EMAIL_OTP_GENERATED), message);
        } catch (Exception e) {
            log.error("Could not send registration verification code", e);
            return new ResponseDTO(false, ResponseCode.LOGIN_FAILURE_VERIFICATION_REQUIRED.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.LOGIN_FAILURE_VERIFICATION_REQUIRED));
        }
    }

    /**
     * Saves the new user and assigns role. Role is checked against the role selected during registration.
     * In case user was invited as property_manager, guard i.e. roles that can't self assign, role from invite is used.
     * In the case user self assigned a role but was also using an invite link, the self assign role is used.
     * @param roleId
     * @param user
     * @param inviteToken
     */
    private void saveAndAssignRole(Long roleId, Users user, String inviteToken) {
        if (StringUtils.isBlank(inviteToken)) {
            assert roleId != null;
            roleService.saveUserAndAssignRoleOnRegistration(roleId, user);
        } else {
            roleService.saveUserAndAssignRoleFromInvite(inviteToken, roleId, user);
        }
    }

    public ResponseDTO login(EmailPasswordDTO emailPasswordDTO) {
        String normalizedEmail = StringUtils.trimToEmpty(emailPasswordDTO.getEmail()).toLowerCase(Locale.ROOT);
        emailPasswordDTO.setEmail(normalizedEmail);
        loginAttemptService.assertLoginAllowed(normalizedEmail);
        Optional<Users> checkIfUserExists;
        try {
            checkIfUserExists = userDao.findByEmail(normalizedEmail);
        } catch (Exception e) {
            log.error("Failed running SQL to find user", e);
            loginAttemptService.loginFailed(normalizedEmail);
            return new ResponseDTO(false, ResponseCode.LOAD_USER_ERROR.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOAD_USER_ERROR));
        }

        if (checkIfUserExists.isEmpty()) {
            loginAttemptService.loginFailed(normalizedEmail);
            return new ResponseDTO(false, ResponseCode.LOGIN_FAILURE_INVALID_USER.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOGIN_FAILURE_INVALID_USER));
        }
        Users users = checkIfUserExists.get();
        if (passwordEncoder.matches(emailPasswordDTO.getPassword(), users.getPassword())) {
            if (!users.isActive()) {
                if (users.isMfaSetup()) {
                    return new ResponseDTO(false, ResponseCode.LOGIN_FAILURE_INACTIVE_USER.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOGIN_FAILURE_INACTIVE_USER));
                } else {
                    try {
                        if (StringUtils.isNotBlank(emailPasswordDTO.getToken()) && users.getInviteId() == null) {
                            roleService.assignRoleFromInvite(emailPasswordDTO.getToken(), users);
                        }
                        String message = totpService.generateOTPCode(normalizedEmail);
                        return new ResponseDTO(true, ResponseCode.EMAIL_OTP_GENERATED.getCode(), i18NService.getLocalizedMessage(ResponseCode.EMAIL_OTP_GENERATED), message);
                    } catch (Exception e) {
                        return new ResponseDTO(false, ResponseCode.LOGIN_FAILURE_VERIFICATION_REQUIRED.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOGIN_FAILURE_VERIFICATION_REQUIRED));
                    }
                }
            }
            if (StringUtils.isNotBlank(emailPasswordDTO.getToken())) {
                roleService.assignRoleFromInvite(emailPasswordDTO.getToken(), users);
            } else {
                recoverPendingTenantRole(users);
            }
            RefreshTokenReplayService.Replacement refreshSession = issueFreshRefreshSession(users);
            users.setLastLogin(ZonedDateTime.now());
            userDao.save(users);
            loginAttemptService.loginSuccess(normalizedEmail);
            return new ResponseDTO(true, ResponseCode.LOGIN_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOGIN_SUCCESS),
                    new LoginResponseDTO(!PMSUtils.isByteArrayEmpty(users.getTotpSecret()),
                    users.isMfaSetup(), jwtService.generateJWT(users.getEmail()),
                            refreshSession.refreshToken(), refreshSession.requestId()));
        }
        loginAttemptService.loginFailed(normalizedEmail);
        return new ResponseDTO(false, ResponseCode.LOGIN_FAILURE_INCORRECT_PASSWORD.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOGIN_FAILURE_INCORRECT_PASSWORD));
    }

    /**
     * Establishes a complete authenticated session after a successful OTP
     * verification. A JWT without a persisted refresh token is intentionally
     * rejected by {@code JWTFilter}, so OTP verification must issue both.
     */
    public LoginResponseDTO createSessionForVerifiedUser(String email) {
        Users user = userDao.findByEmail(email)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LOGIN_FAILURE_INVALID_USER));
        if (!user.isActive()) {
            throw new PMSCustomException(ResponseCode.LOGIN_FAILURE_INACTIVE_USER);
        }
        roleService.completeDeferredInvite(user);
        RefreshTokenReplayService.Replacement refreshSession = issueFreshRefreshSession(user);
        user.setLastLogin(ZonedDateTime.now());
        userDao.save(user);
        loginAttemptService.loginSuccess(email);
        return new LoginResponseDTO(!PMSUtils.isByteArrayEmpty(user.getTotpSecret()), user.isMfaSetup(),
                jwtService.generateJWT(user.getEmail()), refreshSession.refreshToken(), refreshSession.requestId());
    }

    /**
     * Receives a unique Google token from the Angular client, sends to Google for look up and get back a validatedGoogleUser
     * The validatedGoogleUser can either be a new user or a returning user
     *
     * @param googleIdToken The JWT from a successful Google auth
     * @param ipAddress     The IP of the browser sending the request
     * @return a response of success with new JWT or failure
     */
    public ResponseDTO googleLogin(String googleIdToken, Long roleId, String token, String referralCode, String referralCampaign,
                                   ProfileType profileType, String organizationName, String ipAddress) {
        Optional<Users> validatedGoogleUser = googleAuthService.verifyIdToken(googleIdToken);
        if (validatedGoogleUser.isPresent()) {
            Optional<Users> checkIfUserExists;
            try {
                Users googleUser = validatedGoogleUser.get();
                if (signUpCache.contains(googleUser.getEmail())) {
                    return new ResponseDTO(false, ResponseCode.DUPLICATE_USER_DETAILS.getCode(), i18NService.getLocalizedMessage(ResponseCode.DUPLICATE_USER_DETAILS));
                }
                signUpCache.add(googleUser.getEmail());
                checkIfUserExists = userDao.findByEmail(googleUser.getEmail());
                RefreshTokenReplayService.Replacement refreshSession = newFreshRefreshCredentials();
                if (checkIfUserExists.isEmpty()) {
                    if (roleId == null && StringUtils.isBlank(token)) {
                        log.error("Could not register new user, missing role id");
                        signUpCache.remove(googleUser.getEmail());
                        return new ResponseDTO(false, ResponseCode.REGISTRATION_FAILED.getCode(), i18NService.getLocalizedMessage(ResponseCode.REGISTRATION_FAILED));
                    }
                    try {
                        if (StringUtils.isNotBlank(referralCode)) affiliateService.resolve(referralCode);
                        googleUser.setRegistrationIP(ipAddress);
                        googleUser.setProfileType(accountType(profileType).name());
                        googleUser.setOrganizationName(organizationName(profileType, organizationName));
                        googleUser.setLocale(LocaleContextHolder.getLocale().getLanguage());
                        googleUser.setEmailVerified(true);
                        googleUser.setActive(true);
                        googleUser.setAccountStatus(AccountStatus.PENDING_KYC.name());
                        replaceRefreshSession(googleUser, refreshSession);
                        googleUser.setLastLogin(ZonedDateTime.now());
                        setLocationDetailsBasedOnIP(googleUser);
                        saveAndAssignRole(roleId, googleUser, token);
                        affiliateService.attributeRegistration(referralCode, googleUser.getId(), referralCampaign);
                    } catch (Exception e) {
                        signUpCache.remove(googleUser.getEmail());
                        log.error("Could not register new user", e);
                        return new ResponseDTO(false, ResponseCode.REGISTRATION_FAILED.getCode(), i18NService.getLocalizedMessage(ResponseCode.REGISTRATION_FAILED));
                    }
                } else {
                    googleUser = checkIfUserExists.get();
                    replaceRefreshSession(googleUser, refreshSession);
                    googleUser.setLastLogin(ZonedDateTime.now());
                    if (!googleUser.isActive()) {
                        googleUser.setEmailVerified(true);
                        googleUser.setActive(true);
                        if (!AccountStatus.ACTIVE.name().equals(googleUser.getAccountStatus())) {
                            googleUser.setAccountStatus(AccountStatus.PENDING_KYC.name());
                        }
                    }
                    if (StringUtils.isNotBlank(token)) {
                        roleService.assignRoleFromInvite(token, googleUser);
                    } else {
                        recoverPendingTenantRole(googleUser);
                    }
                    userDao.save(googleUser);
                }
                signUpCache.remove(googleUser.getEmail());
                return new ResponseDTO(true, ResponseCode.LOGIN_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOGIN_SUCCESS),
                        new LoginResponseDTO(!PMSUtils.isByteArrayEmpty(googleUser.getTotpSecret()), googleUser.isMfaSetup(),
                                jwtService.generateJWT(googleUser.getEmail()), refreshSession.refreshToken(),
                                refreshSession.requestId()));
            } catch (Exception e) {
                log.error("Failed running SQL to find user", e);
                return new ResponseDTO(false, ResponseCode.LOGIN_ERROR.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOGIN_ERROR));
            }
        } else {
            return new ResponseDTO(false, ResponseCode.GOOGLE_LOGIN_ERROR.getCode(), i18NService.getLocalizedMessage(ResponseCode.GOOGLE_LOGIN_ERROR));
        }
    }

    public void updatePassword(String email, String password) {
        String normalizedEmail = StringUtils.trimToEmpty(email).toLowerCase(Locale.ROOT);
        Users user = userDao.findByEmail(normalizedEmail)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LOGIN_FAILURE_INVALID_USER));
        // Password reset must complete before a new authenticated session is returned.
        // The former asynchronous update allowed an immediate sign-in to race the database write.
        user.setPassword(passwordEncoder.encode(password));
        userDao.save(user);
    }

    /**
     * Attaches an email-bound invitation after OTP proof and before issuing the
     * replacement session. This keeps password recovery in the tenant journey:
     * the returned JWT already contains the invited role and permissions.
     */
    @Transactional
    public void acceptInvitationForVerifiedUser(String email, String token) {
        String normalizedEmail = StringUtils.trimToEmpty(email).toLowerCase(Locale.ROOT);
        Users user = userDao.findByEmail(normalizedEmail)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LOGIN_FAILURE_INVALID_USER));
        roleService.assignRoleFromInvite(token, user);
    }

    private void recoverPendingTenantRole(Users user) {
        try {
            roleService.assignPendingTenantRoleIfMissing(user);
        } catch (RuntimeException exception) {
            // A malformed historical invitation must never lock an otherwise
            // valid customer out. It remains visible to administrators for audit.
            log.warn("Could not recover pending tenant role during sign-in for {}", user.getEmail(), exception);
        }
    }

    private void recoverEmailBoundInvitation(RegistrationDTO registrationDTO, String normalizedEmail) {
        if (registrationDTO.getRoleId() != null || StringUtils.isNotBlank(registrationDTO.getToken())) return;
        roleService.activeInviteTokenForRecipient(normalizedEmail).ifPresent(registrationDTO::setToken);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ResponseDTO loginByRefreshToken(String refreshToken) {
        return loginByRefreshToken(refreshToken, null);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ResponseDTO loginByRefreshToken(String refreshToken, String requestId) {
        String hashedToken = PMSUtils.hashToken(refreshToken);
        // Serialize refresh-token rotation. Without a row lock, concurrent browser
        // requests can both consume one token and leave the client with a mismatched
        // access/refresh pair. The same lock also serializes an exact idempotent
        // retry against the one retained predecessor.
        Optional<Users> checkIfUserExists = userDao.findByCurrentOrReplayRefreshTokenForUpdate(hashedToken);
        if (checkIfUserExists.isEmpty()) {
            return replacedOrExpiredSession();
        }
        Users users = checkIfUserExists.get();
        if (!users.isActive()) {
            return new ResponseDTO(false, ResponseCode.LOGIN_FAILURE_INACTIVE_USER.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.LOGIN_FAILURE_INACTIVE_USER));
        }
        String normalizedRequestId = normalizeReplayRequestId(requestId);
        if (!constantTimeEquals(hashedToken, users.getRefreshToken())) {
            return replayRefreshResponse(users, hashedToken, refreshToken, normalizedRequestId);
        }

        boolean replayProtectedSession = StringUtils.isNotBlank(users.getRefreshTokenRequestHash());
        if (requestId != null && normalizedRequestId == null) {
            // A caller that sends a companion id is claiming support for the
            // paired protocol. Never reinterpret a malformed id as a legacy
            // request because that would silently bypass replay protection.
            return replacedOrExpiredSession();
        }
        if (replayProtectedSession && normalizedRequestId != null
                && !constantTimeEquals(PMSUtils.hashToken(normalizedRequestId),
                users.getRefreshTokenRequestHash())) {
            // An older API instance can rotate the current token while leaving
            // companion-id columns (unknown to that binary) unchanged. Only
            // the deterministic id derived from the *new current token* may
            // repair that mixed-version state; arbitrary mismatches still fail.
            if (!refreshTokenReplayService.isLegacyTransitionRequestId(refreshToken,
                    normalizedRequestId)) {
                return replacedOrExpiredSession();
            }
        }
        if (normalizedRequestId == null) {
            // Rolling-deploy compatibility for clients released before the
            // companion request id existed. Possession of the current refresh
            // token still authorizes one rotation, but the replacement remains
            // explicitly legacy/unpaired. This lets an old client keep working
            // across repeated refreshes without pretending its response can be
            // replayed after an ambiguous network failure.
            return rotateLegacyRefreshSession(users);
        }
        // A legacy session has no stored companion-id hash. If an upgraded
        // client supplies a valid stable id, use it for this transition so the
        // first post-deploy rotation is also recoverable after a lost response.
        RefreshTokenReplayService.Replacement replacement =
                refreshTokenReplayService.issue(refreshToken, normalizedRequestId);
        rememberRefreshResponse(users, hashedToken, normalizedRequestId);
        setCurrentRefreshSession(users, replacement);
        users.setLastLogin(ZonedDateTime.now());
        userDao.save(users);
        return successfulRefresh(users, replacement);
    }

    public void logout() {
        userDao.logoutUserByUserId();
    }

    private ResponseDTO replayRefreshResponse(Users user, String consumedTokenHash,
                                              String consumedToken, String requestId) {
        ZonedDateTime expiresAt = user.getRefreshTokenReplayExpiresAt();
        String requestHash = replayRequestHash(requestId);
        if (!constantTimeEquals(consumedTokenHash, user.getRefreshTokenReplayHash())) {
            return replacedOrExpiredSession();
        }
        if (expiresAt == null || !expiresAt.isAfter(ZonedDateTime.now())) {
            clearRefreshReplay(user);
            userDao.save(user);
            return replacedOrExpiredSession();
        }
        if (requestHash == null
                || !constantTimeEquals(requestHash, user.getRefreshTokenReplayRequestHash())) {
            return replacedOrExpiredSession();
        }
        try {
            return refreshTokenReplayService.recover(consumedToken, requestId, user.getRefreshToken(),
                            user.getRefreshTokenRequestHash())
                    .map(replacement -> successfulRefresh(user, replacement))
                    .orElseGet(this::replacedOrExpiredSession);
        } catch (RuntimeException replayReadFailure) {
            log.warn("Could not recover the bounded refresh response for user {}", user.getId(), replayReadFailure);
            return replacedOrExpiredSession();
        }
    }

    private void rememberRefreshResponse(Users user, String consumedTokenHash, String requestId) {
        String requestHash = replayRequestHash(requestId);
        if (requestHash == null) {
            clearRefreshReplay(user);
            return;
        }
        user.setRefreshTokenReplayHash(consumedTokenHash);
        user.setRefreshTokenReplayRequestHash(requestHash);
        user.setRefreshTokenReplayExpiresAt(ZonedDateTime.now().plus(REFRESH_TOKEN_REPLAY_WINDOW));
    }

    private ResponseDTO successfulRefresh(Users user, RefreshTokenReplayService.Replacement replacement) {
        return new ResponseDTO(true, ResponseCode.LOGIN_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.LOGIN_SUCCESS),
                new LoginResponseDTO(!PMSUtils.isByteArrayEmpty(user.getTotpSecret()), user.isMfaSetup(),
                        jwtService.generateJWT(user.getEmail()), replacement.refreshToken(),
                        replacement.requestId()));
    }

    private ResponseDTO rotateLegacyRefreshSession(Users user) {
        String replacement = PMSUtils.randomMask();
        user.setRefreshToken(PMSUtils.hashToken(replacement));
        user.setRefreshTokenRequestHash(null);
        clearRefreshReplay(user);
        user.setLastLogin(ZonedDateTime.now());
        userDao.save(user);
        return new ResponseDTO(true, ResponseCode.LOGIN_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.LOGIN_SUCCESS),
                new LoginResponseDTO(!PMSUtils.isByteArrayEmpty(user.getTotpSecret()), user.isMfaSetup(),
                        jwtService.generateJWT(user.getEmail()), replacement, null));
    }

    private ResponseDTO replacedOrExpiredSession() {
        return new ResponseDTO(false, ResponseCode.SESSION_REPLACED_OR_EXPIRED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SESSION_REPLACED_OR_EXPIRED));
    }

    private RefreshTokenReplayService.Replacement issueFreshRefreshSession(Users user) {
        RefreshTokenReplayService.Replacement credentials = newFreshRefreshCredentials();
        replaceRefreshSession(user, credentials);
        return credentials;
    }

    private RefreshTokenReplayService.Replacement newFreshRefreshCredentials() {
        return new RefreshTokenReplayService.Replacement(PMSUtils.randomMask(), PMSUtils.randomMask());
    }

    private void replaceRefreshSession(Users user, RefreshTokenReplayService.Replacement credentials) {
        setCurrentRefreshSession(user, credentials);
        clearRefreshReplay(user);
    }

    private void setCurrentRefreshSession(Users user, RefreshTokenReplayService.Replacement credentials) {
        user.setRefreshToken(PMSUtils.hashToken(credentials.refreshToken()));
        user.setRefreshTokenRequestHash(PMSUtils.hashToken(credentials.requestId()));
    }

    private void clearRefreshReplay(Users user) {
        user.setRefreshTokenReplayHash(null);
        user.setRefreshTokenReplayRequestHash(null);
        user.setRefreshTokenReplayExpiresAt(null);
    }

    private String replayRequestHash(String requestId) {
        String normalized = normalizeReplayRequestId(requestId);
        return normalized == null ? null : PMSUtils.hashToken(normalized);
    }

    private String normalizeReplayRequestId(String requestId) {
        String normalized = StringUtils.trimToNull(requestId);
        return normalized == null || normalized.length() < MIN_REFRESH_REQUEST_ID_LENGTH
                || normalized.length() > MAX_REFRESH_REQUEST_ID_LENGTH ? null : normalized;
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    private void setLocationDetailsBasedOnIP(Users user) {
        try {
            GeoLocationResponse location = geolocationService.getLocation(user.getRegistrationIP());
            if (location == null) return;
            user.setCountry(location.countryName());
            user.setCity(location.city());
            user.setCountryCode(location.countryCode());
        } catch (Exception locationError) {
            // Location is enrichment only. An external lookup outage must never
            // prevent account creation or strand a partially-created user.
            log.warn("Continuing registration without IP geolocation for {}", user.getRegistrationIP(), locationError);
        }
    }

    private ProfileType accountType(ProfileType profileType) {
        return profileType == null ? ProfileType.INDIVIDUAL : profileType;
    }

    private String organizationName(ProfileType profileType, String organizationName) {
        return accountType(profileType) == ProfileType.COMPANY ? StringUtils.trimToNull(organizationName) : null;
    }
}
