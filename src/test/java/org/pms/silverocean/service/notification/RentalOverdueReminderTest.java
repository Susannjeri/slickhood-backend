package org.pms.silverocean.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.architecture.events.DomainEventOutboxPublisher;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RentalOverdueReminderTest {
    final PMSInvoiceRepo invoices=mock(PMSInvoiceRepo.class);
    final DomainEventOutboxRepo outbox=mock(DomainEventOutboxRepo.class);
    final DomainEventOutboxPublisher publisher=mock(DomainEventOutboxPublisher.class);
    final UserDao users=mock(UserDao.class);
    final NotificationService notifications=mock(NotificationService.class);
    final I18NService i18n=mock(I18NService.class);
    final ObjectMapper mapper=new ObjectMapper();
    final RentalOverdueReminder service=new RentalOverdueReminder(invoices,outbox,publisher,users,notifications,i18n,mapper);

    @Test void rolloutIsExplicitAndDoesNotBlastHistoricalCustomersByDefault() {
        service.schedule(); verifyNoInteractions(invoices,publisher);
    }
    @Test void cursorPaginationDoesNotSkipMoreThan100Invoices() {
        ReflectionTestUtils.setField(service,"enabled",true);
        var batch=new ArrayList<PMSInvoice>(); for(long id=1;id<=100;id++)batch.add(invoice(id));
        when(invoices.findRentalReminderCandidates(any(),eq(0L),any())).thenReturn(batch);
        when(invoices.findRentalReminderCandidates(any(),eq(100L),any())).thenReturn(List.of(invoice(101)));
        service.schedule(); verify(publisher,times(101)).publish(any(),eq("INVOICE"),any(),any(),any());
        verify(invoices).findRentalReminderCandidates(any(),eq(100L),any());
    }
    @Test void paidOrZeroBalanceInvoiceSuppressesStaleQueuedReminder() throws Exception {
        var invoice=invoice(1); invoice.setPendingAmount(0); var event=event(invoice);
        when(invoices.findByIdForUpdate(1)).thenReturn(Optional.of(invoice));
        service.handle(event); verifyNoInteractions(notifications); assertEquals("PROCESSED",event.getStatus());
    }
    @Test void postponedDueDateSuppressesOldOverdueNotice() throws Exception {
        var invoice=invoice(1); var event=event(invoice); invoice.setDueDate(LocalDate.now().plusDays(4));
        when(invoices.findByIdForUpdate(1)).thenReturn(Optional.of(invoice));
        service.handle(event); verifyNoInteractions(notifications);
    }
    @Test void repeatedDispatchDoesNotQueueTwiceAndUsesCurrentPartialBalance() throws Exception {
        var invoice=invoice(1); var event=event(invoice);
        when(invoices.findByIdForUpdate(1)).thenReturn(Optional.of(invoice));
        Users tenant=new Users(); tenant.setActive(true);tenant.setEmail("tenant@example.test");tenant.setFullName("<img src=x>");
        when(users.findById(22)).thenReturn(Optional.of(tenant));
        when(i18n.getLocalizedMessage(NotificationType.RENT_PAYMENT_REMINDER_EMAIL.getBody())).thenReturn("%s invoice %s balance %s %s due %s");
        service.handle(event); service.handle(event);
        var sent=org.mockito.ArgumentCaptor.forClass(NotificationDTO.class); verify(notifications).queueNotification(sent.capture());
        assertEquals("tenant@example.test",sent.getValue().recipient());
        assertTrue(sent.getValue().formattedMessage().contains("250.00 KES"));
        assertFalse(sent.getValue().formattedMessage().contains("<img"));
    }
    private PMSInvoice invoice(long id){PMSInvoice i=new PMSInvoice();i.setId(id);i.setActive(true);i.setBillingType("RENTAL");i.setPendingAmount(250);i.setBilledUserId(22);i.setCurrency("KES");i.setRef("INV-"+id);i.setDueDate(LocalDate.now().minusDays(2));return i;}
    private DomainEventOutbox event(PMSInvoice invoice) throws Exception {DomainEventOutbox e=new DomainEventOutbox();e.setId(7L);e.setStatus("PROCESSING");e.setPayload(mapper.writeValueAsString(new RentalOverdueReminder.Reminder(invoice.getId(),invoice.getDueDate().toString())));when(outbox.lockForNotification(7)).thenReturn(Optional.of(e));return e;}
}
