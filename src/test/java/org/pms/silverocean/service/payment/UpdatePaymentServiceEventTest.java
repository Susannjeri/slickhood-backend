package org.pms.silverocean.service.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.architecture.events.DomainEventOutboxPublisher;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.payment.contract.InvoicePaidEvent;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.pms.silverocean.service.payment.ledger.FinancialLedgerService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.database.pms.entities.Users;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class UpdatePaymentServiceEventTest {
    @Mock NotificationService notifications;@Mock InvoiceDao invoices;@Mock I18NService i18n;@Mock DomainEventOutboxPublisher events;@Mock FinancialLedgerService ledger;@Mock UserDao users;UpdatePaymentService service;
    @BeforeEach void setup(){service=new UpdatePaymentService(notifications,invoices,i18n,events,ledger,users);lenient().when(i18n.getLocalizedMessage(anyString())).thenReturn("%s");lenient().when(ledger.recordPaymentApplied(any(),anyString(),any())).thenReturn(true);}
    @Test void fullyPaidInvoiceIsSavedThenPublishedAsVersionedEvent(){PMSInvoice invoice=invoice(100);service.setInvoiceToPaid(invoice,"PAY-1",100);verify(invoices).saveInvoice(invoice);verify(events).publish(eq(InvoicePaidEvent.TYPE),eq("INVOICE"),eq("10"),eq("invoice.paid.v1:10"),any(InvoicePaidEvent.class));}
    @Test void partialPaymentDoesNotPublishPaidEvent(){PMSInvoice invoice=invoice(100);service.setInvoiceToPaid(invoice,"PAY-2",40);verify(invoices).saveInvoice(invoice);verifyNoInteractions(events);}
    @Test void duplicatePaymentDoesNotReduceBalanceAgain(){PMSInvoice invoice=invoice(100);when(ledger.recordPaymentApplied(any(),eq("PAY-3"),any())).thenReturn(false);service.setInvoiceToPaid(invoice,"PAY-3",40);assertEquals(100,invoice.getPendingAmount());verify(invoices,never()).saveInvoice(invoice);}
    @Test void overpaymentPaysInvoiceWithoutNegativeBalanceAndBooksCredit(){PMSInvoice invoice=invoice(100);service.setInvoiceToPaid(invoice,"PAY-4",125);assertEquals(0,invoice.getPendingAmount());verify(ledger).recordPaymentApplied(eq(invoice),eq("PAY-4"),eq(new java.math.BigDecimal("100.00")));verify(ledger).recordUnappliedCredit(eq(invoice),eq("PAY-4"),eq(new java.math.BigDecimal("25.00")));}
    @Test void appliedPaymentQueuesEmailAndInAppReceiptForThePayer(){PMSInvoice invoice=invoice(100);invoice.setCustomerEmail("payer@example.test");when(i18n.getLocalizedMessage(NotificationType.PAYMENT_RECEIPT_EMAIL.getBody())).thenReturn("Payment %s %s %s invoice %s balance %s %s");service.setInvoiceToPaid(invoice,"PAY-5",40);verify(notifications).queueEmailAndInApp(eq("payer@example.test"),eq(NotificationType.PAYMENT_RECEIPT_EMAIL),contains("PAY-5"),eq("PARTIAL_PAYMENT_RECEIVED"),contains("Outstanding balance: KES 60.00"));}
    @Test void appliedPaymentNotifiesThePayee(){PMSInvoice invoice=invoice(100);invoice.setPayToUserId(22L);Users payee=Users.builder().email("owner@example.test").build();payee.setActive(true);when(users.findById(22L)).thenReturn(java.util.Optional.of(payee));service.setInvoiceToPaid(invoice,"PAY-6",40);verify(notifications).queueEmailAndInAppOnce(eq("invoice-payment:10:PAY-6"),eq("owner@example.test"),eq(NotificationType.BUSINESS_ALERT_EMAIL),contains("PAY-6"),eq("PARTIAL_PAYMENT_RECEIVED"),contains("Outstanding balance: KES 60.00"),eq("/dashboard/payments"));}
    private PMSInvoice invoice(double pending){PMSInvoice invoice=new PMSInvoice();invoice.setId(10L);invoice.setRef("INV-A");invoice.setCurrency("KES");invoice.setAmount(100);invoice.setPendingAmount(pending);invoice.setCustomerPhoneNumber("+254700000000");invoice.setActive(true);return invoice;}
}
