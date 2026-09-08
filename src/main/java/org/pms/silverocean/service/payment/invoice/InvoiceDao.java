package org.pms.silverocean.service.payment.invoice;

import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.PMSInvoiceRepo;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.ledger.FinancialLedgerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

import static org.pms.silverocean.service.payment.invoice.InvoiceSpecifications.searchParticipantInvoices;
import static org.pms.silverocean.service.payment.invoice.InvoiceSpecifications.searchPlatformInvoices;

@Service @Slf4j
public class InvoiceDao {
    private final PMSInvoiceRepo pmsInvoiceRepo;
    private final FinancialLedgerService financialLedgerService;

    public InvoiceDao(PMSInvoiceRepo pmsInvoiceRepo, FinancialLedgerService financialLedgerService) {
        this.pmsInvoiceRepo = pmsInvoiceRepo;
        this.financialLedgerService = financialLedgerService;
    }

    public void saveInvoice(PMSInvoice pmsInvoice) {
        pmsInvoiceRepo.save(pmsInvoice);
    }

    @Transactional
    public void createInvoice(PMSInvoice pmsInvoice) {
        if (pmsInvoice.getId() != null) {
            throw new PMSCustomException(ResponseCode.GENERAL_FAILURE);
        }
        pmsInvoice = pmsInvoiceRepo.save(pmsInvoice);
        String ref = "INV-" + Long.toHexString(pmsInvoice.getId()).toUpperCase();
        log.info("New Invoice ID {} ref {} ", pmsInvoice.getId(), pmsInvoice.getRef());
        pmsInvoiceRepo.updateInvoiceRef(pmsInvoice.getId(), ref);
        pmsInvoice.setRef(ref);
        financialLedgerService.recordInvoiceIssued(pmsInvoice);
    }

    public Optional<PMSInvoice> getInvoiceByRef(String ref) {
        return pmsInvoiceRepo.findByRef(ref);
    }

    public Optional<PMSInvoice> getInvoiceByRefForOwnerOrPropertyManager(String ref, long ownerOrManagerId) {
        return pmsInvoiceRepo.findByRefAndPayToUserIdAndActiveTrue(ref, ownerOrManagerId);

    }

    public Optional<PMSInvoice> getInvoiceById(long invoiceId) {
        return pmsInvoiceRepo.findById(invoiceId);
    }

    public Optional<PMSInvoice> getInvoiceByIdForUpdate(long invoiceId) {
        return pmsInvoiceRepo.findByIdForUpdate(invoiceId);
    }

    public Set<PMSPayment> loadPendingVerifyTransactionsFromDb(PaymentChannel channel, String status) {
        return pmsInvoiceRepo.findByTransactionInProgressTrue(channel.getName(), status);
    }

    public Page<PMSInvoice> getPlatformInvoices(Pageable pageable, Long billedUserId) {
        return getPlatformInvoices(pageable, billedUserId, null);
    }

    public Page<PMSInvoice> getPlatformInvoices(Pageable pageable, Long billedUserId, Long invoiceId) {
        return pmsInvoiceRepo.findAll(searchPlatformInvoices(billedUserId, invoiceId), pageable);
    }

    public Page<PMSInvoice> getInvoicesForOwnerAndTenantView(Pageable pageable, long userId, Long propertyId, Long unitId) {
        return getInvoicesForOwnerAndTenantView(pageable, userId, propertyId, unitId, null);
    }

    public Page<PMSInvoice> getInvoicesForOwnerAndTenantView(Pageable pageable, long userId, Long propertyId, Long unitId, Long invoiceId) {
        return pmsInvoiceRepo.findAll(searchParticipantInvoices(userId, propertyId, unitId, invoiceId), pageable);
    }

    public Optional<PMSInvoice> getInvoiceForOwnerOrTenantView(long invoiceId, long userId) {
        return pmsInvoiceRepo.findInvoiceForOwnerOrTenant(invoiceId, userId);
    }

    public Optional<PMSInvoice> getInvoiceForOwnerOrTenantView(String invoiceRef, long userId) {
        return pmsInvoiceRepo.findInvoiceForOwnerOrTenantByRef(invoiceRef, userId);
    }

    public Optional<PMSInvoice> getPlatformInvoice(long invoiceId) {
        return pmsInvoiceRepo.findPlatformInvoice(invoiceId);
    }

    public Optional<PMSInvoice> getPlatformInvoice(String invoiceRef) {
        return pmsInvoiceRepo.findPlatformInvoiceByRef(invoiceRef);
    }
}
