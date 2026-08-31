package org.pms.silverocean.service.payment;

import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.pms.silverocean.service.payment.platforms.flutterwave.FWVerifyTransaction;
import org.pms.silverocean.service.payment.wrappers.ManualPaymentDTO;
import org.pms.silverocean.service.payment.wrappers.PaymentDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentDao paymentDao;
    private final InvoiceDao invoiceDao;
    private final FWVerifyTransaction verifyTransaction;
    private final UserDao userDao;
    private final UpdatePaymentService updatePaymentService;
    private final I18NService i18NService;

    public PaymentService(PaymentDao paymentDao, InvoiceDao invoiceDao, FWVerifyTransaction verifyTransaction, UserDao userDao, UpdatePaymentService updatePaymentService, I18NService i18NService) {
        this.paymentDao = paymentDao;
        this.invoiceDao = invoiceDao;
        this.verifyTransaction = verifyTransaction;
        this.userDao = userDao;
        this.updatePaymentService = updatePaymentService;
        this.i18NService = i18NService;
    }

    public Page<PaymentDTO> getPayments(Pageable pageable, String filter) {
        long userId = userDao.getUserId();
        Page<PMSPayment> pmsPayment;
        if (userDao.hasRole(PMSRole.SUPER_ADMIN)) {
            pmsPayment = paymentDao.findPMSPayment(pageable, filter);
        } else {
            pmsPayment = StringUtils.isBlank(filter) ? paymentDao.findPaymentByUser(pageable, userId) : paymentDao.findPaymentByInvoiceRefAndUser(pageable, userId, filter);
        }
        return pmsPayment
                .map(payment -> new PaymentDTO(payment, i18NService.getLocalizedMessage(payment.getStatusDesc())));
    }

    @Transactional
    public void recordManualPayment(ManualPaymentDTO manualPaymentDTO) {
        Users userObject = userDao.getUserObject();
        PMSInvoice invoice = invoiceDao.getInvoiceByRefForOwnerOrPropertyManager(manualPaymentDTO.invoiceRef(), userObject.getId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_INVOICE_NUMBER));
        if (invoice.isPaid()) {
            throw new PMSCustomException(ResponseCode.INVOICE_ALREADY_PAID);
        } else if (!invoice.isActive()) {
            throw new PMSCustomException(ResponseCode.INVALID_INVOICE_NUMBER);
        } else if (invoice.isTransactionInProgress()) {
            throw new PMSCustomException(ResponseCode.TRANSACTION_IN_PROGRESS);
        } else if (manualPaymentDTO.amount() <= 0 || manualPaymentDTO.amount() > invoice.getPendingAmount()) {
            throw new PMSCustomException(ResponseCode.INVALID_AMOUNT);
        }

        invoice.setTransactionInProgress(true);
        invoiceDao.saveInvoice(invoice);
        PMSPayment payment = new PMSPayment(manualPaymentDTO);
        payment.setPayToUserId(invoice.getPayToUserId());
        String rawMessage = String.format("%s : %s ", manualPaymentDTO.transactionDate(), userObject.getFullName());
        String finalMessage = rawMessage.length() > 255
                ? rawMessage.substring(0, 255)
                : rawMessage;
        payment.setStatusDesc(finalMessage);
        updatePaymentService.setInvoiceToPaid(invoice, manualPaymentDTO.transId(), manualPaymentDTO.amount());
        paymentDao.savePMSPayment(payment);
    }

    public void updateFlutterWavePayment(String status, long paymentId, String thirdPartyId) {
        paymentDao.findPaymentByIDAndUserId(paymentId, userDao.getUserId()).ifPresent(p -> {
            p.setStatus(status);
            p.setStatusDesc("Redirect Received");
            if ("successful".equals(status)) {
                p.setThirdPartyTransId(thirdPartyId);
                verifyTransaction.scheduleVerifyFlutterWaveTransaction(paymentId, p.getVerificationRetries());
            } else {
                updatePaymentService.setInvoiceTransactionStatusByBillRefNumber(p.getBillReference(), false);
            }
            paymentDao.savePMSPayment(p);
        });
    }

}
