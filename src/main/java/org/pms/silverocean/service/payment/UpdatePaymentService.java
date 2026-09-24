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
import org.pms.silverocean.service.payment.money.MonetaryPolicy;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.database.pms.entities.Users;

@Component
public class UpdatePaymentService {
    private final NotificationService notificationService;
    private final InvoiceDao invoiceDao;
    private final I18NService i18NService;
    private final DomainEventOutboxPublisher eventPublisher;
    private final FinancialLedgerService financialLedgerService;
    private final UserDao userDao;

    public UpdatePaymentService(
            NotificationService notificationService,
            InvoiceDao invoiceDao,
            I18NService i18NService,
            DomainEventOutboxPublisher eventPublisher,
            FinancialLedgerService financialLedgerService,
            UserDao userDao
    ) {
        this.notificationService = notificationService;
        this.invoiceDao = invoiceDao;
        this.i18NService = i18NService;
        this.eventPublisher = eventPublisher;
        this.financialLedgerService = financialLedgerService;
        this.userDao = userDao;
    }

    @Transactional
    public void setInvoiceToPaid(PMSInvoice invoice, String thirdPartyId, double transAMount) {
        setInvoiceToPaid(invoice, thirdPartyId, BigDecimal.valueOf(transAMount));
    }

    @Transactional
    public void setInvoiceToPaid(PMSInvoice invoice, String thirdPartyId, BigDecimal transactionAmount) {
        if(invoice.getId()!=null)invoice=invoiceDao.getInvoiceByIdForUpdate(invoice.getId()).orElse(invoice);
        MonetaryPolicy.currency(invoice.getCurrency());
        boolean alreadyFullyPaid = invoice.isPaid() && invoice.moneyPendingAmount().signum() <= 0;
        if (alreadyFullyPaid) {
            publishInvoicePaid(invoice,thirdPartyId,invoice.moneyAmount());
            return;
        }

        BigDecimal requestedAmount = MonetaryPolicy.positive(transactionAmount);
        BigDecimal pendingAmount = invoice.moneyPendingAmount();
        if (requestedAmount.signum() <= 0) throw new IllegalArgumentException("Payment amount must be positive");
        BigDecimal appliedAmount = requestedAmount.min(pendingAmount);
        if (!financialLedgerService.recordPaymentApplied(invoice, thirdPartyId, appliedAmount)) return;
        BigDecimal excess = requestedAmount.subtract(appliedAmount);
        if (excess.signum() > 0) financialLedgerService.recordUnappliedCredit(invoice, thirdPartyId, excess);
        invoice.setMoneyPendingAmount(pendingAmount.subtract(appliedAmount));
        invoice.setTransactionInProgress(false);
        String formattedPaymentMessage = String.format(i18NService.getLocalizedMessage(NotificationType.PAYMENT_SUCCESS_SMS.getBody()),
                thirdPartyId, invoice.getCurrency(), appliedAmount.doubleValue(), invoice.getRef(), LocalDateTime.now());
        notificationService.sendNotification(new NotificationDTO(formattedPaymentMessage,
                invoice.getCustomerPhoneNumber(), NotificationType.PAYMENT_SUCCESS_SMS));
        boolean nowFullyPaid = invoice.moneyPendingAmount().signum() <= 0;
        if (nowFullyPaid) {
            invoice.setPaid(true);
        }
        invoiceDao.saveInvoice(invoice);

        if (invoice.getCustomerEmail() != null && !invoice.getCustomerEmail().isBlank()) {
            BigDecimal balance = invoice.moneyPendingAmount();
            String receiptBody = String.format(i18NService.getLocalizedMessage(NotificationType.PAYMENT_RECEIPT_EMAIL.getBody()),
                    thirdPartyId, invoice.getCurrency(), appliedAmount.doubleValue(), invoice.getRef(),
                    balance.doubleValue(), invoice.getCurrency());
            notificationService.queueEmailAndInApp(invoice.getCustomerEmail(), NotificationType.PAYMENT_RECEIPT_EMAIL,
                    receiptBody, nowFullyPaid ? "INVOICE_PAID" : "PARTIAL_PAYMENT_RECEIVED",
                    "Payment " + thirdPartyId + " of " + invoice.getCurrency() + " " + appliedAmount.toPlainString()
                            + " was applied to invoice " + invoice.getRef() + ". Outstanding balance: "
                            + invoice.getCurrency() + " " + balance.toPlainString() + ". Open /dashboard/invoices to view the receipt.");
        }

        if (invoice.getPayToUserId() > 0) {
            PMSInvoice paidInvoice = invoice;
            userDao.findById(paidInvoice.getPayToUserId()).map(Users::getEmail)
                    .filter(email -> email != null && !email.isBlank())
                    .filter(email -> paidInvoice.getCustomerEmail() == null
                            || !email.equalsIgnoreCase(paidInvoice.getCustomerEmail()))
                    .ifPresent(email -> notificationService.queueEmailAndInAppOnce(
                            "invoice-payment:" + paidInvoice.getId() + ":" + thirdPartyId,
                            email, NotificationType.BUSINESS_ALERT_EMAIL,
                            "<p>Payment " + org.springframework.web.util.HtmlUtils.htmlEscape(thirdPartyId)
                                    + " of " + paidInvoice.getCurrency() + " " + appliedAmount.toPlainString()
                                    + " was received for invoice "
                                    + org.springframework.web.util.HtmlUtils.htmlEscape(paidInvoice.getRef()) + ".</p>",
                            nowFullyPaid ? "PAYMENT_RECEIVED" : "PARTIAL_PAYMENT_RECEIVED",
                            "Payment " + thirdPartyId + " of " + paidInvoice.getCurrency() + " "
                                    + appliedAmount.toPlainString() + " was received for invoice " + paidInvoice.getRef()
                                    + ". Outstanding balance: " + paidInvoice.getCurrency() + " "
                                    + paidInvoice.moneyPendingAmount().toPlainString() + ".",
                            "/dashboard/payments"));
        }

        if(nowFullyPaid)publishInvoicePaid(invoice,thirdPartyId,invoice.moneyAmount());
    }

    @Transactional
    public void setInvoiceToPaid(String billRefNumber, String thirdPartyId, double transAMount) {
        getInvoicePayToIDUsingInvoiceRef(billRefNumber).ifPresent(invoice -> setInvoiceToPaid(invoice, thirdPartyId, transAMount));
    }

    @Transactional
    public void setInvoiceToPaid(String billRefNumber, String thirdPartyId, BigDecimal transactionAmount) {
        getInvoicePayToIDUsingInvoiceRef(billRefNumber).ifPresent(invoice -> setInvoiceToPaid(invoice, thirdPartyId, transactionAmount));
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

    private void publishInvoicePaid(PMSInvoice invoice,String providerReference,BigDecimal paidAmount){
        if(invoice.getId()==null)return;InvoicePaidEvent event=new InvoicePaidEvent(invoice.getId(),invoice.getRef(),providerReference,
                MonetaryPolicy.amount(paidAmount),invoice.getCurrency(),LocalDateTime.now());
        eventPublisher.publish(InvoicePaidEvent.TYPE,"INVOICE",Long.toString(invoice.getId()),event.dedupeKey(),event);
    }
}
