package org.pms.silverocean.service.kyc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.KycCaseRepo;
import org.pms.silverocean.database.pms.KycDocumentRepo;
import org.pms.silverocean.database.pms.UserRoleRepo;
import org.pms.silverocean.database.pms.entities.KycCase;
import org.pms.silverocean.database.pms.entities.KycDocument;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.users.ProfileType;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.security.EncryptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KycService {
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");
    private static final Set<String> REVIEWER_EDITABLE_FIELDS = Set.of(
            "fullName", "documentNumber", "taxPin", "dateOfBirth", "expiryDate");
    private final KycCaseRepo caseRepo;
    private final KycDocumentRepo documentRepo;
    private final UserRoleRepo userRoleRepo;
    private final UserDao userDao;
    private final KycRequirementResolver requirementResolver;
    private final DocumentQualityService qualityService;
    private final KycOcrProvider ocrProvider;
    private final GarageService garageService;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    @Value("${kyc.consent.version:2026-08}") private String currentConsentVersion;
    @Value("${kyc.max-file-bytes:10485760}") private long maxFileBytes;
    @Value("${kyc.image.reject-quality-failures:true}") private boolean rejectImageQualityFailures = true;
    @Value("${kyc.ocr.min-confidence:75}") private double minOcrConfidence = 75;
    @Value("${kyc.ocr.reject-validation-warnings:true}") private boolean rejectOcrValidationWarnings = true;

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public KycCaseView start(StartKycRequest request) {
        Users user = currentUser();
        if (!currentConsentVersion.equals(request.consentVersion())) {
            throw new PMSCustomException(ResponseCode.KYC_CONSENT_REQUIRED);
        }
        KycCase kycCase = caseRepo.findByUserId(user.getId()).orElseGet(KycCase::new);
        kycCase.setUserId(user.getId());
        kycCase.setStatus(KycStatus.IN_PROGRESS.name());
        kycCase.setConsentVersion(request.consentVersion());
        kycCase.setConsentAt(ZonedDateTime.now());
        kycCase.setPhoneVerified(user.isPhoneVerified());
        kycCase.setRegistryStatus("NOT_CONFIGURED");
        kycCase.setReviewNotes(null);
        kycCase.setSubmittedAt(null);
        kycCase.setReviewedAt(null);
        kycCase.setReviewedBy(null);
        kycCase.setActive(true);
        caseRepo.save(kycCase);
        if (!AccountStatus.ACTIVE.name().equals(user.getAccountStatus())) {
            user.setAccountStatus(AccountStatus.PENDING_KYC.name());
            userDao.save(user);
        }
        return view(kycCase, user);
    }

    public KycCaseView current() {
        Users user = currentUser();
        return caseRepo.findByUserId(user.getId()).map(kycCase -> view(kycCase, user))
                .orElse(new KycCaseView(null, KycStatus.NOT_STARTED.name(), user.getAccountStatus(),
                        currentConsentVersion, null, user.isPhoneVerified(), user.getPhoneNumber(), timestamp(user.getPhoneVerifiedAt()),
                        "NOT_CONFIGURED", ocrProvider.enabled(),
                        requirements(user), requirements(user).stream().filter(KycRequirement::required)
                        .map(KycRequirement::code).collect(Collectors.toSet()), List.of()));
    }

    /**
     * Re-evaluates the approved evidence after a self-service role is added.
     * Existing documents remain available, but a role whose requirements are
     * not yet covered reopens KYC and removes operational access until review.
     */
    @Transactional(transactionManager = "pmsDBTransactionManager")
    public boolean reopenForNewRoleRequirements() {
        Users user = currentUser();
        KycCase kycCase = caseRepo.findByUserId(user.getId()).orElse(null);
        boolean evidenceMissing = kycCase == null || !missingRequirements(kycCase, user).isEmpty();
        if (!evidenceMissing) return false;

        if (kycCase != null) {
            kycCase.setStatus(KycStatus.IN_PROGRESS.name());
            kycCase.setReviewNotes(null);
            kycCase.setSubmittedAt(null);
            kycCase.setReviewedAt(null);
            kycCase.setReviewedBy(null);
            caseRepo.save(kycCase);
        }
        user.setVerified(false);
        user.setAccountStatus(AccountStatus.PENDING_KYC.name());
        userDao.save(user);
        return true;
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public KycDocumentView upload(KycDocumentType documentType, MultipartFile file) throws Exception {
        Users user = currentUser();
        KycCase kycCase = caseRepo.findByUserId(user.getId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.KYC_CONSENT_REQUIRED));
        if (KycStatus.SUBMITTED.name().equals(kycCase.getStatus())
                || KycStatus.REVIEW_REQUIRED.name().equals(kycCase.getStatus())
                || KycStatus.APPROVED.name().equals(kycCase.getStatus())) {
            throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
        }
        if (requirements(user).stream().noneMatch(requirement -> requirement.acceptedTypes().contains(documentType))) {
            throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
        }
        ValidatedUpload upload = validateAndRead(file);
        byte[] bytes = upload.bytes();
        String contentType = upload.contentType();
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        List<KycDocument> active = activeDocuments(kycCase.getId());
        List<KycDocument> superseded = active.stream()
                .filter(existing -> sameEvidenceSlot(user, existing, documentType))
                .toList();
        Optional<KycDocument> duplicate = active.stream()
                .filter(existing -> sha256.equals(existing.getSha256()))
                .findFirst();
        if (duplicate.isPresent() && !superseded.contains(duplicate.get())) {
            // Reusing one physical document for two different evidence requirements is unsafe.
            throw new PMSCustomException(ResponseCode.KYC_DUPLICATE_DOCUMENT);
        }
        if (duplicate.isPresent() && !DocumentStatus.REJECTED.name().equals(duplicate.get().getStatus())) {
            // Upload controls must be idempotent: a double click or retry must not create a new version.
            return documentView(duplicate.get());
        }
        ImageQualityResult quality = qualityService.inspect(bytes, contentType);
        if (!quality.accepted() && (rejectImageQualityFailures || uninspectable(quality))) {
            throw new PMSCustomException(ResponseCode.KYC_DOCUMENT_QUALITY_FAILED, quality.reason());
        }

        String extension = "application/pdf".equals(contentType) ? ".pdf" :
                ("image/png".equals(contentType) ? ".png" : ".jpg");
        String fileRef = "kyc/" + user.getId() + "/" + UUID.randomUUID() + extension;
        KycDocument document = new KycDocument();
        document.setCaseId(kycCase.getId()); document.setUserId(user.getId());
        document.setDocumentType(documentType.name()); document.setOriginalFileName(safeName(file.getOriginalFilename()));
        document.setContentType(contentType); document.setFileRef(fileRef); document.setFileSize((long) bytes.length);
        document.setSha256(sha256); document.setWidth(quality.width()); document.setHeight(quality.height());
        document.setQualityScore(quality.sharpness());
        document.setQualityStatus(quality.accepted() ? "PASSED" : "REVIEW_REQUIRED");
        document.setActive(true);

        if (!ocrProvider.enabled()) {
            throw new PMSCustomException(ResponseCode.KYC_OCR_PROVIDER_UNAVAILABLE);
        }
        OcrResult ocr;
        try {
            ocr = ocrProvider.extract(bytes, contentType, documentType);
        } catch (RuntimeException providerFailure) {
            log.warn("KYC OCR provider failed for document type {} ({})", documentType,
                    providerFailure.getClass().getSimpleName());
            throw new PMSCustomException(ResponseCode.KYC_OCR_PROVIDER_UNAVAILABLE);
        }
        document.setVersionNo(superseded.stream().mapToInt(existing -> Math.max(1, existing.getVersionNo()))
                .max().orElse(0) + 1);
        document.setMaintenanceReason(superseded.isEmpty() ? "INITIAL_UPLOAD" : "CUSTOMER_REPLACEMENT");
        Set<Long> supersededIds = superseded.stream().map(KycDocument::getId).collect(Collectors.toSet());
        Map<String,String> extractedFields = validateExtractedEvidence(
                ocr.fields(), user, kycCase, supersededIds, documentType);
        if (!quality.accepted()) {
            addValidationWarning(extractedFields, "Image quality: " + quality.reason());
        }
        document.setOcrProvider(ocr.provider()); document.setOcrConfidence(ocr.confidence());
        document.setEncryptedExtractedData(encryptionService.encrypt(objectMapper.writeValueAsString(extractedFields)));
        superseded.stream().findFirst().map(KycDocument::getId).ifPresent(document::setSupersedesDocumentId);
        if (ocrAccepted(documentType, ocr, extractedFields)) {
            document.setStatus(DocumentStatus.OCR_COMPLETE.name());
        } else {
            document.setStatus(DocumentStatus.REJECTED.name());
            document.setRejectionReason(ocrRejectionReason(extractedFields));
        }
        garageService.uploadBytes(fileRef, bytes, contentType);
        documentRepo.save(document);
        superseded.forEach(previous -> {
            previous.setActive(false);
            documentRepo.save(previous);
        });
        kycCase.setStatus(KycStatus.IN_PROGRESS.name()); caseRepo.save(kycCase);
        return KycDocumentView.from(document, extractedFields, Map.of(), null);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public KycCaseView submit() {
        KycCase kycCase = ownCase();
        Set<String> missing = missingRequirements(kycCase);
        if (!missing.isEmpty()) throw new PMSCustomException(ResponseCode.KYC_MISSING_DOCUMENTS, missing);
        Users user = currentUser();
        kycCase.setPhoneVerified(user.isPhoneVerified());
        if (!kycCase.isPhoneVerified()) throw new PMSCustomException(ResponseCode.KYC_PHONE_VERIFICATION_REQUIRED);
        kycCase.setStatus(KycStatus.SUBMITTED.name()); kycCase.setSubmittedAt(ZonedDateTime.now());
        kycCase.setReviewNotes(null);
        kycCase.setReviewedAt(null);
        kycCase.setReviewedBy(null);
        caseRepo.save(kycCase);
        user.setAccountStatus(AccountStatus.KYC_UNDER_REVIEW.name());
        userDao.save(user);
        return view(kycCase, user);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public KycCaseView review(long caseId, KycReviewRequest request) {
        if (request.decision() != KycStatus.APPROVED && request.decision() != KycStatus.REJECTED) {
            throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
        }
        KycCase kycCase = caseRepo.findById(caseId).orElseThrow(() -> new PMSCustomException(ResponseCode.KYC_CASE_NOT_FOUND));
        Users subject = userDao.findById(kycCase.getUserId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_USER_DETAILS));
        boolean pendingReview = KycStatus.SUBMITTED.name().equals(kycCase.getStatus())
                || KycStatus.REVIEW_REQUIRED.name().equals(kycCase.getStatus());
        if (!pendingReview) {
            throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
        }
        long reviewer = currentUser().getId();
        List<KycDocument> currentDocuments = currentDocuments(kycCase, subject);
        Map<Long, KycDocumentReviewRequest> documentDecisions = validateDocumentDecisions(
                request, currentDocuments);
        if (request.decision() == KycStatus.APPROVED
                && (!missingRequirements(kycCase, subject).isEmpty() || !kycCase.isPhoneVerified())) {
            throw new PMSCustomException(ResponseCode.KYC_MISSING_DOCUMENTS);
        }
        String reviewNotes = request.notes();
        if (request.decision() == KycStatus.REJECTED && (reviewNotes == null || reviewNotes.isBlank())) {
            reviewNotes = "Replace the rejected documents and submit the corrected evidence for review.";
        }
        final String recordedReviewNotes = reviewNotes;
        kycCase.setStatus(request.decision().name()); kycCase.setReviewNotes(recordedReviewNotes);
        kycCase.setReviewedBy(reviewer); kycCase.setReviewedAt(ZonedDateTime.now()); caseRepo.save(kycCase);
        if (request.decision() == KycStatus.APPROVED) {
            String identificationNumber = null;
            String taxPin = null;
            for (KycDocument document : currentDocuments) {
                KycDocumentReviewRequest decision = documentDecisions.get(document.getId());
                Map<String, String> originalFields = decrypt(document);
                Map<String, String> corrections = validateReviewerCorrections(document, decision, originalFields);
                persistReviewerCorrections(document, corrections, decision.correctionReason(), reviewer);
                Map<String, String> fields = effectiveFields(originalFields, corrections);
                if (identificationNumber == null) identificationNumber = cleanVerifiedValue(fields.get("documentNumber"));
                if (taxPin == null) taxPin = cleanVerifiedValue(fields.get("taxPin"));
            }
            boolean taxRequired = taxRequired(subject);
            if (identificationNumber == null || (taxRequired && taxPin == null)) {
                throw new PMSCustomException(ResponseCode.KYC_OCR_EVIDENCE_REQUIRED);
            }
            if (!userDao.isValidIDAndTaxPin(subject.getId(), subject.getCountry(), identificationNumber, taxPin)) {
                throw new PMSCustomException(ResponseCode.INVALID_USER_DETAILS);
            }
            subject.setIdentificationNumber(identificationNumber.toUpperCase(Locale.ROOT));
            if (taxPin != null) subject.setTaxPin(taxPin.toUpperCase(Locale.ROOT));
            subject.setVerified(true);
            subject.setAccountStatus(AccountStatus.ACTIVE.name());
            currentDocuments.forEach(document -> {
                if (!DocumentStatus.REJECTED.name().equals(document.getStatus())) {
                    document.setStatus(DocumentStatus.VERIFIED.name());
                }
                document.setReviewedBy(reviewer);
                document.setReviewedAt(ZonedDateTime.now());
                documentRepo.save(document);
            });
        } else {
            subject.setVerified(false);
            subject.setAccountStatus(AccountStatus.KYC_REJECTED.name());
            currentDocuments.forEach(document -> {
                KycDocumentReviewRequest decision = documentDecisions.get(document.getId());
                boolean approved = decision != null && decision.approved();
                document.setStatus(approved ? DocumentStatus.VERIFIED.name() : DocumentStatus.REJECTED.name());
                document.setRejectionReason(approved ? null : rejectionReason(decision, recordedReviewNotes));
                document.setReviewedBy(reviewer);
                document.setReviewedAt(ZonedDateTime.now());
                documentRepo.save(document);
            });
        }
        userDao.save(subject);
        return view(kycCase, subject);
    }

    private Map<Long, KycDocumentReviewRequest> validateDocumentDecisions(
            KycReviewRequest request, List<KycDocument> currentDocuments) {
        List<KycDocumentReviewRequest> submitted = request.documentsOrEmpty();
        if (submitted.isEmpty()) {
            if (!currentDocuments.isEmpty()) {
                // A KYC decision must be an explicit, complete review of the current evidence.
                throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
            }
            return currentDocuments.stream().collect(Collectors.toMap(KycDocument::getId,
                    document -> new KycDocumentReviewRequest(document.getId(),
                            request.decision() == KycStatus.APPROVED, request.notes())));
        }
        Map<Long, KycDocumentReviewRequest> decisions = new LinkedHashMap<>();
        for (KycDocumentReviewRequest decision : submitted) {
            if (decisions.putIfAbsent(decision.documentId(), decision) != null) {
                throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
            }
            if (!decision.approved() && (decision.reason() == null || decision.reason().isBlank())) {
                throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
            }
            if (!decision.approved() && !decision.verifiedFieldsOrEmpty().isEmpty()) {
                throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
            }
        }
        Set<Long> currentIds = currentDocuments.stream().map(KycDocument::getId).collect(Collectors.toSet());
        if (!decisions.keySet().equals(currentIds)) {
            throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
        }
        boolean hasRejectedDocument = decisions.values().stream().anyMatch(decision -> !decision.approved());
        if ((request.decision() == KycStatus.APPROVED && hasRejectedDocument)
                || (request.decision() == KycStatus.REJECTED && !hasRejectedDocument)) {
            throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
        }
        if (request.decision() == KycStatus.APPROVED && currentDocuments.stream()
                .anyMatch(document -> DocumentStatus.REJECTED.name().equals(document.getStatus()))) {
            // Reviewer corrections may fix OCR transcription, never a file rejected by upload controls.
            throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
        }
        return decisions;
    }

    private Map<String, String> validateReviewerCorrections(KycDocument document,
                                                             KycDocumentReviewRequest decision,
                                                             Map<String, String> originalFields) {
        Map<String, String> submitted = decision == null ? Map.of() : decision.verifiedFieldsOrEmpty();
        if (submitted.size() > REVIEWER_EDITABLE_FIELDS.size()) {
            throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
        }
        Map<String, String> corrections = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : submitted.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (!REVIEWER_EDITABLE_FIELDS.contains(key) || !fieldAllowedForDocument(documentType(document), key)
                    || value.isBlank() || value.length() > 255) {
                throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
            }
            String original = originalFields.get(key);
            if (!value.equals(original == null ? "" : original.trim())) corrections.put(key, value);
        }
        if (!corrections.isEmpty()
                && (decision.correctionReason() == null || decision.correctionReason().isBlank())) {
            throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
        }
        return corrections;
    }

    private boolean fieldAllowedForDocument(KycDocumentType type, String field) {
        if (type == KycDocumentType.KRA_PIN_CERTIFICATE) {
            return Set.of("taxPin", "fullName").contains(field);
        }
        if (type == KycDocumentType.PASSPORT || type == KycDocumentType.NATIONAL_ID_FRONT
                || type == KycDocumentType.NATIONAL_ID_BACK || type == KycDocumentType.ALIEN_ID_FRONT
                || type == KycDocumentType.ALIEN_ID_BACK) {
            return Set.of("fullName", "documentNumber", "dateOfBirth", "expiryDate").contains(field);
        }
        return "fullName".equals(field);
    }

    private void persistReviewerCorrections(KycDocument document, Map<String, String> corrections,
                                             String reason, long reviewer) {
        try {
            document.setEncryptedReviewerVerifiedData(corrections.isEmpty() ? null
                    : encryptionService.encrypt(objectMapper.writeValueAsString(corrections)));
        } catch (Exception ignored) {
            throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
        }
        document.setReviewerCorrectionReason(corrections.isEmpty() ? null : reason.trim());
        document.setReviewedBy(reviewer);
        document.setReviewedAt(ZonedDateTime.now());
        documentRepo.save(document);
    }

    private Map<String, String> effectiveFields(Map<String, String> original, Map<String, String> corrections) {
        Map<String, String> effective = new LinkedHashMap<>(original);
        effective.putAll(corrections);
        return effective;
    }

    private String rejectionReason(KycDocumentReviewRequest decision, String fallback) {
        if (decision != null && decision.reason() != null && !decision.reason().isBlank()) {
            return decision.reason().trim();
        }
        return fallback;
    }

    public List<KycAdminCaseView> reviewQueue() {
        List<KycAdminCaseView> queue = new ArrayList<>();
        for (KycCase kycCase : caseRepo.findByStatusInAndActiveTrueOrderBySubmittedAtAsc(
                List.of(KycStatus.SUBMITTED.name(), KycStatus.REVIEW_REQUIRED.name(), KycStatus.REJECTED.name()))) {
            Users subject = userDao.findById(kycCase.getUserId())
                    .orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_USER_DETAILS));
            queue.add(new KycAdminCaseView(subject.getId(), subject.getFullName(), subject.getEmail(),
                    view(kycCase, subject)));
        }
        return queue;
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public KycDocumentView maintainDocument(long documentId, KycDocumentMaintenanceRequest request) {
        KycDocument document = documentRepo.findById(documentId).filter(KycDocument::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));
        if (request.issuedAt() != null && request.expiresAt() != null
                && !request.expiresAt().isAfter(request.issuedAt())) {
            throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
        }
        if (request.reverificationDueAt() != null && request.expiresAt() != null
                && request.reverificationDueAt().isAfter(request.expiresAt())) {
            throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
        }
        document.setIssuedAt(request.issuedAt());
        document.setExpiresAt(request.expiresAt());
        document.setReverificationDueAt(request.reverificationDueAt() != null
                ? request.reverificationDueAt() : request.expiresAt());
        document.setMaintenanceReason(request.reason() == null ? null : request.reason().trim());
        documentRepo.save(document);
        return documentView(document);
    }

    @Scheduled(cron = "${kyc.reverification.cron:0 15 1 * * *}")
    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void flagDocumentsDueForReverification() {
        ZonedDateTime now = ZonedDateTime.now();
        for (KycDocument document : documentRepo
                .findByActiveTrueAndReverificationDueAtLessThanEqualAndStatusNot(now, DocumentStatus.REVIEW_REQUIRED.name())) {
            document.setStatus(DocumentStatus.REVIEW_REQUIRED.name());
            if (document.getMaintenanceReason() == null || document.getMaintenanceReason().isBlank()) {
                document.setMaintenanceReason("Document expired or reached its re-verification date.");
            }
            documentRepo.save(document);
            caseRepo.findById(document.getCaseId()).ifPresent(kycCase -> {
                // An expired document needs customer action, not a passive
                // "under review" screen that prevents replacement uploads.
                kycCase.setStatus(KycStatus.EXPIRED.name());
                kycCase.setReviewNotes(document.getMaintenanceReason());
                caseRepo.save(kycCase);
            });
            userDao.findById(document.getUserId()).ifPresent(user -> {
                user.setVerified(false);
                user.setAccountStatus(AccountStatus.PENDING_KYC.name());
                userDao.save(user);
            });
        }
    }

    private String cleanVerifiedValue(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("\\s+", "").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public KycCaseView reprocessOwnDocuments() throws Exception {
        if (!ocrProvider.enabled()) throw new PMSCustomException(ResponseCode.KYC_OCR_PROVIDER_UNAVAILABLE);
        Users user = currentUser();
        KycCase kycCase = ownCase();
        if (KycStatus.APPROVED.name().equals(kycCase.getStatus())) {
            throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
        }
        return reprocessDocuments(kycCase, user);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public KycCaseView reprocessCase(long caseId) throws Exception {
        if (!ocrProvider.enabled()) throw new PMSCustomException(ResponseCode.KYC_OCR_PROVIDER_UNAVAILABLE);
        KycCase kycCase = caseRepo.findById(caseId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.KYC_CASE_NOT_FOUND));
        if (KycStatus.APPROVED.name().equals(kycCase.getStatus())) {
            throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
        }
        Users user = userDao.findById(kycCase.getUserId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_USER_DETAILS));
        return reprocessDocuments(kycCase, user);
    }

    private KycCaseView reprocessDocuments(KycCase kycCase, Users user) throws Exception {
        for (KycDocument document : currentDocuments(kycCase, user)) {
            KycDocumentType type = KycDocumentType.valueOf(document.getDocumentType());
            var stored = garageService.download(document.getFileRef());
            String contentType = stored.contentType() == null ? document.getContentType() : stored.contentType();
            OcrResult ocr;
            try {
                ocr = ocrProvider.extract(stored.bytes(), contentType, type);
            } catch (RuntimeException providerFailure) {
                log.warn("KYC OCR reprocessing failed for document type {} ({})", type,
                        providerFailure.getClass().getSimpleName());
                throw new PMSCustomException(ResponseCode.KYC_OCR_PROVIDER_UNAVAILABLE);
            }
            Map<String, String> fields = validateExtractedEvidence(ocr.fields(), user, kycCase, Set.of(), type);
            document.setOcrProvider(ocr.provider());
            document.setOcrConfidence(ocr.confidence());
            document.setEncryptedExtractedData(encryptionService.encrypt(objectMapper.writeValueAsString(fields)));
            if (ocrAccepted(type, ocr, fields)) {
                document.setStatus(DocumentStatus.OCR_COMPLETE.name());
                document.setRejectionReason(null);
            } else {
                document.setStatus(DocumentStatus.REJECTED.name());
                document.setRejectionReason(ocrRejectionReason(fields));
            }
            documentRepo.save(document);
        }

        String identificationNumber = null;
        String taxPin = null;
        for (KycDocument document : currentDocuments(kycCase, user)) {
            if (DocumentStatus.REJECTED.name().equals(document.getStatus())) continue;
            Map<String, String> fields = decrypt(document);
            if (identificationNumber == null) identificationNumber = cleanVerifiedValue(fields.get("documentNumber"));
            if (taxPin == null) taxPin = cleanVerifiedValue(fields.get("taxPin"));
        }
        boolean complete = missingRequirements(kycCase, user).isEmpty()
                && identificationNumber != null && (!taxRequired(user) || taxPin != null);
        if (complete && userDao.isValidIDAndTaxPin(user.getId(), user.getCountry(), identificationNumber, taxPin)) {
            user.setIdentificationNumber(identificationNumber.toUpperCase(Locale.ROOT));
            if (taxPin != null) user.setTaxPin(taxPin.toUpperCase(Locale.ROOT));
            user.setVerified(false);
            user.setAccountStatus(AccountStatus.KYC_UNDER_REVIEW.name());
            kycCase.setStatus(KycStatus.SUBMITTED.name());
            if (kycCase.getSubmittedAt() == null) kycCase.setSubmittedAt(ZonedDateTime.now());
        } else {
            user.setVerified(false);
            user.setAccountStatus(AccountStatus.PENDING_KYC.name());
            kycCase.setStatus(KycStatus.IN_PROGRESS.name());
            kycCase.setSubmittedAt(null);
        }
        kycCase.setReviewNotes(complete ? null
                : "Upload-stage checks found evidence that must be corrected before submission.");
        kycCase.setReviewedAt(null);
        kycCase.setReviewedBy(null);
        userDao.save(user);
        caseRepo.save(kycCase);
        return view(kycCase, user);
    }

    private boolean ocrAccepted(KycDocumentType type, OcrResult ocr, Map<String, String> fields) {
        if (type == KycDocumentType.SELFIE) return true;
        boolean confidenceAccepted = !requiresMachineReadableEvidence(type)
                || ocr.confidence() >= minOcrConfidence;
        boolean warningsAccepted = !rejectOcrValidationWarnings || !fields.containsKey("_validationWarnings");
        return confidenceAccepted && warningsAccepted;
    }

    private boolean requiresMachineReadableEvidence(KycDocumentType type) {
        return type == KycDocumentType.KRA_PIN_CERTIFICATE
                || type == KycDocumentType.PASSPORT
                || type == KycDocumentType.NATIONAL_ID_FRONT
                || type == KycDocumentType.NATIONAL_ID_BACK
                || type == KycDocumentType.ALIEN_ID_FRONT
                || type == KycDocumentType.ALIEN_ID_BACK;
    }

    private String ocrRejectionReason(Map<String, String> fields) {
        String warning = fields.get("_validationWarnings");
        return warning == null || warning.isBlank()
                ? "The required identity data could not be read confidently. Upload a clearer original document."
                : warning;
    }

    /**
     * Returns a KYC document only to its owner or a user with KYC review authority.
     * The bytes are delivered by the authenticated API, never through a permanent public URL.
     */
    public KycDocumentContent documentContent(long documentId) {
        KycDocument document = documentRepo.findById(documentId)
                .filter(KycDocument::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));
        long requesterId = userDao.getUserId();
        boolean reviewer = userDao.hasPermission(org.pms.silverocean.service.auth.roles.enums.Permission.LIST_USERS);
        if (document.getUserId() != requesterId && !reviewer) {
            // Do not disclose whether another customer's document exists.
            throw new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND);
        }
        var stored = garageService.download(document.getFileRef());
        String contentType = stored.contentType() == null ? document.getContentType() : stored.contentType();
        return new KycDocumentContent(stored.bytes(), contentType, document.getOriginalFileName(),
                stored.contentLength() == null ? stored.bytes().length : stored.contentLength());
    }

    private ValidatedUpload validateAndRead(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new PMSCustomException(ResponseCode.UNSUPPORTED_MEDIA_TYPE);
        }
        if (file.getSize() > maxFileBytes) {
            throw new PMSCustomException(ResponseCode.MAX_UPLOAD_SIZE_EXCEEDED);
        }
        byte[] bytes = file.getBytes();
        String detectedType = detectContentType(bytes);
        if (bytes.length == 0 || detectedType == null) {
            throw new PMSCustomException(ResponseCode.UNSUPPORTED_MEDIA_TYPE);
        }
        String declaredType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (ALLOWED_TYPES.contains(declaredType) && !declaredType.equals(detectedType)) {
            throw new PMSCustomException(ResponseCode.UNSUPPORTED_MEDIA_TYPE);
        }
        return new ValidatedUpload(bytes, detectedType);
    }

    private String detectContentType(byte[] b) {
        if (b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return "application/pdf";
        if (b.length > 8 && (b[0] & 255) == 137 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 13 && b[5] == 10 && b[6] == 26 && b[7] == 10) return "image/png";
        if (b.length > 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255) return "image/jpeg";
        return null;
    }

    private record ValidatedUpload(byte[] bytes, String contentType) { }

    private KycCase ownCase() {
        return caseRepo.findByUserId(currentUser().getId()).orElseThrow(() -> new PMSCustomException(ResponseCode.KYC_CASE_NOT_FOUND));
    }

    private Users currentUser() {
        Users user = userDao.getUserObject();
        if (user == null) throw new PMSCustomException(ResponseCode.INVALID_USER_DETAILS);
        return user;
    }

    private Set<PMSRole> roles(long userId) {
        return userRoleRepo.findByUserId(userId).stream().map(role -> PMSRole.roleFromSavedName(role.getName())).collect(Collectors.toSet());
    }

    private Set<KycRequirement> requirements(Users user) {
        return requirementResolver.resolve(roles(user.getId()), ProfileType.valueOf(user.getProfileType()));
    }

    private Set<String> missingRequirements(KycCase kycCase, Users user) {
        Set<KycDocumentType> uploaded = currentDocuments(kycCase, user).stream()
                .filter(doc -> !DocumentStatus.REJECTED.name().equals(doc.getStatus()))
                .filter(doc -> !DocumentStatus.REVIEW_REQUIRED.name().equals(doc.getStatus()))
                .filter(doc -> !DocumentStatus.SUPERSEDED.name().equals(doc.getStatus()))
                .map(doc -> KycDocumentType.valueOf(doc.getDocumentType())).collect(Collectors.toSet());
        Set<KycRequirement> effective = effectiveRequirements(user, uploaded);
        Set<String> missing = effective.stream().filter(KycRequirement::required)
                .filter(req -> req.acceptedTypes().stream().noneMatch(uploaded::contains))
                .map(KycRequirement::code).collect(Collectors.toSet());
        boolean backRequired = effective.stream().anyMatch(requirement -> requirement.required()
                && "IDENTITY_BACK".equals(requirement.code()));
        if (backRequired && uploaded.contains(KycDocumentType.NATIONAL_ID_FRONT)
                && !uploaded.contains(KycDocumentType.NATIONAL_ID_BACK)) missing.add("IDENTITY_BACK");
        if (backRequired && uploaded.contains(KycDocumentType.ALIEN_ID_FRONT)
                && !uploaded.contains(KycDocumentType.ALIEN_ID_BACK)) missing.add("IDENTITY_BACK");
        return missing;
    }

    private Set<String> missingRequirements(KycCase kycCase) {
        return missingRequirements(kycCase, currentUser());
    }

    private Set<KycRequirement> effectiveRequirements(Users user, Set<KycDocumentType> uploaded) {
        Set<KycRequirement> resolved = requirements(user);
        if (!uploaded.contains(KycDocumentType.PASSPORT)) return resolved;
        return resolved.stream().filter(requirement -> !"IDENTITY_BACK".equals(requirement.code()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean taxRequired(Users user) {
        return requirements(user).stream().anyMatch(requirement -> requirement.required()
                && "TAX".equals(requirement.code()));
    }

    private KycCaseView view(KycCase kycCase, Users user) {
        List<KycDocumentView> docs = currentDocuments(kycCase, user).stream()
                .map(this::documentView).toList();
        Set<KycDocumentType> uploadedTypes = docs.stream()
                .map(doc -> KycDocumentType.valueOf(doc.documentType())).collect(Collectors.toSet());
        return new KycCaseView(kycCase.getId(), kycCase.getStatus(), user.getAccountStatus(),
                currentConsentVersion, kycCase.getReviewNotes(), user.isPhoneVerified(), user.getPhoneNumber(),
                timestamp(user.getPhoneVerifiedAt()), kycCase.getRegistryStatus(), ocrProvider.enabled(),
                effectiveRequirements(user, uploadedTypes), missingRequirements(kycCase, user), docs);
    }

    private String timestamp(ZonedDateTime value) { return value == null ? null : value.toString(); }

    private Map<String, String> decrypt(KycDocument document) {
        try {
            if (document.getEncryptedExtractedData() == null) return Map.of();
            var decrypted = encryptionService.decrypt(document.getEncryptedExtractedData());
            return decrypted == null ? Map.of() : objectMapper.readValue(decrypted.decryptedValue(), new TypeReference<>() {});
        } catch (Exception ignored) { return Map.of(); }
    }

    private Map<String, String> decryptReviewerVerified(KycDocument document) {
        try {
            if (document.getEncryptedReviewerVerifiedData() == null) return Map.of();
            var decrypted = encryptionService.decrypt(document.getEncryptedReviewerVerifiedData());
            return decrypted == null ? Map.of() : objectMapper.readValue(
                    decrypted.decryptedValue(), new TypeReference<>() {});
        } catch (Exception ignored) { return Map.of(); }
    }

    private KycDocumentView documentView(KycDocument document) {
        return KycDocumentView.from(document, decrypt(document), decryptReviewerVerified(document), null);
    }

    private Map<String,String> validateExtractedEvidence(Map<String,String> source, Users user, KycCase kycCase,
                                                         Set<Long> supersededIds, KycDocumentType documentType) {
        Map<String,String> fields = new LinkedHashMap<>(source == null ? Map.of() : source);
        List<String> warnings = new ArrayList<>();
        if (fields.containsKey("_validationWarnings")) warnings.add(fields.get("_validationWarnings"));
        String detectedName = normalizeName(fields.get("fullName"));
        String expectedName = normalizeName(expectedDocumentOwner(user, documentType));
        if (!detectedName.isBlank() && !expectedName.isBlank()) {
            Set<String> detectedTokens = new HashSet<>(List.of(detectedName.split(" ")));
            Set<String> expectedTokens = new HashSet<>(List.of(expectedName.split(" ")));
            detectedTokens.retainAll(expectedTokens);
            if (detectedTokens.isEmpty()) warnings.add("Name on the document does not match the account name");
        }
        String number = fields.get("documentNumber");
        if (number != null) {
            boolean conflicts = activeDocuments(kycCase.getId()).stream()
                    .filter(existing -> !supersededIds.contains(existing.getId()))
                    .map(this::decrypt).map(existing -> existing.get("documentNumber"))
                    .filter(Objects::nonNull).anyMatch(existing -> !existing.equalsIgnoreCase(number));
            if (conflicts) warnings.add("Document number conflicts with another uploaded identity document");
        }
        if (!warnings.isEmpty()) {
            fields.put("_validationStatus", "REVIEW_REQUIRED");
            fields.put("_validationWarnings", String.join("; ", new LinkedHashSet<>(warnings)));
        }
        return fields;
    }

    private String expectedDocumentOwner(Users user, KycDocumentType documentType) {
        boolean company = ProfileType.COMPANY.name().equalsIgnoreCase(user.getProfileType());
        if (company && organizationDocument(documentType)) return user.getOrganizationName();
        return user.getFullName();
    }

    private boolean organizationDocument(KycDocumentType documentType) {
        return documentType == KycDocumentType.BUSINESS_REGISTRATION_CERTIFICATE
                || documentType == KycDocumentType.CR12
                || documentType == KycDocumentType.KRA_PIN_CERTIFICATE;
    }

    private void addValidationWarning(Map<String, String> fields, String warning) {
        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        String existing = fields.get("_validationWarnings");
        if (existing != null && !existing.isBlank()) warnings.addAll(List.of(existing.split("; ")));
        warnings.add(warning);
        fields.put("_validationStatus", "REVIEW_REQUIRED");
        fields.put("_validationWarnings", String.join("; ", warnings));
    }

    private boolean uninspectable(ImageQualityResult quality) {
        return quality.width() == 0 && quality.height() == 0;
    }

    /**
     * Returns exactly one current upload for each logical KYC requirement. Older
     * attempts stay in the database as immutable audit evidence but never take
     * part in a new submission or review decision.
     */
    private List<KycDocument> currentDocuments(KycCase kycCase, Users user) {
        List<KycDocument> active = activeDocuments(kycCase.getId());
        Set<KycRequirement> resolved = requirements(user);
        LinkedHashMap<Long, KycDocument> selected = new LinkedHashMap<>();
        for (KycRequirement requirement : resolved) {
            active.stream()
                    .filter(document -> requirement.acceptedTypes().contains(documentType(document)))
                    .findFirst()
                    .ifPresent(document -> selected.putIfAbsent(document.getId(), document));
        }
        // Defensive support for legacy/test cases whose role requirements are unavailable.
        if (resolved.isEmpty()) {
            LinkedHashSet<KycDocumentType> seen = new LinkedHashSet<>();
            active.stream().filter(document -> seen.add(documentType(document)))
                    .forEach(document -> selected.putIfAbsent(document.getId(), document));
        }
        return new ArrayList<>(selected.values());
    }

    private List<KycDocument> activeDocuments(long caseId) {
        return documentRepo.findByCaseIdAndActiveTrueOrderByCreatedOnDesc(caseId);
    }

    private boolean sameEvidenceSlot(Users user, KycDocument existing, KycDocumentType replacement) {
        KycDocumentType existingType = documentType(existing);
        return requirements(user).stream().anyMatch(requirement ->
                requirement.acceptedTypes().contains(existingType)
                        && requirement.acceptedTypes().contains(replacement));
    }

    private KycDocumentType documentType(KycDocument document) {
        return KycDocumentType.valueOf(document.getDocumentType());
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z ]", " ").replaceAll("\\s+", " ").trim();
    }

    private String safeName(String name) {
        if (name == null) return "document";
        return java.nio.file.Path.of(name).getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
