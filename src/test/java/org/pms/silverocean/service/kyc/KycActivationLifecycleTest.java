package org.pms.silverocean.service.kyc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.KycCaseRepo;
import org.pms.silverocean.database.pms.KycDocumentRepo;
import org.pms.silverocean.database.pms.UserRoleRepo;
import org.pms.silverocean.database.pms.entities.KycCase;
import org.pms.silverocean.database.pms.entities.KycDocument;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.security.EncryptionService;
import org.pms.silverocean.service.security.DecryptDTO;
import org.pms.silverocean.service.users.ProfileType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KycActivationLifecycleTest {
    private KycCaseRepo cases;
    private KycDocumentRepo documents;
    private UserRoleRepo roles;
    private UserDao users;
    private KycRequirementResolver requirements;
    private GarageService garage;
    private EncryptionService encryption;
    private DocumentQualityService quality;
    private KycOcrProvider ocr;
    private KycService service;

    @BeforeEach void setUp() {
        cases = mock(KycCaseRepo.class); documents = mock(KycDocumentRepo.class);
        roles = mock(UserRoleRepo.class); users = mock(UserDao.class); requirements = mock(KycRequirementResolver.class); garage = mock(GarageService.class);
        encryption = mock(EncryptionService.class);
        quality = mock(DocumentQualityService.class);
        ocr = mock(KycOcrProvider.class);
        when(documents.findByCaseIdAndActiveTrueOrderByCreatedOnDesc(anyLong())).thenReturn(List.of());
        when(documents.save(any(KycDocument.class))).thenAnswer(invocation -> {
            KycDocument saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(100L);
            return saved;
        });
        when(roles.findByUserId(any(Long.class))).thenReturn(Set.of());
        when(requirements.resolve(any())).thenReturn(Set.of());
        when(requirements.resolve(any(), any())).thenReturn(Set.of());
        when(users.isValidIDAndTaxPin(anyLong(), any(), any(), any())).thenReturn(true);
        service = new KycService(cases, documents, roles, users, requirements,
                quality, ocr, garage,
                encryption, new ObjectMapper());
        ReflectionTestUtils.setField(service, "maxFileBytes", 10_485_760L);
        ReflectionTestUtils.setField(service, "rejectImageQualityFailures", true);
        ReflectionTestUtils.setField(service, "minOcrConfidence", 75D);
        ReflectionTestUtils.setField(service, "rejectOcrValidationWarnings", true);
    }

    @Test void submissionMovesCustomerIntoReviewGate() {
        Users customer = customer(12); customer.setPhoneVerified(true);
        KycCase kycCase = submittedCase(40, 12, KycStatus.IN_PROGRESS);
        when(users.getUserObject()).thenReturn(customer); when(cases.findByUserId(12)).thenReturn(Optional.of(kycCase));

        KycCaseView view = service.submit();

        assertThat(view.status()).isEqualTo("SUBMITTED");
        assertThat(customer.getAccountStatus()).isEqualTo(AccountStatus.KYC_UNDER_REVIEW.name());
        verify(users).save(customer);
    }

    @Test void approvalActivatesCustomer() {
        Users reviewer = customer(1); Users subject = customer(12);
        KycCase kycCase = submittedCase(40, 12, KycStatus.SUBMITTED);
        when(users.getUserObject()).thenReturn(reviewer); when(users.findById(12)).thenReturn(Optional.of(subject));
        when(cases.findById(40L)).thenReturn(Optional.of(kycCase));
        KycDocument identity = document(81, 12); identity.setCaseId(40); identity.setDocumentType(KycDocumentType.NATIONAL_ID_FRONT.name()); identity.setEncryptedExtractedData(new byte[]{1});
        KycDocument tax = document(82, 12); tax.setCaseId(40); tax.setDocumentType(KycDocumentType.KRA_PIN_CERTIFICATE.name()); tax.setEncryptedExtractedData(new byte[]{2});
        when(documents.findByCaseIdAndActiveTrueOrderByCreatedOnDesc(40L)).thenReturn(List.of(identity, tax));
        when(encryption.decrypt(new byte[]{1})).thenReturn(new DecryptDTO(false, "{\"documentNumber\":\"12345678\"}"));
        when(encryption.decrypt(new byte[]{2})).thenReturn(new DecryptDTO(false, "{\"taxPin\":\"A123456789B\"}"));

        service.review(40, new KycReviewRequest(KycStatus.APPROVED, "Documents matched"));

        assertThat(subject.isVerified()).isTrue();
        assertThat(subject.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE.name());
        assertThat(subject.getIdentificationNumber()).isEqualTo("12345678");
        assertThat(subject.getTaxPin()).isEqualTo("A123456789B");
        verify(users).save(subject);
    }

    @Test void rejectionKeepsCustomerOutsideOperationalWorkspace() {
        Users reviewer = customer(1); Users subject = customer(12);
        KycCase kycCase = submittedCase(40, 12, KycStatus.SUBMITTED);
        when(users.getUserObject()).thenReturn(reviewer); when(users.findById(12)).thenReturn(Optional.of(subject));
        when(cases.findById(40L)).thenReturn(Optional.of(kycCase));

        service.review(40, new KycReviewRequest(KycStatus.REJECTED, "Identity image edges are cropped"));

        assertThat(subject.isVerified()).isFalse();
        assertThat(subject.getAccountStatus()).isEqualTo(AccountStatus.KYC_REJECTED.name());
    }

    @Test void reviewerKeepsGoodEvidenceAndReturnsOnlyTheInaccurateDocument() {
        Users reviewer = customer(1); Users subject = customer(12);
        KycCase kycCase = submittedCase(40, 12, KycStatus.SUBMITTED);
        KycDocument identity = document(81, 12); identity.setCaseId(40);
        identity.setDocumentType(KycDocumentType.NATIONAL_ID_FRONT.name());
        identity.setStatus(DocumentStatus.OCR_COMPLETE.name());
        KycDocument tax = document(82, 12); tax.setCaseId(40);
        tax.setDocumentType(KycDocumentType.KRA_PIN_CERTIFICATE.name());
        tax.setStatus(DocumentStatus.OCR_COMPLETE.name());
        when(users.getUserObject()).thenReturn(reviewer); when(users.findById(12)).thenReturn(Optional.of(subject));
        when(cases.findById(40L)).thenReturn(Optional.of(kycCase));
        when(documents.findByCaseIdAndActiveTrueOrderByCreatedOnDesc(40L)).thenReturn(List.of(identity, tax));

        service.review(40, new KycReviewRequest(KycStatus.REJECTED, "One correction is required", List.of(
                new KycDocumentReviewRequest(81, false, "The ID number is obscured by glare"),
                new KycDocumentReviewRequest(82, true, null))));

        assertThat(identity.getStatus()).isEqualTo(DocumentStatus.REJECTED.name());
        assertThat(identity.getRejectionReason()).isEqualTo("The ID number is obscured by glare");
        assertThat(tax.getStatus()).isEqualTo(DocumentStatus.VERIFIED.name());
        assertThat(tax.getRejectionReason()).isNull();
        assertThat(subject.getAccountStatus()).isEqualTo(AccountStatus.KYC_REJECTED.name());
    }

    @Test void reviewerMustDecideEveryCurrentDocument() {
        Users reviewer = customer(1); Users subject = customer(12);
        KycCase kycCase = submittedCase(40, 12, KycStatus.SUBMITTED);
        KycDocument identity = document(81, 12); identity.setCaseId(40);
        identity.setDocumentType(KycDocumentType.NATIONAL_ID_FRONT.name());
        KycDocument tax = document(82, 12); tax.setCaseId(40);
        tax.setDocumentType(KycDocumentType.KRA_PIN_CERTIFICATE.name());
        when(users.getUserObject()).thenReturn(reviewer); when(users.findById(12)).thenReturn(Optional.of(subject));
        when(cases.findById(40L)).thenReturn(Optional.of(kycCase));
        when(documents.findByCaseIdAndActiveTrueOrderByCreatedOnDesc(40L)).thenReturn(List.of(identity, tax));

        assertThatThrownBy(() -> service.review(40,
                new KycReviewRequest(KycStatus.REJECTED, "Incomplete decision", List.of(
                        new KycDocumentReviewRequest(81, false, "Unreadable")))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test void approvalCannotCreateAnActiveButIncompleteIdentity() {
        Users reviewer = customer(1); Users subject = customer(12);
        KycCase kycCase = submittedCase(40, 12, KycStatus.SUBMITTED);
        when(users.getUserObject()).thenReturn(reviewer); when(users.findById(12)).thenReturn(Optional.of(subject));
        when(cases.findById(40L)).thenReturn(Optional.of(kycCase));

        assertThatThrownBy(() -> service.review(40,
                new KycReviewRequest(KycStatus.APPROVED, "Documents matched")))
                .isInstanceOf(RuntimeException.class);

        assertThat(subject.isVerified()).isFalse();
        assertThat(subject.getIdentificationNumber()).isNull();
        assertThat(subject.getTaxPin()).isNull();
    }

    @Test void approvedLegacyProfileCannotBeManuallyRepaired() {
        Users reviewer = customer(1); Users subject = customer(12);
        subject.setFullName("Legacy Customer"); subject.setPhoneNumber("+254700000012");
        KycCase kycCase = submittedCase(40, 12, KycStatus.APPROVED);
        when(users.getUserObject()).thenReturn(reviewer); when(users.findById(12)).thenReturn(Optional.of(subject));
        when(cases.findById(40L)).thenReturn(Optional.of(kycCase));

        assertThatThrownBy(() -> service.review(40,
                new KycReviewRequest(KycStatus.APPROVED, "Legacy profile repaired")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test void ownerCanReadProtectedDocumentThroughAuthenticatedService() {
        KycDocument document = document(81,12);
        when(users.getUserId()).thenReturn(12L); when(documents.findById(81L)).thenReturn(Optional.of(document));
        when(garage.download("kyc/12/id.jpg")).thenReturn(new GarageService.StoredObject(new byte[]{1,2},"image/jpeg",2L));

        KycDocumentContent content = service.documentContent(81);

        assertThat(content.bytes()).containsExactly(1,2);
        assertThat(content.contentType()).isEqualTo("image/jpeg");
    }

    @Test void unreadableIdentityDocumentIsRejectedByOcr() throws Exception {
        Users subject = customer(12);
        KycCase kycCase = submittedCase(40, 12, KycStatus.IN_PROGRESS);
        byte[] image = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
        when(users.getUserObject()).thenReturn(subject);
        when(cases.findByUserId(12)).thenReturn(Optional.of(kycCase));
        when(requirements.resolve(any(), any())).thenReturn(Set.of(
                new KycRequirement("IDENTITY", "Identity document", true,
                        Set.of(KycDocumentType.NATIONAL_ID_FRONT, KycDocumentType.PASSPORT))));
        when(quality.inspect(image, "image/jpeg")).thenReturn(new ImageQualityResult(true, 1200, 800, 90, null));
        when(ocr.enabled()).thenReturn(true);
        when(ocr.extract(image, "image/jpeg", KycDocumentType.NATIONAL_ID_FRONT)).thenReturn(
                new OcrResult("TEST_OCR", 42, java.util.Map.of("_validationWarnings", "Could not confidently extract: document number")));
        when(encryption.encrypt(any())).thenReturn(new byte[]{9});

        KycDocumentView result = service.upload(KycDocumentType.NATIONAL_ID_FRONT,
                new MockMultipartFile("file", "id.jpg", "image/jpeg", image));

        assertThat(result.status()).isEqualTo(DocumentStatus.REJECTED.name());
        assertThat(result.rejectionReason()).contains("document number");
        verify(garage).uploadBytes(any(), any(), any());
    }

    @Test void testingModeAcceptsModerateOcrEvidenceForControlledReview() throws Exception {
        Users subject = customer(12);
        subject.setFullName("Collectable Class");
        KycCase kycCase = submittedCase(40, 12, KycStatus.IN_PROGRESS);
        byte[] image = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
        when(users.getUserObject()).thenReturn(subject);
        when(cases.findByUserId(12)).thenReturn(Optional.of(kycCase));
        when(requirements.resolve(any(), any())).thenReturn(Set.of(
                new KycRequirement("IDENTITY", "Identity document", true,
                        Set.of(KycDocumentType.NATIONAL_ID_FRONT, KycDocumentType.PASSPORT))));
        when(quality.inspect(image, "image/jpeg")).thenReturn(new ImageQualityResult(true, 800, 500, 20, null));
        when(ocr.enabled()).thenReturn(true);
        when(ocr.extract(image, "image/jpeg", KycDocumentType.NATIONAL_ID_FRONT)).thenReturn(
                new OcrResult("TEST_OCR", 58, java.util.Map.of(
                        "documentNumber", "12345678",
                        "_validationWarnings", "Name could not be read confidently")));
        when(encryption.encrypt(any())).thenReturn(new byte[]{9});
        ReflectionTestUtils.setField(service, "minOcrConfidence", 55D);
        ReflectionTestUtils.setField(service, "rejectOcrValidationWarnings", false);

        KycDocumentView result = service.upload(KycDocumentType.NATIONAL_ID_FRONT,
                new MockMultipartFile("file", "test-id.jpg", "image/jpeg", image));

        assertThat(result.status()).isEqualTo(DocumentStatus.OCR_COMPLETE.name());
        assertThat(result.extractedFields()).containsEntry("_validationStatus", "REVIEW_REQUIRED");
        assertThat(result.extractedFields()).containsKey("_validationWarnings");
    }

    @Test void advisoryImageQualityModeKeepsReadableEvidenceForControlledReview() throws Exception {
        Users subject = customer(12);
        subject.setFullName("Collectable Class");
        KycCase kycCase = submittedCase(40, 12, KycStatus.IN_PROGRESS);
        byte[] image = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
        when(users.getUserObject()).thenReturn(subject);
        when(cases.findByUserId(12)).thenReturn(Optional.of(kycCase));
        when(requirements.resolve(any(), any())).thenReturn(Set.of(
                new KycRequirement("IDENTITY", "Identity document", true,
                        Set.of(KycDocumentType.NATIONAL_ID_FRONT))));
        when(quality.inspect(image, "image/jpeg")).thenReturn(
                new ImageQualityResult(false, 800, 500, 18, "Image resolution is too low"));
        when(ocr.enabled()).thenReturn(true);
        when(ocr.extract(image, "image/jpeg", KycDocumentType.NATIONAL_ID_FRONT)).thenReturn(
                new OcrResult("TEST_OCR", 92, Map.of("documentNumber", "12345678", "fullName", "Collectable Class")));
        when(encryption.encrypt(any())).thenReturn(new byte[]{9});
        ReflectionTestUtils.setField(service, "rejectImageQualityFailures", false);
        ReflectionTestUtils.setField(service, "rejectOcrValidationWarnings", false);

        KycDocumentView result = service.upload(KycDocumentType.NATIONAL_ID_FRONT,
                new MockMultipartFile("file", "test-id.jpg", "image/jpeg", image));

        assertThat(result.status()).isEqualTo(DocumentStatus.OCR_COMPLETE.name());
        assertThat(result.qualityStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(result.extractedFields().get("_validationWarnings")).contains("Image quality");
    }

    @Test void advisoryModeStillBlocksCorruptImagesBeforeOcr() {
        Users subject = customer(12);
        KycCase kycCase = submittedCase(40, 12, KycStatus.IN_PROGRESS);
        byte[] image = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
        when(users.getUserObject()).thenReturn(subject);
        when(cases.findByUserId(12)).thenReturn(Optional.of(kycCase));
        when(requirements.resolve(any(), any())).thenReturn(Set.of(
                new KycRequirement("IDENTITY", "Identity document", true,
                        Set.of(KycDocumentType.NATIONAL_ID_FRONT))));
        when(quality.inspect(image, "image/jpeg")).thenReturn(
                new ImageQualityResult(false, 0, 0, 0, "Unreadable image"));
        ReflectionTestUtils.setField(service, "rejectImageQualityFailures", false);

        assertThatThrownBy(() -> service.upload(KycDocumentType.NATIONAL_ID_FRONT,
                new MockMultipartFile("file", "broken.jpg", "image/jpeg", image)))
                .isInstanceOf(RuntimeException.class);
        verify(ocr, never()).extract(any(), any(), any());
        verify(garage, never()).uploadBytes(any(), any(), any());
    }

    @Test void companyRegistrationCertificateMatchesOrganizationNameNotRepresentativeName() throws Exception {
        Users subject = customer(12);
        subject.setFullName("Susan Wanjohi");
        subject.setProfileType(ProfileType.COMPANY.name());
        subject.setOrganizationName("Mitero Hope SHG");
        KycCase kycCase = submittedCase(40, 12, KycStatus.IN_PROGRESS);
        byte[] image = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
        when(users.getUserObject()).thenReturn(subject);
        when(cases.findByUserId(12)).thenReturn(Optional.of(kycCase));
        when(requirements.resolve(any(), any())).thenReturn(Set.of(
                new KycRequirement("ORGANIZATION", "Organization document", true,
                        Set.of(KycDocumentType.BUSINESS_REGISTRATION_CERTIFICATE))));
        when(quality.inspect(image, "image/jpeg")).thenReturn(new ImageQualityResult(true, 1200, 800, 90, null));
        when(ocr.enabled()).thenReturn(true);
        when(ocr.extract(image, "image/jpeg", KycDocumentType.BUSINESS_REGISTRATION_CERTIFICATE)).thenReturn(
                new OcrResult("TEST_OCR", 96, Map.of("fullName", "Mitero Hope SHG", "documentNumber", "PVT-123")));
        when(encryption.encrypt(any())).thenReturn(new byte[]{9});

        KycDocumentView result = service.upload(KycDocumentType.BUSINESS_REGISTRATION_CERTIFICATE,
                new MockMultipartFile("file", "registration.jpg", "image/jpeg", image));

        assertThat(result.status()).isEqualTo(DocumentStatus.OCR_COMPLETE.name());
        assertThat(result.extractedFields()).doesNotContainKey("_validationWarnings");
    }

    @Test void disabledOcrBlocksUploadWithoutStoringDocument() {
        Users subject = customer(12);
        KycCase kycCase = submittedCase(40, 12, KycStatus.IN_PROGRESS);
        byte[] image = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
        when(users.getUserObject()).thenReturn(subject);
        when(cases.findByUserId(12)).thenReturn(Optional.of(kycCase));
        when(quality.inspect(image, "image/jpeg")).thenReturn(new ImageQualityResult(true, 1200, 800, 90, null));
        when(ocr.enabled()).thenReturn(false);

        assertThatThrownBy(() -> service.upload(KycDocumentType.NATIONAL_ID_FRONT,
                new MockMultipartFile("file", "id.jpg", "image/jpeg", image)))
                .isInstanceOf(RuntimeException.class);
        verify(garage, never()).uploadBytes(any(), any(), any());
    }

    @Test void resubmissionViewShowsOnlyLatestEvidenceForEachRequirement() {
        Users subject = customer(12);
        KycCase kycCase = submittedCase(40, 12, KycStatus.SUBMITTED);
        KycRequirement identity = new KycRequirement("IDENTITY", "Identity document", true,
                Set.of(KycDocumentType.NATIONAL_ID_FRONT, KycDocumentType.PASSPORT));
        KycRequirement tax = new KycRequirement("TAX", "KRA PIN", true,
                Set.of(KycDocumentType.KRA_PIN_CERTIFICATE));
        KycDocument latestIdentity = document(103, 12);
        latestIdentity.setCaseId(40); latestIdentity.setDocumentType(KycDocumentType.NATIONAL_ID_FRONT.name());
        latestIdentity.setStatus(DocumentStatus.OCR_COMPLETE.name());
        KycDocument rejectedPassport = document(102, 12);
        rejectedPassport.setCaseId(40); rejectedPassport.setDocumentType(KycDocumentType.PASSPORT.name());
        rejectedPassport.setStatus(DocumentStatus.REJECTED.name());
        KycDocument latestTax = document(101, 12);
        latestTax.setCaseId(40); latestTax.setDocumentType(KycDocumentType.KRA_PIN_CERTIFICATE.name());
        latestTax.setStatus(DocumentStatus.OCR_COMPLETE.name());
        KycDocument historicalTax = document(100, 12);
        historicalTax.setCaseId(40); historicalTax.setDocumentType(KycDocumentType.KRA_PIN_CERTIFICATE.name());
        historicalTax.setStatus(DocumentStatus.REJECTED.name());
        when(users.getUserObject()).thenReturn(subject);
        when(cases.findByUserId(12)).thenReturn(Optional.of(kycCase));
        when(requirements.resolve(any(), any())).thenReturn(Set.of(identity, tax));
        when(documents.findByCaseIdAndActiveTrueOrderByCreatedOnDesc(40L))
                .thenReturn(List.of(latestIdentity, rejectedPassport, latestTax, historicalTax));

        KycCaseView view = service.current();

        assertThat(view.documents()).extracting(KycDocumentView::id)
                .containsExactlyInAnyOrder(103L, 101L);
        assertThat(view.documents()).noneMatch(document -> document.id() == 102L || document.id() == 100L);
    }

    @Test void cleanReplacementSupersedesRejectedAlternativesWithoutFalseIdentityConflict() throws Exception {
        Users subject = customer(12);
        subject.setFullName("Collectable Class");
        KycCase kycCase = submittedCase(40, 12, KycStatus.IN_PROGRESS);
        KycRequirement identity = new KycRequirement("IDENTITY", "Identity document", true,
                Set.of(KycDocumentType.NATIONAL_ID_FRONT, KycDocumentType.PASSPORT));
        KycDocument rejectedPassport = document(92, 12);
        rejectedPassport.setCaseId(40); rejectedPassport.setDocumentType(KycDocumentType.PASSPORT.name());
        rejectedPassport.setStatus(DocumentStatus.REJECTED.name()); rejectedPassport.setEncryptedExtractedData(new byte[]{2});
        KycDocument rejectedId = document(91, 12);
        rejectedId.setCaseId(40); rejectedId.setDocumentType(KycDocumentType.NATIONAL_ID_FRONT.name());
        rejectedId.setStatus(DocumentStatus.REJECTED.name()); rejectedId.setEncryptedExtractedData(new byte[]{1});
        byte[] image = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
        when(users.getUserObject()).thenReturn(subject);
        when(cases.findByUserId(12)).thenReturn(Optional.of(kycCase));
        when(requirements.resolve(any(), any())).thenReturn(Set.of(identity));
        when(documents.findByCaseIdAndActiveTrueOrderByCreatedOnDesc(40L))
                .thenReturn(List.of(rejectedPassport, rejectedId));
        when(encryption.decrypt(new byte[]{1})).thenReturn(new DecryptDTO(false, "{\"documentNumber\":\"OLD-ID\"}"));
        when(encryption.decrypt(new byte[]{2})).thenReturn(new DecryptDTO(false, "{\"documentNumber\":\"OLD-PASSPORT\"}"));
        when(quality.inspect(image, "image/jpeg")).thenReturn(new ImageQualityResult(true, 1200, 800, 90, null));
        when(ocr.enabled()).thenReturn(true);
        when(ocr.extract(image, "image/jpeg", KycDocumentType.NATIONAL_ID_FRONT)).thenReturn(
                new OcrResult("TEST_OCR", 96, java.util.Map.of(
                        "documentNumber", "12345678", "fullName", "Collectable Class")));
        when(encryption.encrypt(any())).thenReturn(new byte[]{9});

        KycDocumentView replacement = service.upload(KycDocumentType.NATIONAL_ID_FRONT,
                new MockMultipartFile("file", "clean-id.jpg", "image/jpeg", image));

        assertThat(replacement.status()).isEqualTo(DocumentStatus.OCR_COMPLETE.name());
        assertThat(replacement.extractedFields()).doesNotContainKey("_validationWarnings");
        assertThat(rejectedPassport.isActive()).isFalse();
        assertThat(rejectedId.isActive()).isFalse();
    }

    @Test void unrelatedCustomerCannotReadAnotherCustomersDocument() {
        when(users.getUserId()).thenReturn(99L); when(users.hasPermission(any())).thenReturn(false);
        when(documents.findById(81L)).thenReturn(Optional.of(document(81,12)));

        assertThatThrownBy(() -> service.documentContent(81)).isInstanceOf(RuntimeException.class);
    }

    private Users customer(long id) {
        Users user = new Users(); user.setId(id); user.setEmail("user" + id + "@example.com");
        user.setAccountStatus(AccountStatus.PENDING_KYC.name()); return user;
    }

    private KycCase submittedCase(long id, long userId, KycStatus status) {
        KycCase value = new KycCase(); value.setId(id); value.setUserId(userId); value.setStatus(status.name());
        value.setPhoneVerified(true); value.setActive(true); value.setRegistryStatus("NOT_CONFIGURED"); return value;
    }

    private KycDocument document(long id,long userId) {
        KycDocument value = new KycDocument(); value.setId(id); value.setUserId(userId); value.setActive(true);
        value.setFileRef("kyc/12/id.jpg"); value.setOriginalFileName("id.jpg"); value.setContentType("image/jpeg"); return value;
    }
}
