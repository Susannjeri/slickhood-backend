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
    @Test void tenantCannotEditDatesAfterDraftSnapshotExists() {
        when(users.getUserId()).thenReturn(4L);
        when(leases.getLeaseByIdAndTenantId(1L,4L)).thenReturn(Optional.of(lease()));
        when(documents.existsCurrentAgreement(1L)).thenReturn(true);
        assertThrows(PMSCustomException.class, () -> service.tenantEditLease(1,LocalDate.now(),LocalDate.now().plusYears(1)));
        verify(leases,never()).saveLease(any(),any());
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
}
