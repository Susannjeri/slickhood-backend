package org.pms.silverocean.service.estate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.EstateServiceChargeRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.entities.DomainEventOutbox;
import org.pms.silverocean.database.pms.entities.EstateServiceCharge;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.pms.silverocean.service.payment.latefee.LateFeePolicyService;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceChargeReminderHandlerTest {
    @Mock EstateServiceChargeRepo charges;
    @Mock InvoiceDao invoices;
    @Mock UnitRepo units;
    @Mock UserDao users;
    @Mock I18NService i18n;
    @Mock NotificationService notifications;
    @Mock LateFeePolicyService lateFees;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void overdueEventQueuesOnlyTheHomeownersCurrentUnpaidBalance() throws Exception {
        EstateServiceCharge charge = charge();
        PMSInvoice invoice = invoice(false);
        Users homeowner = new Users(); homeowner.setId(20L); homeowner.setActive(true); homeowner.setEmail("owner@example.com"); homeowner.setFullName("Amina Owner");
        Unit unit = new Unit(); unit.setId(77L); unit.setRef("A-101");
        when(charges.findById(5L)).thenReturn(Optional.of(charge));
        when(invoices.getInvoiceById(9L)).thenReturn(Optional.of(invoice));
        when(users.findById(20L)).thenReturn(Optional.of(homeowner));
        when(units.findById(77L)).thenReturn(Optional.of(unit));
        when(i18n.getLocalizedMessage(NotificationType.SERVICE_CHARGE_OVERDUE_EMAIL.getBody()))
                .thenReturn("Hello %s, balance %s %s for %s due %s invoice %s");

        handler().handle(event(ServiceChargeReminderEvent.Phase.OVERDUE));

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(notifications).queueEmailAndInAppOnce(anyString(),eq("owner@example.com"),
                eq(NotificationType.SERVICE_CHARGE_OVERDUE_EMAIL),sent.capture(),
                eq("SERVICE_CHARGE_OVERDUE"),anyString(),eq("/dashboard/invoices"));
        assertThat(sent.getValue()).contains("2500.00 KES", "A-101", "INV-9");
    }

    @Test
    void overdueEventAlsoAlertsTheEstatePayeeWithoutEndingOwnership() throws Exception {
        EstateServiceCharge charge = charge();
        PMSInvoice invoice = invoice(false); invoice.setBilledUserId(20L); invoice.setPayToUserId(30L);
        Users homeowner = user(20L, "owner@example.com", "Amina Owner");
        Users estateManager = user(30L, "estate@example.com", "Estate Manager");
        Unit unit = new Unit(); unit.setId(77L); unit.setRef("A-101");
        when(charges.findById(5L)).thenReturn(Optional.of(charge));
        when(invoices.getInvoiceById(9L)).thenReturn(Optional.of(invoice));
        when(users.findById(20L)).thenReturn(Optional.of(homeowner));
        when(users.findById(30L)).thenReturn(Optional.of(estateManager));
        when(units.findById(77L)).thenReturn(Optional.of(unit));
        when(i18n.getLocalizedMessage(NotificationType.SERVICE_CHARGE_OVERDUE_EMAIL.getBody()))
                .thenReturn("Hello %s, balance %s %s for %s due %s invoice %s");
        when(i18n.getLocalizedMessage(NotificationType.RECEIVABLE_OVERDUE_EMAIL.getBody()))
                .thenReturn("Invoice %s balance %s %s due %s");

        handler().handle(event(ServiceChargeReminderEvent.Phase.OVERDUE));

        verify(notifications).queueEmailAndInAppOnce(anyString(),eq("estate@example.com"),
                eq(NotificationType.RECEIVABLE_OVERDUE_EMAIL), anyString(),
                eq("SERVICE_CHARGE_RECEIVABLE_OVERDUE"), contains("must not be ended automatically"),eq("/dashboard/invoices"));
    }

    @Test
    void paidInvoiceSuppressesAStaleQueuedReminder() throws Exception {
        when(charges.findById(5L)).thenReturn(Optional.of(charge()));
        when(invoices.getInvoiceById(9L)).thenReturn(Optional.of(invoice(true)));

        handler().handle(event(ServiceChargeReminderEvent.Phase.PRE_DUE));

        verify(notifications, never()).queueEmailAndInAppOnce(anyString(),anyString(), any(), anyString(), anyString(), anyString(),anyString());
    }

    @Test void deferredDueDateSuppressesOldOverdueNotice() throws Exception {
        var charge=charge();charge.setDueDate(LocalDate.now().plusDays(2));
        when(charges.findById(5L)).thenReturn(Optional.of(charge));
        when(invoices.getInvoiceById(9L)).thenReturn(Optional.of(invoice(false)));
        handler().handle(event(ServiceChargeReminderEvent.Phase.OVERDUE));
        verify(notifications,never()).queueEmailAndInAppOnce(anyString(),anyString(), any(), anyString(), anyString(), anyString(),anyString());
    }

    @Test void zeroBalanceSuppressesReminderEvenWhenLegacyPaidFlagIsFalse() throws Exception {
        var invoice=invoice(false);invoice.setPendingAmount(0);
        when(charges.findById(5L)).thenReturn(Optional.of(charge()));
        when(invoices.getInvoiceById(9L)).thenReturn(Optional.of(invoice));
        handler().handle(event(ServiceChargeReminderEvent.Phase.OVERDUE));
        verify(notifications,never()).queueEmailAndInAppOnce(anyString(),anyString(), any(), anyString(), anyString(), anyString(),anyString());
    }

    private ServiceChargeReminderHandler handler() {
        return new ServiceChargeReminderHandler(mapper, charges, invoices, units, users, i18n, notifications, lateFees);
    }
    private DomainEventOutbox event(ServiceChargeReminderEvent.Phase phase) throws Exception {
        DomainEventOutbox event = new DomainEventOutbox();
        event.setPayload(mapper.writeValueAsString(new ServiceChargeReminderEvent(5L, phase)));
        return event;
    }
    private EstateServiceCharge charge() {
        EstateServiceCharge charge = new EstateServiceCharge(); charge.setId(5L); charge.setInvoiceId(9L); charge.setUnitId(77L);
        charge.setHomeownerUserId(20L); charge.setDueDate(LocalDate.of(2026, 8, 20)); charge.setActive(true); return charge;
    }
    private PMSInvoice invoice(boolean paid) {
        PMSInvoice invoice = new PMSInvoice(); invoice.setId(9L); invoice.setRef("INV-9"); invoice.setCurrency("KES");
        invoice.setPendingAmount(2500); invoice.setPaid(paid); invoice.setActive(true); return invoice;
    }
    private Users user(long id, String email, String name) {
        Users user = new Users(); user.setId(id); user.setActive(true); user.setEmail(email); user.setFullName(name); return user;
    }
}
