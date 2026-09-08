package org.pms.silverocean.service.estate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.EstateServiceChargeRepo;
import org.pms.silverocean.database.pms.PropertyOwnershipRepo;
import org.pms.silverocean.database.pms.PropertyRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.PropertyOwnership;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.payment.invoice.InvoiceService;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
class EstateServiceTest {
    @Mock PropertyOwnershipRepo ownerships;
    @Mock PropertyRepo properties;
    @Mock UnitRepo units;
    @Mock UserDao users;
    @Mock EstateServiceChargeRepo charges;
    @Mock InvoiceService invoices;
    @Mock NotificationService notifications;
    @Mock I18NService i18n;
    @Mock org.pms.silverocean.service.teamaccess.WorkspaceSelectionService workspaces;
    private Property estate() {
        Property p = new Property(); p.setId(11L); p.setActive(true);
        p.setManagementMode(org.pms.silverocean.service.property.PMSPropertyManagementMode.SERVICE_CHARGE); return p;
    }
    private void member() {
        var m = new org.pms.silverocean.database.pms.entities.WorkspaceMembership(); m.setId(5L);
        when(workspaces.selectedMembership(999L)).thenReturn(Optional.of(m));
    }

    private EstateService service;
    private Unit unit;
    private Users homeowner;

    @BeforeEach
    void setUp() {
        service = new EstateService(ownerships, properties, units, users, charges, invoices, notifications, i18n, new EstateAccessService(properties, users, workspaces));
        unit = new Unit();
        unit.setId(77L);
        unit.setPropertyId(11L);
        unit.setActive(true);
        unit.setLeaseMode("SERVICE_CHARGE");
        homeowner = new Users();
        homeowner.setId(200L);
        homeowner.setActive(true);
    }

    @Test
    void homeownerInviteCreatesOwnershipHistoryInsteadOfStaffAccess() {
        Property property = estate();
        property.setId(11L);
        property.setActive(true);
        when(users.findById(homeowner.getId())).thenReturn(Optional.of(homeowner));
        when(units.findAndLockById(unit.getId())).thenReturn(Optional.of(unit));
        when(properties.findByIdAndHomeownerInviter(unit.getPropertyId(), 999L)).thenReturn(Optional.of(property));
        when(ownerships.findCurrentForUpdate(unit.getId())).thenReturn(Optional.empty());

        service.createOwnershipFromInvite(unit.getId(), homeowner.getId(), 999L);

        ArgumentCaptor<PropertyOwnership> saved = ArgumentCaptor.forClass(PropertyOwnership.class);
        verify(ownerships).save(saved.capture());
        assertEquals(homeowner.getId(), saved.getValue().getHomeownerUserId());
        assertEquals(unit.getId(), saved.getValue().getUnitId());
        assertEquals("HOMEOWNER_INVITE", saved.getValue().getSource());
        assertEquals(999L, saved.getValue().getCreatedBy());
    }

    @Test
    void repeatedHomeownerInviteIsIdempotent() {
        PropertyOwnership current = new PropertyOwnership();
        current.setUnitId(unit.getId());
        current.setHomeownerUserId(homeowner.getId());
        current.setActive(true);
        when(users.findById(homeowner.getId())).thenReturn(Optional.of(homeowner));
        when(units.findAndLockById(unit.getId())).thenReturn(Optional.of(unit));
        when(properties.findByIdAndHomeownerInviter(unit.getPropertyId(), 999L)).thenReturn(Optional.of(estate()));
        when(ownerships.findCurrentForUpdate(unit.getId())).thenReturn(Optional.of(current));

        PropertyOwnership result = service.createOwnershipFromInvite(unit.getId(), homeowner.getId(), 999L);

        assertSame(current, result);
        verify(ownerships, never()).save(current);
    }

