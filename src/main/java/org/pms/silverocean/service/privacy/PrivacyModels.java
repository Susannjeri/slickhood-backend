package org.pms.silverocean.service.privacy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.pms.silverocean.database.pms.entities.PrivacyRequest;

import java.time.ZonedDateTime;
import java.util.List;

public final class PrivacyModels {
    private PrivacyModels() {}
    public enum Type { ACCESS_EXPORT, ERASURE }
    public enum Status { SUBMITTED, IN_REVIEW, APPROVED, IN_PROGRESS, REJECTED, COMPLETED }
    public record Submit(@NotNull Type type, @NotBlank @Size(max=1000) String reason) {}
    public record Review(@NotNull Status status, boolean legalHold, @Size(max=1000) String retentionBasis,
                         @NotBlank @Size(max=2000) String reviewerNotes, @Size(max=500) String resultReference) {}
    public record RequestView(long id, Type type, Status status, String reason, ZonedDateTime submittedAt,
                              ZonedDateTime dueAt, boolean legalHold, String retentionBasis,
                              String reviewerNotes, String resultReference, ZonedDateTime reviewedAt) {
        public RequestView(PrivacyRequest request) {
            this(request.getId(), Type.valueOf(request.getRequestType()), Status.valueOf(request.getStatus()),
                    request.getReason(), request.getCreatedOn(), request.getDueAt(), request.isLegalHold(),
                    request.getRetentionBasis(), request.getReviewerNotes(), request.getResultReference(), request.getReviewedAt());
        }
    }
    public record UserData(long id, String fullName, String email, String phoneNumber, String source,
                           String registrationIP, boolean emailVerified, boolean phoneVerified, String country,
                           String city, String countryCode, String locale, ZonedDateTime lastLogin,
                           boolean verified, String profileType, String identificationNumber, String taxPin,
                           ZonedDateTime registeredAt) {}
    public record KycData(String status, String consentVersion, ZonedDateTime consentAt, boolean phoneVerified,
                          String registryStatus, ZonedDateTime submittedAt, ZonedDateTime reviewedAt,
                          List<KycDocumentData> documents) {}
    public record KycDocumentData(long id, String documentType, String originalFileName, String contentType,
                                  long fileSize, Integer width, Integer height, Double qualityScore,
                                  String qualityStatus, String status, String ocrProvider, Double ocrConfidence,
                                  String rejectionReason, ZonedDateTime uploadedAt, ZonedDateTime reviewedAt) {}
    public record SubscriptionData(long id, String role, String planCode, String status, ZonedDateTime startAt,
                                   ZonedDateTime endAt, boolean autoRenew, String sourcePaymentRef) {}
    public record Export(ZonedDateTime generatedAt, String scopeNotice, UserData user, List<String> roles,
                         KycData kyc, List<SubscriptionData> subscriptions) {}
}
