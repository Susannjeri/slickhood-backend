package org.pms.silverocean.service.payment.invoice;

import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.lease.LeaseDao;
import org.pms.silverocean.service.lease.wrappers.PMSLeaseMode;
import org.pms.silverocean.service.payment.invoice.wrappers.ProcessLeaseInvoiceDTO;
import org.pms.silverocean.service.property.charges.PMSPeriod;
import org.pms.silverocean.service.property.wrappers.UnitChargeProjection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class CreateInvoiceService {
    private static final String AMOUNT = " Amount";
    private final LeaseDao leaseDao;
    private final ConfigService configService;
    private final InvoiceService invoiceService;

    public CreateInvoiceService(LeaseDao leaseDao, ConfigService configService, InvoiceService invoiceService) {
        this.leaseDao = leaseDao;
        this.configService = configService;
        this.invoiceService = invoiceService;
    }

    @Transactional
    public void processPendingInvoices() {
        log.info("Running Invoice Creation Service");
        Slice<ProcessLeaseInvoiceDTO> leaseSlice = leaseDao.getLeasePaymentsDueToday(PageRequest.of(0, configService.getConfigByName(PMSConfigs.LEASE_PAYMENT_RECORDS_PAGE_SIZE).get().intValue()));
        while (leaseSlice.hasContent()) {
            log.info("Processing Invoices for {} leases.", leaseSlice.getNumber());
            // Process the current batch
            processPendingInvoices(leaseSlice.getContent());
            if (leaseSlice.hasNext()) {
                leaseSlice = leaseDao.getLeasePaymentsDueToday(leaseSlice.nextPageable());
            } else {
                log.info("Done processing lease invoices");
                break;
            }
        }
        log.info("Invoice Creation Service going to sleep");
    }


    private void processPendingInvoices(List<ProcessLeaseInvoiceDTO> leasePaymentsDueToday) {
        Set<Long> idsToIncrement = new HashSet<>();
        Set<Long> idsToDeactivate = new HashSet<>();
        for (ProcessLeaseInvoiceDTO lease : leasePaymentsDueToday) {
            String leaseMode = lease.leaseMode(); // "RENT" or "SALE"
            boolean isFirstPayment = lease.leaseDate().isEqual(lease.nextPaymentDate());
            Map<String, Double> invoiceAmounts = new LinkedHashMap<>();
            if (isFirstPayment && PMSLeaseMode.SALE.name().equals(leaseMode)) {
                invoiceAmounts.put(leaseMode, lease.price());
            } else {
                invoiceAmounts.put(leaseMode, lease.price());
                idsToIncrement.add(lease.id());
            }
            if (lease.isCharges()) {
                populateCharges(invoiceAmounts, lease.id(), isFirstPayment);
            } else if (PMSLeaseMode.SALE.name().equals(leaseMode)) {
                idsToDeactivate.add(lease.id());
            }

            if (!invoiceAmounts.isEmpty()) {
                invoiceService.createInvoice(lease.unitId(), lease.tenantUserId(), invoiceAmounts);
            }
        }
        if (!idsToIncrement.isEmpty()) {
            leaseDao.incrementNextPaymentDate(idsToIncrement);
        }
        if (!idsToDeactivate.isEmpty()) {
            leaseDao.deactivatePaymentDue(idsToDeactivate);
        }
        leaseDao.clearEntityManagerPaginationCache();
    }


    private void populateCharges(Map<String, Double> invoiceAmounts, long leaseId, boolean isFirstPayment) {
        List<UnitChargeProjection> charges = leaseDao.getLeaseChargeByLeaseId(leaseId);

        LocalDate today = LocalDate.now();
        for (UnitChargeProjection charge : charges) {
            PMSPeriod period = PMSPeriod.valueOf(charge.getPeriodId());
            if (isFirstPayment) {
                invoiceAmounts.put(charge.getChargeName() + AMOUNT, charge.getAmount());
                updateChargeNextPayment(charge.getId(), charge.getNextPaymentDate(), period);
            } else {
                if (!today.isBefore(charge.getNextPaymentDate())) {
                    switch (period) {
                        case PMSPeriod.MONTHLY, PMSPeriod.ANNUAL -> {
                            invoiceAmounts.put(charge.getChargeName() + AMOUNT, charge.getAmount());
                            updateChargeNextPayment(charge.getId(), charge.getNextPaymentDate(), period);
                        }
                    }
                }
            }

        }
    }

    private void updateChargeNextPayment(long leaseChargeId, LocalDate currentPaymentDate, PMSPeriod period) {
        LocalDate updatedDate;
        if (PMSPeriod.ONE_TIME.equals(period)) {
            log.info("Skipping date update for ONE_TIME lease charge ID: {}", leaseChargeId);
            return; // Exit method immediately
        }
        // 2. Increment based on the defined period
        switch (period) {
            case MONTHLY -> updatedDate = currentPaymentDate.plusMonths(1);
            case ANNUAL -> updatedDate = currentPaymentDate.plusYears(1);
            default -> throw new PMSCustomException(ResponseCode.GENERAL_FAILURE);
        }

        // 3. Save the updated nextPaymentDate back to the database
        leaseDao.updateLeaseChargeNextPaymentDate(leaseChargeId, updatedDate);
    }
}
