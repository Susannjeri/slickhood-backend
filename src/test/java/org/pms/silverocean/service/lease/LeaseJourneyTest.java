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
    @InjectMocks LeaseService service;

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
    private Lease lease() { Lease l=new Lease(); l.setId(1L);l.setTenantId(2L);l.setActive(true);l.setLeaseMode("RENT");l.setGovernedDocumentRequired(true);l.setMoveInDate(LocalDate.now().plusDays(1));return l; }
    private UnitTenant tenancy() { UnitTenant t=new UnitTenant();t.setId(2L);t.setUnitId(3L);t.setUserId(4L);t.setActive(true);return t; }
    private Unit unit(boolean occupied) { Unit u=new Unit();u.setId(3L);u.setActive(true);u.setOccupied(occupied);u.setLeaseMode("RENT");return u; }
}
