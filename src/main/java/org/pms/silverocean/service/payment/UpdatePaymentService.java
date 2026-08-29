package org.pms.silverocean.service.payment;

import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.pms.silverocean.service.architecture.events.DomainEventOutboxPublisher;
import org.pms.silverocean.service.payment.contract.InvoicePaidEvent;
import org.pms.silverocean.service.payment.ledger.FinancialLedgerService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class UpdatePaymentService {
    private final NotificationService notificationService;
    private final InvoiceDao invoiceDao;
    private final I18NService i18NService;
    private final DomainEventOutboxPublisher eventPublisher;
    private final FinancialLedgerService financialLedgerService;

    public UpdatePaymentService(
            NotificationService notificationService,
            InvoiceDao invoiceDao,
            I18NService i18NService,
            DomainEventOutboxPublisher eventPublisher,
            FinancialLedgerService financialLedgerService
    ) {
        this.notificationService = notificationService;
        this.invoiceDao = invoiceDao;
        this.i18NService = i18NService;
        this.eventPublisher = eventPublisher;
        this.financialLedgerService = financialLedgerService;
    }

    @Transactional
    public void setInvoiceToPaid(PMSInvoice invoice, String thirdPartyId, double transAMount) {
        if(invoice.getId()!=null)invoice=invoiceDao.getInvoiceByIdForUpdate(invoice.getId()).orElse(invoice);
        boolean alreadyFullyPaid = invoice.isPaid() && invoice.getPendingAmount() <= 0;
        if (alreadyFullyPaid) {
            publishInvoicePaid(invoice,thirdPartyId,invoice.getAmount());
            return;
        }

        BigDecimal requestedAmount = BigDecimal.valueOf(transAMount).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal pendingAmount = BigDecimal.valueOf(invoice.getPendingAmount()).setScale(2, java.math.RoundingMode.HALF_UP);
        if (requestedAmount.signum() <= 0) throw new IllegalArgumentException("Payment amount must be positive");
        BigDecimal appliedAmount = requestedAmount.min(pendingAmount);
        if (!financialLedgerService.recordPaymentApplied(invoice, thirdPartyId, appliedAmount)) return;
        BigDecimal excess = requestedAmount.subtract(appliedAmount);
        if (excess.signum() > 0) financialLedgerService.recordUnappliedCredit(invoice, thirdPartyId, excess);
        invoice.setPendingAmount(pendingAmount.subtract(appliedAmount).doubleValue());
        invoice.setTransactionInProgress(false);
        String formattedPaymentMessage = String.format(i18NService.getLocalizedMessage(NotificationType.PAYMENT_SUCCESS_SMS.getBody()),
                thirdPartyId, invoice.getCurrency(), appliedAmount.doubleValue(), invoice.getRef(), LocalDateTime.now());
        notificationService.sendNotification(new NotificationDTO(formattedPaymentMessage,
                invoice.getCustomerPhoneNumber(), NotificationType.PAYMENT_SUCCESS_SMS));
        boolean nowFullyPaid = invoice.getPendingAmount() <= 0;
        if (nowFullyPaid) {
            invoice.setPaid(true);
        }
        invoiceDao.saveInvoice(invoice);

        if(nowFullyPaid)publishInvoicePaid(invoice,thirdPartyId,invoice.getAmount());
    }

    @Transactional
    public void setInvoiceToPaid(String billRefNumber, String thirdPartyId, double transAMount) {
        getInvoicePayToIDUsingInvoiceRef(billRefNumber).ifPresent(invoice -> setInvoiceToPaid(invoice, thirdPartyId, transAMount));
    }

    public void setInvoiceTransactionStatusByBillRefNumber(String billRefNumber, boolean inProgress) {
        getInvoicePayToIDUsingInvoiceRef(billRefNumber).ifPresent(invoice -> {
            invoice.setTransactionInProgress(inProgress);
            updateInvoice(invoice);
        });
    }

    public Optional<PMSInvoice> getInvoicePayToIDUsingInvoiceRef(String billRefNumber) {
        return invoiceDao.getInvoiceByRef(billRefNumber);
    }

    public void sendInvalidAccountNotification(String phoneNumber) {
        notificationService.sendNotification(new NotificationDTO(i18NService.getLocalizedMessage(NotificationType.MPESA_VALIDATION_FAILED_SMS.getBody()),
                phoneNumber, NotificationType.MPESA_VALIDATION_FAILED_SMS));
    }

    @Async
    public void updateInvoice(PMSInvoice invoice) {
        invoiceDao.saveInvoice(invoice);
    }

    private void publishInvoicePaid(PMSInvoice invoice,String providerReference,double paidAmount){
        if(invoice.getId()==null)return;InvoicePaidEvent event=new InvoicePaidEvent(invoice.getId(),invoice.getRef(),providerReference,
                BigDecimal.valueOf(paidAmount),invoice.getCurrency(),LocalDateTime.now());
        eventPublisher.publish(InvoicePaidEvent.TYPE,"INVOICE",Long.toString(invoice.getId()),event.dedupeKey(),event);
    }
}
