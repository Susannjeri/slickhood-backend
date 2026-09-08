package org.pms.silverocean.service.payment.invoice;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.Lease;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.UnitTenant;
import org.pms.silverocean.service.lease.LeaseDao;
import org.pms.silverocean.service.property.wrappers.UnitChargeProjection;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LeaseInitialBillingServiceTest {
    @Test
    void finalSignatureIssuesRentAndDepositDueOnMoveInAndSchedulesRecurringRent() {
        LeaseDao leases = mock(LeaseDao.class);
        InvoiceService invoices = mock(InvoiceService.class);
        Lease lease = lease();
        UnitTenant tenancy = new UnitTenant(); tenancy.setUserId(4L);
        Unit unit = new Unit(); unit.setId(3L);
        UnitChargeProjection deposit = charge(8, "DEPOSIT", 50000, "ONE_TIME");
        UnitChargeProjection service = charge(9, "SERVICE", 2500, "MONTHLY");
        when(leases.getLeaseChargeByLeaseId(1L)).thenReturn(List.of(deposit, service));

        new LeaseInitialBillingService(leases, invoices).issue(lease, tenancy, unit);

        verify(invoices).createPropertyInvoice(eq(3L), eq(4L), argThat(amounts ->
                amounts.get("First month's rent") == 50000d
                        && amounts.get("DEPOSIT amount") == 50000d
                        && amounts.get("SERVICE amount") == 2500d),
                eq("RENTAL"), eq(LocalDate.of(2026, 10, 15)));
        verify(leases).updateLeaseChargeNextPaymentDate(9L, LocalDate.of(2026, 11, 15));
        verify(leases, never()).updateLeaseChargeNextPaymentDate(eq(8L), any());
        assertEquals(LocalDate.of(2026, 11, 5), lease.getNextPaymentDate());
        assertTrue(lease.isPaymentDue());
    }

    private Lease lease() {
        Lease lease = new Lease();
        lease.setId(1L); lease.setLeaseMode("RENT"); lease.setSigned(true);
        lease.setPrice(50000); lease.setMoveInDate(LocalDate.of(2026, 10, 15));
        lease.setRentDueDayOfMonth(5);
        return lease;
    }

    private UnitChargeProjection charge(long id, String name, double amount, String period) {
        UnitChargeProjection charge = mock(UnitChargeProjection.class);
        when(charge.getId()).thenReturn(id);
        when(charge.getChargeName()).thenReturn(name);
        when(charge.getAmount()).thenReturn(amount);
        when(charge.getPeriodId()).thenReturn(period);
        return charge;
    }
}
