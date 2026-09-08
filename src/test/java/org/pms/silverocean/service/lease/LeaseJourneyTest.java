package org.pms.silverocean.service.lease;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.LeaseDocumentRepo;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.RoleService;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.property.UnitDao;
import org.pms.silverocean.service.invites.InviteDao;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaseJourneyTest {
    @Mock LeaseDao leases;
    @Mock UserDao users;
    @Mock UnitDao units;
    @Mock RoleService roles;
    @Mock LeaseDocumentRepo documents;
    @Mock LeaseAccessService access;
    @Mock InviteDao invites;
    @Mock LeaseTemplateDao templates;
    @Mock org.pms.silverocean.service.leasedocument.TenantLeaseAgreementService tenantAgreements;
    @Mock org.pms.silverocean.service.mustache.RenderService renderer;
    @InjectMocks LeaseService service;

    @Test void leaseListIncludesTheCurrentAgreementStateWithoutExposingAnotherLease() {
        Lease lease = lease();
        LeaseDocumentRepo.AgreementSummary agreement = mock(LeaseDocumentRepo.AgreementSummary.class);
        when(agreement.getDocumentId()).thenReturn(9L);
        when(agreement.getLeaseId()).thenReturn(lease.getId());
        when(agreement.getStatus()).thenReturn(org.pms.silverocean.service.leasedocument.LeaseDocumentStatus.ISSUED);
        var dto = new org.pms.silverocean.service.lease.wrappers.LeaseDTO(lease, "Tenant", null);
        when(users.getUserId()).thenReturn(4L);
        when(users.getActiveRole()).thenReturn(PMSRole.TENANT);
        when(leases.getScopedLeaseList(eq(4L), eq(PMSRole.TENANT.name()), isNull(), any()))
                .thenReturn(new PageImpl<>(java.util.List.of(dto)));
        when(documents.findCurrentAgreementsForLeases(java.util.Set.of(1L)))
                .thenReturn(java.util.List.of(agreement));

        var result = service.getLeaseList(PageRequest.of(0, 25));

        assertEquals(9L, result.getContent().getFirst().agreementDocumentId());
        assertEquals("ISSUED", result.getContent().getFirst().agreementStatus());
        verify(documents).findCurrentAgreementsForLeases(java.util.Set.of(1L));
    }

    @Test void bothGovernedSignaturesActivateTenancyAndOccupyUnit() {
        Lease lease = lease(); Unit unit = unit(false); UnitTenant tenancy = tenancy();
        when(leases.getLeaseForUpdate(1L)).thenReturn(Optional.of(lease));
        when(leases.getUnitTenantByTenantId(2L)).thenReturn(Optional.of(tenancy));
        when(units.findByAndLockById(3L)).thenReturn(Optional.of(unit));
        when(units.findPropertyOwnerId(3L)).thenReturn(Optional.of(5L));
        service.activateFromGovernedAgreement(1,5,4,LocalDateTime.now(),LocalDateTime.now().minusMinutes(1));
        assertTrue(unit.isOccupied()); assertTrue(tenancy.isLeaseAccepted()); assertTrue(lease.isSigned());
        assertEquals("ACTIVE",lease.getLifecycleStatus()); assertTrue(lease.isPaymentDue());
        verify(leases).deleteUnsignedLeaseAndUnitTenantsByUnitIdAndLeaseId(3L,1L);
    }
    @Test void oneSignatureCannotActivateOrOccupyUnit() {
        when(leases.getLeaseForUpdate(1L)).thenReturn(Optional.of(lease()));
        assertThrows(PMSCustomException.class, () -> service.activateFromGovernedAgreement(1,5,4,null,LocalDateTime.now()));
        verifyNoInteractions(units);
    }
    @Test void competingSignedLeaseCannotTakeOccupiedUnit() {
        when(leases.getLeaseForUpdate(1L)).thenReturn(Optional.of(lease()));
        when(leases.getUnitTenantByTenantId(2L)).thenReturn(Optional.of(tenancy()));
        when(units.findByAndLockById(3L)).thenReturn(Optional.of(unit(true)));
        when(units.findPropertyOwnerId(3L)).thenReturn(Optional.of(5L));
        assertThrows(PMSCustomException.class, () -> service.activateFromGovernedAgreement(1,5,4,LocalDateTime.now(),LocalDateTime.now()));
        verify(units,never()).update(any());
    }
    @Test void oneTenantCanInitializeLeasesForMoreThanOneDifferentUnit() {
        Users tenant = new Users(); tenant.setId(4L); tenant.setActive(true); tenant.setFullName("Tenant One");
        tenant.setEmail("tenant@example.test"); tenant.setPhoneNumber("+254700000001");
        tenant.setIdentificationNumber("ID-4"); tenant.setTaxPin("A000000004Z");
        when(users.getUserObject()).thenReturn(tenant);
        when(leases.getLeaseFromTokenAndUser(anyString(),eq(4L))).thenReturn(Optional.empty());
        Unit first = rentableUnit(3L,"A-1"), second = rentableUnit(4L,"B-1");
        when(units.findByToken("first")).thenReturn(Optional.of(first));
        when(units.findByToken("second")).thenReturn(Optional.of(second));
        when(units.findByAndLockById(3L)).thenReturn(Optional.of(first));
        when(units.findByAndLockById(4L)).thenReturn(Optional.of(second));
        when(leases.hasActiveLeaseForUnit(3L)).thenReturn(false);
        when(leases.hasActiveLeaseForUnit(4L)).thenReturn(false);
        Invite firstInvite = tenantInvite(31L,"first"), secondInvite = tenantInvite(32L,"second");
        when(invites.getActiveTokenForUpdate("first")).thenReturn(Optional.of(firstInvite));
        when(invites.getActiveTokenForUpdate("second")).thenReturn(Optional.of(secondInvite));
        LeaseTemplate template = new LeaseTemplate(); template.setId(7L); template.setLeaseMode("RENT"); template.setPetsPolicy(new byte[0]);
        when(templates.getTemplateById(7L)).thenReturn(Optional.of(template));
        when(units.getAllUnitCharges(anyLong())).thenReturn(java.util.List.of());
        java.util.concurrent.atomic.AtomicLong tenancyIds = new java.util.concurrent.atomic.AtomicLong(40L);
        doAnswer(call -> { ((UnitTenant)call.getArgument(0)).setId(tenancyIds.incrementAndGet()); return null; }).when(leases).saveUnitTenant(any());
        java.util.concurrent.atomic.AtomicLong ids = new java.util.concurrent.atomic.AtomicLong(100L);
        doAnswer(call -> { ((Lease)call.getArgument(0)).setId(ids.incrementAndGet()); return null; }).when(leases).createLease(any());
        doAnswer(call -> { Lease lease=call.getArgument(0); LeaseDocument document=new LeaseDocument(); document.setId(lease.getId()+1000); document.setStatus(org.pms.silverocean.service.leasedocument.LeaseDocumentStatus.ISSUED); return document; })
                .when(tenantAgreements).createIssuedAgreement(any(),any(),any(),eq(tenant));

        var firstResult = service.initializeLeaseDraft("first");
        var secondResult = service.initializeLeaseDraft("second");

        assertNotEquals(firstResult.leaseId(),secondResult.leaseId());
        verify(leases).hasActiveLeaseForUnit(3L);
        verify(leases).hasActiveLeaseForUnit(4L);
        verify(leases,times(2)).createLease(any());
    }

    @Test void aUnitWithAnActiveLeaseCannotStartAnotherTenantJourney() {
        Users tenant = new Users(); tenant.setId(4L); tenant.setFullName("Tenant"); tenant.setEmail("tenant@example.test");
        tenant.setPhoneNumber("+254700000001"); tenant.setIdentificationNumber("ID-4"); tenant.setTaxPin("A000000004Z");
        when(users.getUserObject()).thenReturn(tenant);
        when(leases.getLeaseFromTokenAndUser("token",4L)).thenReturn(Optional.empty());
        Unit unit = rentableUnit(3L,"A-1");
        when(units.findByToken("token")).thenReturn(Optional.of(unit));
        when(units.findByAndLockById(3L)).thenReturn(Optional.of(unit));
        when(leases.hasActiveLeaseForUnit(3L)).thenReturn(true);
        assertThrows(PMSCustomException.class,()->service.initializeLeaseDraft("token"));
        verifyNoInteractions(invites,tenantAgreements);
    }
    @Test void legacyLandlordCannotSignBeforeTenant() {
        Lease lease = lease(); lease.setGovernedDocumentRequired(false);
        when(users.getUserId()).thenReturn(5L); when(users.getActiveRole()).thenReturn(PMSRole.LANDLORD);
        when(leases.getLeaseByIdAndStaffOwnerOrTenantId(1L,5L)).thenReturn(Optional.of(lease));
        assertThrows(PMSCustomException.class, () -> service.signLease(1));
        verifyNoInteractions(units);
    }
    @Test void expiredTermCannotActivateOrStartBilling() {
        Lease lease = lease(); lease.setMoveOutDate(LocalDate.now().minusDays(1));
        Unit unit = unit(false);
        when(leases.getLeaseForUpdate(1L)).thenReturn(Optional.of(lease));
        when(leases.getUnitTenantByTenantId(2L)).thenReturn(Optional.of(tenancy()));
        when(units.findByAndLockById(3L)).thenReturn(Optional.of(unit));
        when(units.findPropertyOwnerId(3L)).thenReturn(Optional.of(5L));
        assertThrows(PMSCustomException.class, () -> service.activateFromGovernedAgreement(1,5,4,LocalDateTime.now(),LocalDateTime.now()));
        assertFalse(unit.isOccupied()); assertFalse(lease.isPaymentDue());
        verify(units,never()).update(any());
    }

    @Test void originalPartyCanViewSignedSnapshotAfterTenancyEnds() throws Exception {
        LeaseDocument document = new LeaseDocument(); document.setId(9L);
        document.setStatus(org.pms.silverocean.service.leasedocument.LeaseDocumentStatus.SIGNED);
        document.setRenderedHtml("<html><body><p>Original agreed rent</p></body></html>");
        document.setIssuerUserId(5L); document.setRecipientUserId(4L);
        document.setIssuerSignedAt(LocalDateTime.of(2026,9,1,12,0));
        when(users.getUserId()).thenReturn(4L);
        when(documents.findAccessibleAgreement(eq(1L),eq(4L),any())).thenReturn(java.util.List.of(document));
        var output = new java.io.ByteArrayOutputStream();
        service.viewLease(1,output);
        verify(renderer).toPdf(argThat(html -> html.contains("Original agreed rent") && html.contains("Electronic execution record") && html.contains("2026-09-01T12:00")),eq(output));
        verifyNoInteractions(leases, units);
    }

    @Test void governedLeaseDoesNotFallBackToAnUnsignedTemplate() {
        when(users.getUserId()).thenReturn(4L);
        when(leases.getLeaseByIdAndStaffOwnerOrTenantId(1L,4L)).thenReturn(Optional.of(lease()));
        assertThrows(PMSCustomException.class, () -> service.viewLease(1,new java.io.ByteArrayOutputStream()));
        verifyNoInteractions(renderer);
    }

    @Test void unrelatedUserCannotReadAnotherPartysLeasePdf() {
        when(users.getUserId()).thenReturn(999L);
        assertThrows(PMSCustomException.class, () -> service.viewLease(1,new java.io.ByteArrayOutputStream()));
        verifyNoInteractions(renderer);
    }

    @Test void terminationStopsFutureBillingAndReleasesUnitWithoutDeletingAgreements() {
        Lease lease = lease(); lease.setSigned(true); lease.setPaymentDue(true);
        lease.setLifecycleStatus("NOTICE_GIVEN");
        Unit unit = unit(true); UnitTenant tenancy = tenancy(); tenancy.setLeaseAccepted(true);
        when(leases.getTerminationCandidates(any(),any())).thenReturn(java.util.List.of(lease),java.util.List.of());
        when(leases.getUnitTenantByTenantId(2L)).thenReturn(Optional.of(tenancy));
        when(leases.getUnitByTenantId(2L)).thenReturn(Optional.of(unit));
        service.finalizeDueTerminations();
        service.finalizeDueTerminations();
        assertEquals("TERMINATED",lease.getLifecycleStatus());
        assertFalse(lease.isActive()); assertFalse(lease.isPaymentDue());
        assertFalse(tenancy.isActive()); assertFalse(tenancy.isLeaseAccepted()); assertFalse(unit.isOccupied());
        verify(units,times(1)).update(unit);
        verifyNoInteractions(documents);
    }

    @Test void existingTerminationCannotBeOverwrittenOrNotifiedAgain() {
        Lease lease=lease();lease.setSigned(true);lease.setLifecycleStatus("NOTICE_GIVEN");
        when(users.getUserId()).thenReturn(4L);
        when(leases.getLeaseByIdAndStaffOwnerOrTenantId(1L,4L)).thenReturn(Optional.of(lease));
        assertThrows(PMSCustomException.class,()->service.requestTermination(1,
                new org.pms.silverocean.service.lease.wrappers.LeaseTerminationRequest(LocalDate.now().plusMonths(2),"Duplicate request")));
        verify(leases,never()).saveLease(any(),any());
    }

    @Test void terminationCannotBypassTheAgreedNoticePeriod() {
        Lease lease = lease(); lease.setSigned(true); lease.setNoticePeriodInMonths(1);
        when(users.getUserId()).thenReturn(4L);
        when(leases.getLeaseByIdAndStaffOwnerOrTenantId(1L,4L)).thenReturn(Optional.of(lease));
        assertThrows(PMSCustomException.class, () -> service.requestTermination(1,
                new org.pms.silverocean.service.lease.wrappers.LeaseTerminationRequest(LocalDate.now(),"Move out")));
        verify(leases,never()).saveLease(any(),any());
    }

    private Lease lease() { Lease l=new Lease(); l.setId(1L);l.setTenantId(2L);l.setActive(true);l.setLeaseMode("RENT");l.setGovernedDocumentRequired(true);l.setMoveInDate(LocalDate.now().plusDays(1));l.setMoveOutDate(LocalDate.now().plusYears(1));return l; }
    private UnitTenant tenancy() { UnitTenant t=new UnitTenant();t.setId(2L);t.setUnitId(3L);t.setUserId(4L);t.setActive(true);return t; }
    private Unit unit(boolean occupied) { Unit u=new Unit();u.setId(3L);u.setActive(true);u.setOccupied(occupied);u.setLeaseMode("RENT");return u; }
    private Unit rentableUnit(long id,String ref) { Unit u=unit(false);u.setId(id);u.setRef(ref);u.setTemplateId(7L);u.setPrice(25000);u.setCurrency("KES");return u; }
    private Invite tenantInvite(long id,String token) { Invite i=new Invite();i.setId(id);i.setToken(token);i.setType("TENANT");i.setRecipient("tenant@example.test");i.setLeaseStartDate(LocalDate.now().plusDays(1));i.setLeaseEndDate(LocalDate.now().plusYears(1));i.setAgreementTemplateId(21L);i.setExpiryDate(LocalDateTime.now().plusDays(2));i.setActive(true);return i; }
}