    @Test
    void ownershipTransferCannotPrecedeCurrentOwnership() {
        LocalDate existingStart = LocalDate.of(2026, 8, 1);
        PropertyOwnership current = new PropertyOwnership();
        current.setUnitId(unit.getId());
        current.setHomeownerUserId(300L);
        current.setOwnershipStart(existingStart);
        current.setActive(true);
        when(users.getUserId()).thenReturn(999L);
        when(users.findById(homeowner.getId())).thenReturn(Optional.of(homeowner));
        when(properties.findByIdAndCreatedByAndActiveTrue(11L, 999L)).thenReturn(Optional.of(estate()));
        when(users.getActiveRole()).thenReturn(PMSRole.ESTATE_MANAGER);
        when(users.hasPermission(Permission.MANAGE_ESTATE)).thenReturn(true);
        when(units.findAndLockById(unit.getId())).thenReturn(Optional.of(unit));
        when(ownerships.findCurrentForUpdate(unit.getId())).thenReturn(Optional.of(current));

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> service.create(new OwnershipRequest(11L, unit.getId(), homeowner.getId(), existingStart, "TRANSFER")));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, error.getResponseCode());
    }

    @Test
    void serviceChargeRejectsCurrencyThatDoesNotMatchTheUnit() {
        PropertyOwnership ownership = new PropertyOwnership();
        ownership.setId(88L);
        ownership.setPropertyId(11L);
        ownership.setUnitId(unit.getId());
        ownership.setHomeownerUserId(homeowner.getId());
        ownership.setActive(true);
        unit.setCurrency("KES");
        when(ownerships.findActiveForUpdate(88L)).thenReturn(Optional.of(ownership));
        when(users.getUserId()).thenReturn(999L);
        when(users.getActiveRole()).thenReturn(PMSRole.ESTATE_MANAGER);
        when(users.hasPermission(Permission.CREATE_SERVICE_CHARGE)).thenReturn(true);
        when(properties.findByIdAndCreatedByAndActiveTrue(11L, 999L)).thenReturn(Optional.of(estate()));
        when(units.findById(unit.getId())).thenReturn(Optional.of(unit));

        PMSCustomException error = assertThrows(PMSCustomException.class, () -> service.createServiceCharge(
                new ServiceChargeRequest(88L, new BigDecimal("1500.00"), "USD", LocalDate.now(), "Security")));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, error.getResponseCode());
        verify(invoices, never()).createPropertyInvoice(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ownershipCannotBeEndedInTheFuture() {
        PropertyOwnership ownership = new PropertyOwnership();
        ownership.setId(88L); ownership.setPropertyId(11L); ownership.setHomeownerUserId(200L);
        LocalDate today = LocalDate.now(org.pms.silverocean.common.PMSUtils.getZoneId());
        ownership.setOwnershipStart(today.minusYears(1)); ownership.setActive(true);
        when(ownerships.findActiveForUpdate(88L)).thenReturn(Optional.of(ownership));
        when(users.getUserId()).thenReturn(999L);
        when(users.getActiveRole()).thenReturn(PMSRole.ESTATE_MANAGER);
        when(users.hasPermission(Permission.MANAGE_ESTATE)).thenReturn(true);
        when(properties.findByIdAndCreatedByAndActiveTrue(11L, 999L)).thenReturn(Optional.of(estate()));

        PMSCustomException error = assertThrows(PMSCustomException.class, () -> service.end(88L,
                new OwnershipTerminationRequest(today.plusDays(1), "Sale completed")));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, error.getResponseCode());
        verify(ownerships, never()).save(ownership);
    }

    @Test
    void delegatedEstateOperatorCanManageItsAssignedProperty() {
        when(users.getUserId()).thenReturn(999L);
        when(users.getActiveRole()).thenReturn(PMSRole.ESTATE_OPERATIONS_MANAGER);
        when(users.hasPermission(Permission.MANAGE_ESTATE)).thenReturn(true);
        member();
        when(properties.findByIdAndManagerRoleAndInviteId(11L, 999L, "ESTATE_OPERATIONS_MANAGER", -5L)).thenReturn(Optional.of(estate()));
        when(users.findById(homeowner.getId())).thenReturn(Optional.of(homeowner));
        when(units.findAndLockById(unit.getId())).thenReturn(Optional.of(unit));
        when(ownerships.findCurrentForUpdate(unit.getId())).thenReturn(Optional.empty());

        service.create(new OwnershipRequest(11L, unit.getId(), homeowner.getId(), LocalDate.now(), "ONBOARDING"));

        verify(properties).findByIdAndManagerRoleAndInviteId(11L, 999L, "ESTATE_OPERATIONS_MANAGER", -5L);
        verify(ownerships).save(any(PropertyOwnership.class));
    }

    @Test
    void delegatedViewerGetsAPropertyScopedPagedRegistry() {
        PageRequest request = PageRequest.of(0, 25);
        when(users.getUserId()).thenReturn(999L);
        when(users.getActiveRole()).thenReturn(PMSRole.WORKSPACE_VIEWER);
        when(users.hasPermission(Permission.VIEW_ESTATE)).thenReturn(true);
        member();
        when(ownerships.findPageByEstateScope(999L, false, "WORKSPACE_VIEWER", -5L, 11L, true, request))
                .thenReturn(new PageImpl<>(java.util.List.of(), request, 0));

        service.list(request, 11L, true);

        verify(ownerships).findPageByEstateScope(999L, false, "WORKSPACE_VIEWER", -5L, 11L, true, request);
    }

    @Test
    void completedSaleCreatesOwnershipWithoutRequiringEstateRole() {
        when(users.getUserId()).thenReturn(555L);
        when(users.findById(homeowner.getId())).thenReturn(Optional.of(homeowner));
        when(units.findAndLockById(unit.getId())).thenReturn(Optional.of(unit));
        when(ownerships.findCurrentForUpdate(unit.getId())).thenReturn(Optional.empty());
        when(ownerships.save(any(PropertyOwnership.class))).thenAnswer(call -> call.getArgument(0));

        PropertyOwnership ownership = service.transferFromSale(11L, unit.getId(), homeowner.getId(), 91L);

        assertEquals(91L, ownership.getSourceSaleTransactionId());
        assertEquals("SALE_COMPLETION", ownership.getSource());
        assertEquals(555L, ownership.getCreatedBy());
        verify(properties, never()).findByIdAndStaffOrOwner(11L, 555L);
    }

    @Test
    void repeatedCompletionForTheSameSaleIsIdempotent() {
        PropertyOwnership completed = new PropertyOwnership();
        completed.setPropertyId(11L); completed.setUnitId(unit.getId());
        completed.setHomeownerUserId(homeowner.getId()); completed.setSourceSaleTransactionId(91L);
        when(ownerships.findBySourceSaleTransactionId(91L)).thenReturn(Optional.of(completed));

        PropertyOwnership result = service.transferFromSale(11L, unit.getId(), homeowner.getId(), 91L);

        assertEquals(completed, result);
        verify(units, never()).findAndLockById(unit.getId());
        verify(ownerships, never()).save(any());
    }
    @Test
    void estateOwnerServiceChargesRemainPropertyScopedAndPaged() {
        PageRequest request = PageRequest.of(0, 25);
        when(users.getUserId()).thenReturn(999L);
        when(users.getActiveRole()).thenReturn(PMSRole.ESTATE_MANAGER);
        when(charges.findPageByEstateScope(999L, true, "ESTATE_MANAGER", null, 11L, request))
                .thenReturn(new PageImpl<>(java.util.List.of(), request, 0));

        service.listServiceCharges(request, 11L);

        verify(charges).findPageByEstateScope(999L, true, "ESTATE_MANAGER", null, 11L, request);
    }

    @Test
    void delegatedFinanceViewerCannotEscapeAssignedPropertyScope() {
        PageRequest request = PageRequest.of(0, 25);
        when(users.getUserId()).thenReturn(999L);
        when(users.getActiveRole()).thenReturn(PMSRole.PROPERTY_ACCOUNTANT);
        when(users.hasPermission(Permission.VIEW_SERVICE_CHARGE)).thenReturn(true);
        member();
        when(charges.findPageByEstateScope(999L, false, "PROPERTY_ACCOUNTANT", -5L, 11L, request))
                .thenReturn(new PageImpl<>(java.util.List.of(), request, 0));

        service.listServiceCharges(request, 11L);

        verify(charges).findPageByEstateScope(999L, false, "PROPERTY_ACCOUNTANT", -5L, 11L, request);
    }

    @Test
    void estateOwnerSeesOwnRegistryWithoutAnEmployeeRow() {
        var page = PageRequest.of(0, 25);
        when(users.getUserId()).thenReturn(999L);
        when(users.getActiveRole()).thenReturn(PMSRole.ESTATE_MANAGER);
        service.list(page, 11L, true);
        verify(ownerships).findPageByEstateScope(999L, true, "ESTATE_MANAGER", null, 11L, true, page);
    }

    @Test
    void futureOwnershipCannotRevokeTheCurrentHomeownerEarly() {
        when(users.getUserId()).thenReturn(999L);
        when(users.getActiveRole()).thenReturn(PMSRole.ESTATE_MANAGER);
        when(users.hasPermission(Permission.MANAGE_ESTATE)).thenReturn(true);
        when(properties.findByIdAndCreatedByAndActiveTrue(11L,999L)).thenReturn(Optional.of(estate()));
        assertThrows(PMSCustomException.class, () -> service.create(new OwnershipRequest(11L,77L,200L,
                LocalDate.now(org.pms.silverocean.common.PMSUtils.getZoneId()).plusDays(1),"TRANSFER")));
        verify(ownerships,never()).save(any());
        verifyNoInvoice();
    }

    @Test
    void serviceChargeCannotBillAUnitFromAnotherPropertyOrARental() {
        PropertyOwnership o = new PropertyOwnership(); o.setId(88L); o.setPropertyId(11L);
        o.setUnitId(77L); o.setHomeownerUserId(200L); o.setActive(true);
        when(ownerships.findActiveForUpdate(88L)).thenReturn(Optional.of(o));
        when(users.getUserId()).thenReturn(999L);
        when(users.getActiveRole()).thenReturn(PMSRole.ESTATE_MANAGER);
        when(users.hasPermission(Permission.CREATE_SERVICE_CHARGE)).thenReturn(true);
        when(properties.findByIdAndCreatedByAndActiveTrue(11L,999L)).thenReturn(Optional.of(estate()));
        when(units.findById(77L)).thenReturn(Optional.of(unit));
        var request = new ServiceChargeRequest(88L,new BigDecimal("1000.00"),"KES",LocalDate.now(),"Security");
        unit.setPropertyId(12L);
        assertThrows(PMSCustomException.class, () -> service.createServiceCharge(request));
        unit.setPropertyId(11L); unit.setLeaseMode("RENT");
        assertThrows(PMSCustomException.class, () -> service.createServiceCharge(request));
        verifyNoInvoice();
    }

    @Test
    void estateAccountantCreatesInvoiceForCurrentHomeowner() {
        PropertyOwnership o = new PropertyOwnership(); o.setId(88L); o.setPropertyId(11L);
        o.setUnitId(77L); o.setHomeownerUserId(200L); o.setActive(true);
        when(ownerships.findActiveForUpdate(88L)).thenReturn(Optional.of(o));
        when(users.getUserId()).thenReturn(999L);
        when(users.getActiveRole()).thenReturn(PMSRole.PROPERTY_ACCOUNTANT);
        when(users.hasPermission(Permission.CREATE_SERVICE_CHARGE)).thenReturn(true);
        member();
        when(properties.findByIdAndManagerRoleAndInviteId(11L,999L,"PROPERTY_ACCOUNTANT",-5L)).thenReturn(Optional.of(estate()));
        unit.setCurrency("KES"); when(units.findById(77L)).thenReturn(Optional.of(unit));
        PMSInvoice invoice = new PMSInvoice(); invoice.setId(91L);
        when(invoices.createPropertyInvoice(77L,200L,java.util.Map.of("Security",1000.0),"SERVICE_CHARGE",LocalDate.now())).thenReturn(invoice);
        when(charges.save(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.createServiceCharge(new ServiceChargeRequest(88L,new BigDecimal("1000.00"),"KES",LocalDate.now(),"Security"));
        assertEquals(91L,result.getInvoiceId()); assertEquals(200L,result.getHomeownerUserId());
        assertEquals(11L,result.getPropertyId());
        verify(users,never()).hasPermission(Permission.MANAGE_ESTATE);
    }

    @Test
    void homeownerDirectorySearchRemainsInsideTheSelectedWorkspace() {
        PageRequest page = PageRequest.of(0, 25);
        when(users.getUserId()).thenReturn(999L);
        when(users.getActiveRole()).thenReturn(PMSRole.WORKSPACE_VIEWER);
        when(users.hasPermission(Permission.VIEW_ESTATE)).thenReturn(true);
        member();
        when(ownerships.findPageByEstateScope(999L, false, "WORKSPACE_VIEWER", -5L, 11L, true, "amina", page))
                .thenReturn(new PageImpl<>(java.util.List.of(), page, 0));

        service.list(page, 11L, true, "  amina  ");

        verify(ownerships).findPageByEstateScope(999L, false, "WORKSPACE_VIEWER", -5L, 11L, true, "amina", page);
    }

    @Test
    void superAdminDoesNotReceiveAPlatformWideHomeownerDirectory() {
        PageRequest page = PageRequest.of(0, 25);
        when(users.getUserId()).thenReturn(999L);
        when(users.getActiveRole()).thenReturn(PMSRole.SUPER_ADMIN);

        assertEquals(0, service.list(page, null, null).getTotalElements());

        org.mockito.Mockito.verifyNoInteractions(ownerships);
    }

    private void verifyNoInvoice() {
        org.mockito.Mockito.verifyNoInteractions(invoices);
    }
}
