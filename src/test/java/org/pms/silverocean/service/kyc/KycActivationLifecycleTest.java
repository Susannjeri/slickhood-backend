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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KycActivationLifecycleTest {
    private KycCaseRepo cases;
    private KycDocumentRepo documents;
    private UserRoleRepo roles;
    private UserDao users;
    private KycRequirementResolver requirements;
    private GarageService garage;
    private KycService service;

    @BeforeEach void setUp() {
        cases = mock(KycCaseRepo.class); documents = mock(KycDocumentRepo.class);
        roles = mock(UserRoleRepo.class); users = mock(UserDao.class); requirements = mock(KycRequirementResolver.class); garage = mock(GarageService.class);
        when(documents.findByCaseIdAndActiveTrueOrderByCreatedOnDesc(anyLong())).thenReturn(List.of());
        when(roles.findByUserId(any(Long.class))).thenReturn(Set.of());
        when(requirements.resolve(any())).thenReturn(Set.of());
        service = new KycService(cases, documents, roles, users, requirements,
                mock(DocumentQualityService.class), mock(KycOcrProvider.class), garage,
                mock(EncryptionService.class), new ObjectMapper());
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

        service.review(40, new KycReviewRequest(KycStatus.APPROVED, "Documents matched"));

        assertThat(subject.isVerified()).isTrue();
        assertThat(subject.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE.name());
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

    @Test void ownerCanReadProtectedDocumentThroughAuthenticatedService() {
        KycDocument document = document(81,12);
        when(users.getUserId()).thenReturn(12L); when(documents.findById(81L)).thenReturn(Optional.of(document));
        when(garage.download("kyc/12/id.jpg")).thenReturn(new GarageService.StoredObject(new byte[]{1,2},"image/jpeg",2L));

        KycDocumentContent content = service.documentContent(81);

        assertThat(content.bytes()).containsExactly(1,2);
        assertThat(content.contentType()).isEqualTo("image/jpeg");
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
