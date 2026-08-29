package org.pms.silverocean.service.payment;

import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.database.pms.PMSPaymentRepo;
import org.pms.silverocean.service.payment.platforms.mpesa.TransactionCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.pms.silverocean.service.payment.PaymentSpecification.searchPayment;

@Service
public class PaymentDao {
    private final PMSPaymentRepo pmsPaymentRepo;

    public PaymentDao(PMSPaymentRepo pmsPaymentRepo) {
        this.pmsPaymentRepo = pmsPaymentRepo;
    }

    public Optional<PMSPayment> findPaymentByThirdPartyID(String thirdPartyTransId) {
        return pmsPaymentRepo.findByThirdPartyTransId(thirdPartyTransId);
    }

    public boolean callbackAlreadyProcessed(String thirdPartyTransId, String billReference,
                                            String category, String successStatus) {
        return pmsPaymentRepo.existsByThirdPartyTransIdAndBillReferenceAndCategoryAndStatus(
                thirdPartyTransId, billReference, category, successStatus);
    }

    public Optional<PMSPayment> findPaymentByID(long paymentId) {
        return pmsPaymentRepo.findById(paymentId);
    }

    public Optional<PMSPayment> findPaymentByIDAndUserId(long paymentId, long userId) {
        return pmsPaymentRepo.findByIdAndUserId(paymentId, userId);
    }

    public Optional<PMSPayment> findPaymentByIdForAuthorizedUser(long paymentId, long userId) {
        return pmsPaymentRepo.findByIdForAuthorizedUser(paymentId, userId);
    }

    public void savePMSPayment(PMSPayment pmsPayment) {
        if (pmsPayment != null && pmsPayment.getId() != null) {
            pmsPayment.setUpdatedOn(LocalDateTime.now());
        }
        assert pmsPayment != null;
        pmsPaymentRepo.save(pmsPayment);
    }

    public Page<PMSPayment> findPMSPayment(Pageable pageable, String filter) {
        return pmsPaymentRepo.findAll(searchPayment(Optional.ofNullable(filter)), pageable);
    }

    public Page<PMSPayment> findPaymentByInvoiceRefAndUser(Pageable pageable, long userId, String invoiceRef) {
        return pmsPaymentRepo.findByInvoiceRefAndUser(pageable, invoiceRef, userId, TransactionCategory.PAYMENT_VALIDATION.name(), TransactionCategory.PAYMENT_VALIDATION.getSuccessString());
    }

    public Page<PMSPayment> findPaymentByUser(Pageable pageable, long userId) {
        return pmsPaymentRepo.findByUser(pageable, userId, TransactionCategory.PAYMENT_VALIDATION.name(), TransactionCategory.PAYMENT_VALIDATION.getSuccessString());
    }
}
