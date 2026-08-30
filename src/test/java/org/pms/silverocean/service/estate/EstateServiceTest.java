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

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

    private EstateService service;
    private Unit unit;
    private Users homeowner;

    @BeforeEach
    void setUp() {
        service = new EstateService(ownerships, properties, units, users, charges, invoices, notifications, i18n);
        unit = new Unit();
        unit.setId(77L);
        unit.setPropertyId(11L);
        unit.setActive(true);
        homeowner = new Users();
        homeowner.setId(200L);
        homeowner.setActive(true);
    }

    @Test
    void homeownerInviteCreatesOwnershipHistoryInsteadOfStaffAccess() {
        Property property = new Property();
        property.setId(11L);
        property.setActive(true);
        when(users.findById(homeowner.getId())).thenReturn(Optional.of(homeowner));
        when(units.findAndLockById(unit.getId())).thenReturn(Optional.of(unit));
        when(properties.findByIdAndStaffOrOwner(unit.getPropertyId(), 999L)).thenReturn(Optional.of(property));
        when(ownerships.findFirstByUnitIdAndActiveTrue(unit.getId())).thenReturn(Optional.empty());

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
        when(properties.findByIdAndStaffOrOwner(unit.getPropertyId(), 999L)).thenReturn(Optional.of(new Property()));
        when(ownerships.findFirstByUnitIdAndActiveTrue(unit.getId())).thenReturn(Optional.of(current));

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
        when(properties.findByIdAndCreatedByAndActiveTrue(11L, 999L)).thenReturn(Optional.of(new Property()));
        when(users.getActiveRole()).thenReturn(org.pms.silverocean.service.auth.roles.enums.PMSRole.LANDLORD);
        when(units.findAndLockById(unit.getId())).thenReturn(Optional.of(unit));
        when(ownerships.findFirstByUnitIdAndActiveTrue(unit.getId())).thenReturn(Optional.of(current));

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
        when(ownerships.findById(88L)).thenReturn(Optional.of(ownership));
        when(users.getUserId()).thenReturn(999L);
        when(users.getActiveRole()).thenReturn(org.pms.silverocean.service.auth.roles.enums.PMSRole.LANDLORD);
        when(properties.findByIdAndCreatedByAndActiveTrue(11L, 999L)).thenReturn(Optional.of(new Property()));
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
        ownership.setOwnershipStart(LocalDate.now().minusYears(1)); ownership.setActive(true);
        when(ownerships.findById(88L)).thenReturn(Optional.of(ownership));
        when(users.getUserId()).thenReturn(999L);
        when(users.getActiveRole()).thenReturn(org.pms.silverocean.service.auth.roles.enums.PMSRole.LANDLORD);
        when(properties.findByIdAndCreatedByAndActiveTrue(11L, 999L)).thenReturn(Optional.of(new Property()));

        PMSCustomException error = assertThrows(PMSCustomException.class, () -> service.end(88L,
                new OwnershipTerminationRequest(LocalDate.now().plusDays(1), "Sale completed")));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, error.getResponseCode());
        verify(ownerships, never()).save(ownership);
    }
}
