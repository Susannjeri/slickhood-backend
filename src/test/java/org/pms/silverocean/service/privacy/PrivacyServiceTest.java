package org.pms.silverocean.service.privacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.PrivacyRequest;
import org.pms.silverocean.database.pms.entities.Role;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrivacyServiceTest {
    @Mock PrivacyRequestRepo requests;
    @Mock UserRoleRepo roles;
    @Mock KycCaseRepo kycCases;
    @Mock KycDocumentRepo kycDocuments;
    @Mock UserSubscriptionRepo subscriptions;
    @Mock UserDao users;
    @Mock AuditLogService audit;
    PrivacyService service;

    @BeforeEach
    void setup() {
        service = new PrivacyService(requests, roles, kycCases, kycDocuments, subscriptions, users, audit);
        ReflectionTestUtils.setField(service, "slaDays", 30);
    }

    @Test
    void duplicateOpenRequestIsRejected() {
        when(users.getUserId()).thenReturn(7L);
        when(requests.existsByUserIdAndRequestTypeAndStatusInAndActiveTrue(eq(7L), eq("ERASURE"), any()))
                .thenReturn(true);
        var input = new PrivacyModels.Submit(PrivacyModels.Type.ERASURE, "Close my account");

        PMSCustomException error = assertThrows(PMSCustomException.class, () -> service.submit(input));

        assertEquals(ResponseCode.PRIVACY_REQUEST_DUPLICATE, error.getResponseCode());
        verify(requests, never()).save(any());
    }

    @Test
    void submittedRequestGetsThirtyDayDeadlineAndAuditRecord() {
        when(users.getUserId()).thenReturn(7L);
        when(requests.existsByUserIdAndRequestTypeAndStatusInAndActiveTrue(eq(7L), eq("ACCESS_EXPORT"), any()))
                .thenReturn(false);
        when(requests.save(any())).thenAnswer(invocation -> {
            PrivacyRequest request = invocation.getArgument(0);
            request.setId(11L);
            return request;
        });

        var result = service.submit(new PrivacyModels.Submit(PrivacyModels.Type.ACCESS_EXPORT, "Please provide my data"));

        assertEquals(11L, result.id());
        assertEquals(PrivacyModels.Status.SUBMITTED, result.status());
        assertTrue(result.dueAt().isAfter(java.time.ZonedDateTime.now().plusDays(29)));
        verify(audit).createAuditLog(any(PrivacyRequest.class), eq("PRIVACY_REQUEST_SUBMITTED"));
    }

    @Test
    void erasureCannotCompleteWhileLegalHoldIsActive() {
        PrivacyRequest request = new PrivacyRequest();
        request.setId(12L);
        request.setUserId(7L);
        request.setRequestType("ERASURE");
        request.setStatus("APPROVED");
        request.setActive(true);
        when(requests.findByIdForUpdate(12L)).thenReturn(Optional.of(request));
        var review = new PrivacyModels.Review(PrivacyModels.Status.COMPLETED, true,
                "Tax records must be retained", "Reviewed statutory obligations", null);

        PMSCustomException error = assertThrows(PMSCustomException.class, () -> service.review(12L, review));

        assertEquals(ResponseCode.PRIVACY_REQUEST_INVALID_STATE, error.getResponseCode());
        verify(requests, never()).save(any());
    }

    @Test
    void exportContainsPortableProfileAndRolesWithoutCredentialFields() {
        when(users.getUserId()).thenReturn(7L);
        Users user = new Users();
        user.setId(7L); user.setFullName("Susan Njeri"); user.setEmail("susan@example.test");
        user.setPhoneNumber("254700000000"); user.setPassword("never-export"); user.setRefreshToken("never-export");
        when(users.findById(7L)).thenReturn(Optional.of(user));
        Role role = new Role(); role.setName("Landlord");
        when(roles.findByUserId(7L)).thenReturn(Set.of(role));
        when(kycCases.findByUserId(7L)).thenReturn(Optional.empty());
        when(subscriptions.findAllByCreatedByOrderByCreatedOnDesc(7L)).thenReturn(java.util.List.of());

        PrivacyModels.Export export = service.exportMyData();

        assertEquals("susan@example.test", export.user().email());
        assertEquals(java.util.List.of("Landlord"), export.roles());
        assertNull(export.kyc());
        assertFalse(export.toString().contains("never-export"));
    }
}
