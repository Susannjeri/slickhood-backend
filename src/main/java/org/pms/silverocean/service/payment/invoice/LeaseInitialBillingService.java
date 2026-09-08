package org.pms.silverocean.service.payment.invoice;

import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.Lease;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.UnitTenant;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.lease.LeaseDao;
import org.pms.silverocean.service.property.charges.PMSPeriod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Freezes and issues the first rental obligation when the second party signs.
 * The invoice is created in the same transaction as lease activation so an
 * occupied unit can never be committed without its initial bill.
 */
@Service
public class LeaseInitialBillingService {
    private final LeaseDao leases;
    private final InvoiceService invoices;

    public LeaseInitialBillingService(LeaseDao leases, InvoiceService invoices) {
        this.leases = leases;
        this.invoices = invoices;
    }

    @Transactional
    public void issue(Lease lease, UnitTenant tenancy, Unit unit) {
        if (lease == null || tenancy == null || unit == null || !lease.isSigned()
                || !"RENT".equals(lease.getLeaseMode()) || lease.getMoveInDate() == null
                || lease.getPrice() <= 0 || tenancy.getUserId() <= 0 || unit.getId() == null) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA_CONSTRAINT);
        }

        LocalDate initialDueDate = lease.getMoveInDate();
        Map<String, Double> amounts = new LinkedHashMap<>();
        amounts.put("First month's rent", lease.getPrice());

        leases.getLeaseChargeByLeaseId(lease.getId()).forEach(charge -> {
            if (charge.getAmount() <= 0 || charge.getPeriodId() == null) return;
            amounts.put(charge.getChargeName() + " amount", charge.getAmount());
            PMSPeriod period = PMSPeriod.valueOf(charge.getPeriodId());
            if (period == PMSPeriod.MONTHLY) {
                leases.updateLeaseChargeNextPaymentDate(charge.getId(), initialDueDate.plusMonths(1));
            } else if (period == PMSPeriod.ANNUAL) {
                leases.updateLeaseChargeNextPaymentDate(charge.getId(), initialDueDate.plusYears(1));
            }
        });

        invoices.createPropertyInvoice(unit.getId(), tenancy.getUserId(), amounts, "RENTAL", initialDueDate);
        lease.setNextPaymentDate(nextRecurringRentDate(lease, initialDueDate));
        lease.setPaymentDue(true);
    }

    static LocalDate nextRecurringRentDate(Lease lease, LocalDate initialDueDate) {
        LocalDate nextMonth = initialDueDate.plusMonths(1);
        int requestedDay = Optional.ofNullable(lease.getRentDueDayOfMonth())
                .orElse(initialDueDate.getDayOfMonth());
        return nextMonth.withDayOfMonth(Math.min(requestedDay, nextMonth.lengthOfMonth()));
    }
}
