package org.pms.silverocean.service.privacy;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.KycCaseRepo;
import org.pms.silverocean.database.pms.KycDocumentRepo;
import org.pms.silverocean.database.pms.PrivacyRequestRepo;
import org.pms.silverocean.database.pms.UserRoleRepo;
import org.pms.silverocean.database.pms.UserSubscriptionRepo;
import org.pms.silverocean.database.pms.entities.KycDocument;
import org.pms.silverocean.database.pms.entities.PrivacyRequest;
import org.pms.silverocean.database.pms.entities.Role;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PrivacyService {
    private static final Set<String> OPEN = Set.of("SUBMITTED", "IN_REVIEW", "APPROVED", "IN_PROGRESS");
    private final PrivacyRequestRepo requests;
    private final UserRoleRepo roles;
    private final KycCaseRepo kycCases;
    private final KycDocumentRepo kycDocuments;
    private final UserSubscriptionRepo subscriptions;
    private final UserDao users;
    private final AuditLogService audit;

    @Value("${privacy.request.sla-days:30}")
    private int slaDays;

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public PrivacyModels.RequestView submit(PrivacyModels.Submit input) {
        long userId = users.getUserId();
        if (requests.existsByUserIdAndRequestTypeAndStatusInAndActiveTrue(userId, input.type().name(), OPEN)) {
            throw new PMSCustomException(ResponseCode.PRIVACY_REQUEST_DUPLICATE);
        }
        PrivacyRequest request = new PrivacyRequest();
        request.setUserId(userId);
        request.setRequestType(input.type().name());
        request.setStatus(PrivacyModels.Status.SUBMITTED.name());
        request.setReason(input.reason().trim());
        request.setDueAt(now().plusDays(slaDays));
        request.setCreatedBy(userId);
        request.setActive(true);
        requests.save(request);
        audit.createAuditLog(request, "PRIVACY_REQUEST_SUBMITTED");
        return new PrivacyModels.RequestView(request);
    }

    public Page<PrivacyModels.RequestView> myRequests(Pageable pageable) {
        return requests.findByUserIdAndActiveTrueOrderByCreatedOnDesc(users.getUserId(), pageable)
                .map(PrivacyModels.RequestView::new);
    }

    public Page<PrivacyModels.RequestView> allRequests(Pageable pageable) {
        return requests.findByActiveTrueOrderByCreatedOnDesc(pageable).map(PrivacyModels.RequestView::new);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public PrivacyModels.RequestView review(long id, PrivacyModels.Review input) {
        PrivacyRequest request = requests.findByIdForUpdate(id)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PRIVACY_REQUEST_NOT_FOUND));
        PrivacyModels.Status current = PrivacyModels.Status.valueOf(request.getStatus());
        if (!allowed(current, input.status()) ||
                (input.legalHold() && (input.retentionBasis() == null || input.retentionBasis().isBlank())) ||
                (input.status() == PrivacyModels.Status.COMPLETED && request.getRequestType().equals("ERASURE") && input.legalHold()) ||
                (input.status() == PrivacyModels.Status.COMPLETED && request.getRequestType().equals("ACCESS_EXPORT") &&
                        (input.resultReference() == null || input.resultReference().isBlank()))) {
            throw new PMSCustomException(ResponseCode.PRIVACY_REQUEST_INVALID_STATE);
        }
        request.setStatus(input.status().name());
        request.setLegalHold(input.legalHold());
        request.setRetentionBasis(trim(input.retentionBasis()));
        request.setReviewerNotes(input.reviewerNotes().trim());
        request.setResultReference(trim(input.resultReference()));
        request.setReviewedBy(users.getUserId());
        request.setReviewedAt(now());
        requests.save(request);
        audit.createAuditLog(request, "PRIVACY_REQUEST_" + input.status().name());
        return new PrivacyModels.RequestView(request);
    }

    @Transactional(readOnly = true, transactionManager = "pmsDBTransactionManager")
    public PrivacyModels.Export exportMyData() {
        long userId = users.getUserId();
        Users user = users.findById(userId).orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_USER_DETAILS));
        var userData = new PrivacyModels.UserData(user.getId(), user.getFullName(), user.getEmail(), user.getPhoneNumber(),
                user.getSource(), user.getRegistrationIP(), user.isEmailVerified(), user.isPhoneVerified(), user.getCountry(),
                user.getCity(), user.getCountryCode(), user.getLocale(), user.getLastLogin(), user.isVerified(),
                user.getProfileType(), user.getIdentificationNumber(), user.getTaxPin(), user.getCreatedOn());
        List<String> roleNames = roles.findByUserId(userId).stream().map(Role::getName).sorted().toList();
        PrivacyModels.KycData kyc = kycCases.findByUserId(userId).map(c -> {
            List<PrivacyModels.KycDocumentData> docs = kycDocuments.findByCaseIdAndActiveTrueOrderByCreatedOnDesc(c.getId()).stream()
                    .map(this::documentData).toList();
            return new PrivacyModels.KycData(c.getStatus(), c.getConsentVersion(), c.getConsentAt(), c.isPhoneVerified(),
                    c.getRegistryStatus(), c.getSubmittedAt(), c.getReviewedAt(), docs);
        }).orElse(null);
        List<PrivacyModels.SubscriptionData> plans = subscriptions.findAllByCreatedByOrderByCreatedOnDesc(userId).stream()
                .map(s -> new PrivacyModels.SubscriptionData(s.getId(), s.getRole().name(), s.getPlanCode(), s.getStatus().name(),
                        s.getStartAt(), s.getEndAt(), s.isAutoRenew(), s.getSourcePaymentRef())).toList();
        return new PrivacyModels.Export(now(),
                "This portable summary contains core account, role, KYC metadata and subscription data. Credentials, authentication secrets, document storage paths, hashes and encrypted OCR payloads are excluded for security.",
                userData, roleNames, kyc, plans);
    }

    private PrivacyModels.KycDocumentData documentData(KycDocument d) {
        return new PrivacyModels.KycDocumentData(d.getId(), d.getDocumentType(), d.getOriginalFileName(), d.getContentType(),
                d.getFileSize(), d.getWidth(), d.getHeight(), d.getQualityScore(), d.getQualityStatus(), d.getStatus(),
                d.getOcrProvider(), d.getOcrConfidence(), d.getRejectionReason(), d.getCreatedOn(), d.getReviewedAt());
    }

    private boolean allowed(PrivacyModels.Status current, PrivacyModels.Status next) {
        return switch (current) {
            case SUBMITTED -> Set.of(PrivacyModels.Status.IN_REVIEW, PrivacyModels.Status.REJECTED).contains(next);
            case IN_REVIEW -> Set.of(PrivacyModels.Status.APPROVED, PrivacyModels.Status.REJECTED).contains(next);
            case APPROVED -> Set.of(PrivacyModels.Status.IN_PROGRESS, PrivacyModels.Status.COMPLETED).contains(next);
            case IN_PROGRESS -> Set.of(PrivacyModels.Status.COMPLETED, PrivacyModels.Status.REJECTED).contains(next);
            default -> false;
        };
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private ZonedDateTime now() { return ZonedDateTime.now(ZoneId.of("UTC")); }
}
