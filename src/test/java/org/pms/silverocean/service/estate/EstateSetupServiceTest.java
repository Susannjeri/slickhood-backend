package org.pms.silverocean.service.estate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.EstateBudgetRepo;
import org.pms.silverocean.database.pms.PropertyAccountRepo;
import org.pms.silverocean.database.pms.PropertyManagerRepo;
import org.pms.silverocean.database.pms.PropertyOwnershipRepo;
import org.pms.silverocean.database.pms.PropertyRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.property.PMSPropertyManagementMode;

import java.time.Year;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstateSetupServiceTest {
    @Mock PropertyRepo properties;
    @Mock UnitRepo units;
    @Mock PropertyManagerRepo managers;
    @Mock PropertyAccountRepo accounts;
    @Mock PropertyOwnershipRepo ownerships;
    @Mock EstateBudgetRepo budgets;
    @Mock UserDao users;

    private EstateSetupService service;

    @BeforeEach
    void setUp() {
        service = new EstateSetupService(properties, units, managers, accounts, ownerships, budgets, users);
        when(users.getUserId()).thenReturn(99L);
    }

    @Test
    void inaccessiblePropertyDoesNotExposeSetupInformation() {
        when(properties.findByIdAndStaffOrOwner(10L, 99L)).thenReturn(Optional.empty());

        PMSCustomException error = assertThrows(PMSCustomException.class, () -> service.getStatus(10L));

        assertThat(error.getResponseCode()).isEqualTo(ResponseCode.PROPERTY_NOT_FOUND);
        verify(units, never()).countAllByPropertyIdAndActiveTrue(10L);
    }

    @Test
    void serviceChargeSetupBeginsWithUnits() {
        stubProperty(PMSPropertyManagementMode.SERVICE_CHARGE);

        EstateSetupStatus status = service.getStatus(10L);

        assertThat(status.nextAction()).isEqualTo(EstateSetupStatus.NextAction.ADD_UNITS);
        assertThat(status.readyForHomeownerOperations()).isFalse();
    }

    @Test
    void serviceChargeSetupRequiresHomeownersAfterUnitsAndAccount() {
        stubProperty(PMSPropertyManagementMode.SERVICE_CHARGE);
        when(units.countAllByPropertyIdAndActiveTrue(10L)).thenReturn(20);
        when(accounts.countByPropertyIdAndActiveTrue(10L)).thenReturn(1L);

        EstateSetupStatus status = service.getStatus(10L);

        assertThat(status.nextAction()).isEqualTo(EstateSetupStatus.NextAction.ASSIGN_HOMEOWNERS);
        assertThat(status.unitsConfigured()).isTrue();
        assertThat(status.billingConfigured()).isTrue();
    }

    @Test
    void serviceChargeSetupIsReadyOnlyAfterOwnershipAndCurrentBudget() {
        stubProperty(PMSPropertyManagementMode.SERVICE_CHARGE);
        when(units.countAllByPropertyIdAndActiveTrue(10L)).thenReturn(20);
        when(accounts.countByPropertyIdAndActiveTrue(10L)).thenReturn(1L);
        when(ownerships.countByPropertyIdAndActiveTrue(10L)).thenReturn(20L);
        when(budgets.countByPropertyIdAndBudgetYearAndActiveTrue(10L, Year.now().getValue())).thenReturn(1L);
        when(managers.countByPropertyIdAndActiveTrue(10L)).thenReturn(1L);

        EstateSetupStatus status = service.getStatus(10L);

        assertThat(status.nextAction()).isEqualTo(EstateSetupStatus.NextAction.READY);
        assertThat(status.readyForHomeownerOperations()).isTrue();
        assertThat(status.activeHomeowners()).isEqualTo(20);
    }

    private void stubProperty(PMSPropertyManagementMode mode) {
        Property property = new Property();
        property.setId(10L);
        property.setName("Green Court");
        property.setManagementMode(mode);
        property.setActive(true);
        when(properties.findByIdAndStaffOrOwner(10L, 99L)).thenReturn(Optional.of(property));
    }
}
