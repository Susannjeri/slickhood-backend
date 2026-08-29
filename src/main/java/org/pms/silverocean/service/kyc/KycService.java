package org.pms.silverocean.service.kyc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.security.EncryptionService;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KycService {
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");
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
        byte[] bytes = validateAndRead(file);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if (documentRepo.existsByUserIdAndSha256AndActiveTrue(user.getId(), sha256)) {
            throw new PMSCustomException(ResponseCode.KYC_DUPLICATE_DOCUMENT);
        }
        ImageQualityResult quality = qualityService.inspect(bytes, file.getContentType());
        if (!quality.accepted()) throw new PMSCustomException(ResponseCode.KYC_DOCUMENT_QUALITY_FAILED, quality.reason());

        String extension = "application/pdf".equals(file.getContentType()) ? ".pdf" :
                ("image/png".equals(file.getContentType()) ? ".png" : ".jpg");
        String fileRef = "kyc/" + user.getId() + "/" + UUID.randomUUID() + extension;
        garageService.uploadBytes(fileRef, bytes, file.getContentType());

        KycDocument document = new KycDocument();
        document.setCaseId(kycCase.getId()); document.setUserId(user.getId());
        document.setDocumentType(documentType.name()); document.setOriginalFileName(safeName(file.getOriginalFilename()));
        document.setContentType(file.getContentType()); document.setFileRef(fileRef); document.setFileSize(file.getSize());
        document.setSha256(sha256); document.setWidth(quality.width()); document.setHeight(quality.height());
        document.setQualityScore(quality.sharpness()); document.setQualityStatus("PASSED"); document.setActive(true);

        OcrResult ocr;
        try {
            ocr = ocrProvider.extract(bytes, file.getContentType(), documentType);
        } catch (RuntimeException providerFailure) {
            // Provider outages must not lose an otherwise valid upload; route it to human review.
            ocr = new OcrResult("PROVIDER_UNAVAILABLE", 0, Map.of());
            document.setRejectionReason("Automatic extraction is temporarily unavailable; document requires review");
        }
        Map<String,String> extractedFields = validateExtractedEvidence(ocr.fields(), user, kycCase);
        document.setOcrProvider(ocr.provider()); document.setOcrConfidence(ocr.confidence());
        if (ocrProvider.enabled()) {
            document.setEncryptedExtractedData(encryptionService.encrypt(objectMapper.writeValueAsString(extractedFields)));
            document.setStatus(extractedFields.isEmpty() || ocr.confidence() < 75 || extractedFields.containsKey("_validationWarnings") ?
                    DocumentStatus.REVIEW_REQUIRED.name() : DocumentStatus.OCR_COMPLETE.name());
        } else {
            document.setStatus(DocumentStatus.REVIEW_REQUIRED.name());
        }
        documentRepo.save(document);
        kycCase.setStatus(KycStatus.IN_PROGRESS.name()); caseRepo.save(kycCase);
        return KycDocumentView.from(document, extractedFields, null);
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
        if (!KycStatus.SUBMITTED.name().equals(kycCase.getStatus()) && !KycStatus.REVIEW_REQUIRED.name().equals(kycCase.getStatus())) {
            throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
        }
        long reviewer = currentUser().getId();
        kycCase.setStatus(request.decision().name()); kycCase.setReviewNotes(request.notes());
        kycCase.setReviewedBy(reviewer); kycCase.setReviewedAt(ZonedDateTime.now()); caseRepo.save(kycCase);
        Users subject = userDao.findById(kycCase.getUserId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_USER_DETAILS));
        if (request.decision() == KycStatus.APPROVED) {
            documentRepo.findByCaseIdAndActiveTrueOrderByCreatedOnDesc(kycCase.getId()).stream()
                    .map(this::decrypt).forEach(fields -> {
                        if (fields.containsKey("documentNumber")) subject.setIdentificationNumber(fields.get("documentNumber"));
                        if (fields.containsKey("taxPin")) subject.setTaxPin(fields.get("taxPin"));
                    });
            subject.setVerified(true);
            subject.setAccountStatus(AccountStatus.ACTIVE.name());
            documentRepo.findByCaseIdAndActiveTrueOrderByCreatedOnDesc(kycCase.getId()).forEach(document -> {
                document.setStatus(DocumentStatus.VERIFIED.name());
                document.setReviewedBy(reviewer);
                document.setReviewedAt(ZonedDateTime.now());
                documentRepo.save(document);
            });
        } else {
            if (request.notes() == null || request.notes().isBlank()) {
                throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
            }
            subject.setVerified(false);
            subject.setAccountStatus(AccountStatus.KYC_REJECTED.name());
            documentRepo.findByCaseIdAndActiveTrueOrderByCreatedOnDesc(kycCase.getId()).forEach(document -> {
                document.setStatus(DocumentStatus.REJECTED.name());
                document.setRejectionReason(request.notes());
                document.setReviewedBy(reviewer);
                document.setReviewedAt(ZonedDateTime.now());
                documentRepo.save(document);
            });
        }
        userDao.save(subject);
        return view(kycCase, subject);
    }

    public List<KycAdminCaseView> reviewQueue() {
        return caseRepo.findByStatusInAndActiveTrueOrderBySubmittedAtAsc(
                        List.of(KycStatus.SUBMITTED.name(), KycStatus.REVIEW_REQUIRED.name(), KycStatus.REJECTED.name()))
                .stream().map(kycCase -> {
                    Users subject = userDao.findById(kycCase.getUserId())
                            .orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_USER_DETAILS));
                    return new KycAdminCaseView(subject.getId(), subject.getFullName(), subject.getEmail(),
                            view(kycCase, subject));
                }).toList();
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

    private byte[] validateAndRead(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty() || file.getSize() > maxFileBytes || !ALLOWED_TYPES.contains(file.getContentType())) {
            throw new PMSCustomException(ResponseCode.UNSUPPORTED_MEDIA_TYPE);
        }
        byte[] bytes = file.getBytes();
        if (bytes.length == 0 || !signatureMatches(bytes, file.getContentType())) {
            throw new PMSCustomException(ResponseCode.UNSUPPORTED_MEDIA_TYPE);
        }
        return bytes;
    }

    private boolean signatureMatches(byte[] b, String type) {
        if ("application/pdf".equals(type)) return b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
        if ("image/png".equals(type)) return b.length > 8 && (b[0] & 255) == 137 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        return b.length > 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255;
    }

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

    private Set<KycRequirement> requirements(Users user) { return requirementResolver.resolve(roles(user.getId())); }

    private Set<String> missingRequirements(KycCase kycCase, Users user) {
        Set<KycDocumentType> uploaded = documentRepo.findByCaseIdAndActiveTrueOrderByCreatedOnDesc(kycCase.getId()).stream()
                .filter(doc -> !DocumentStatus.REJECTED.name().equals(doc.getStatus()))
                .map(doc -> KycDocumentType.valueOf(doc.getDocumentType())).collect(Collectors.toSet());
        return requirements(user).stream().filter(KycRequirement::required)
                .filter(req -> req.acceptedTypes().stream().noneMatch(uploaded::contains))
                .map(KycRequirement::code).collect(Collectors.toSet());
    }

    private Set<String> missingRequirements(KycCase kycCase) {
        return missingRequirements(kycCase, currentUser());
    }

    private KycCaseView view(KycCase kycCase, Users user) {
        List<KycDocumentView> docs = documentRepo.findByCaseIdAndActiveTrueOrderByCreatedOnDesc(kycCase.getId()).stream()
                .map(doc -> KycDocumentView.from(doc, decrypt(doc), null)).toList();
        return new KycCaseView(kycCase.getId(), kycCase.getStatus(), user.getAccountStatus(),
                currentConsentVersion, kycCase.getReviewNotes(), kycCase.isPhoneVerified(), user.getPhoneNumber(),
                timestamp(user.getPhoneVerifiedAt()), kycCase.getRegistryStatus(), ocrProvider.enabled(),
                requirements(user), missingRequirements(kycCase, user), docs);
    }

    private String timestamp(ZonedDateTime value) { return value == null ? null : value.toString(); }

    private Map<String, String> decrypt(KycDocument document) {
        try {
            if (document.getEncryptedExtractedData() == null) return Map.of();
            var decrypted = encryptionService.decrypt(document.getEncryptedExtractedData());
            return decrypted == null ? Map.of() : objectMapper.readValue(decrypted.decryptedValue(), new TypeReference<>() {});
        } catch (Exception ignored) { return Map.of(); }
    }

    private Map<String,String> validateExtractedEvidence(Map<String,String> source, Users user, KycCase kycCase) {
        Map<String,String> fields = new LinkedHashMap<>(source == null ? Map.of() : source);
        List<String> warnings = new ArrayList<>();
        if (fields.containsKey("_validationWarnings")) warnings.add(fields.get("_validationWarnings"));
        String detectedName = normalizeName(fields.get("fullName"));
        String accountName = normalizeName(user.getFullName());
        if (!detectedName.isBlank() && !accountName.isBlank()) {
            Set<String> detectedTokens = new HashSet<>(List.of(detectedName.split(" ")));
            Set<String> accountTokens = new HashSet<>(List.of(accountName.split(" ")));
            detectedTokens.retainAll(accountTokens);
            if (detectedTokens.isEmpty()) warnings.add("Name on the document does not match the account name");
        }
        String number = fields.get("documentNumber");
        if (number != null) {
            boolean conflicts = documentRepo.findByCaseIdAndActiveTrueOrderByCreatedOnDesc(kycCase.getId()).stream()
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

    private String normalizeName(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z ]", " ").replaceAll("\\s+", " ").trim();
    }

    private String safeName(String name) {
        if (name == null) return "document";
        return java.nio.file.Path.of(name).getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
