package org.pms.silverocean.service.leasefinance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.LateFeeRuleRepo;
import org.pms.silverocean.database.pms.LeaseFinancialEventRepo;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.lease.LeaseDao;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.pms.silverocean.service.payment.ledger.FinancialLedgerService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaseFinanceServiceTest {
    @Mock LeaseFinancialEventRepo events;
    @Mock LateFeeRuleRepo rules;
    @Mock LeaseDao leases;
    @Mock InvoiceDao invoices;
    @Mock FinancialLedgerService ledger;
    @Mock UserDao users;
    LeaseFinanceService service;
    Lease lease;

    @BeforeEach void setup() {
        service = new LeaseFinanceService(events, rules, leases, invoices, ledger, users);
        lease = new Lease(); lease.setId(5L); lease.setTenantId(6); lease.setCurrency("KES");
        when(users.getUserId()).thenReturn(1L);
        when(leases.getLeaseByIdAndStaffOwner(5L, 1L)).thenReturn(Optional.of(lease));
    }

    @Test void depositCannotBeRefundedBeyondHeldBalance() {
        when(events.findByIdempotencyKey("refund-1")).thenReturn(Optional.empty());
        when(events.total(5L, "DEPOSIT_RECEIVED")).thenReturn(new BigDecimal("100"));
        when(events.total(5L, "DEPOSIT_DEDUCTION")).thenReturn(BigDecimal.ZERO);
        when(events.total(5L, "DEPOSIT_REFUND")).thenReturn(BigDecimal.ZERO);
        var request = new LeaseFinanceModels.Create("refund-1", 5L, null, LeaseFinanceModels.Type.DEPOSIT_REFUND,
                new BigDecimal("100.01"), "RF-1", "Move-out refund");
        assertThrows(PMSCustomException.class, () -> service.create(request));
        verifyNoInteractions(ledger);
    }

    @Test void lateFeeUsesRuleAndHonoursCap() {
        LateFeeRule rule = new LateFeeRule(); rule.setLeaseId(5); rule.setFlatAmount(new BigDecimal("50"));
        rule.setPercentageRate(new BigDecimal("20")); rule.setGraceDays(3); rule.setMaximumAmount(new BigDecimal("100"));
        rule.setEnabled(true); rule.setActive(true);
        when(rules.findByLeaseIdAndActiveTrue(5L)).thenReturn(Optional.of(rule));
        PMSInvoice invoice = new PMSInvoice(); invoice.setId(9L); invoice.setUnitId(7); invoice.setBilledUserId(8);
        invoice.setPayToUserId(2); invoice.setPendingAmount(1000); invoice.setDueDate(LocalDate.now().minusDays(5));
        when(invoices.getInvoiceByIdForUpdate(9L)).thenReturn(Optional.of(invoice));
        UnitTenant tenancy = new UnitTenant(); tenancy.setUserId(8);
        Unit unit = new Unit(); unit.setId(7L); unit.setCreatedBy(2L); unit.setPropertyId(4L); unit.setCurrency("KES");
        when(leases.getUnitTenantByTenantId(6L)).thenReturn(Optional.of(tenancy));
        when(leases.getUnitByTenantId(6L)).thenReturn(Optional.of(unit));
        when(events.findByIdempotencyKey("late-1")).thenReturn(Optional.empty());
        when(events.save(any())).thenAnswer(i -> i.getArgument(0));
        LeaseFinancialEvent result = service.assess(new LeaseFinanceModels.Assess("late-1", 5L, 9L));
        assertEquals(new BigDecimal("100.00"), result.getAmount());
        assertEquals("LATE_FEE_CHARGED", result.getEventType());
        verify(ledger).recordLeaseFinancialEvent(lease, tenancy, unit, result);
    }
}
