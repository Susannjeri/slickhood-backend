package org.pms.silverocean.service.payment.invoice;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.database.pms.PMSInvoiceRepo;
import org.pms.silverocean.service.payment.invoice.wrappers.AmountCurrencyProjection;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InvoiceReportDao {
    private final PMSInvoiceRepo pmsInvoiceRepo;


    public Set<AmountCurrencyProjection> getTotalPaidSubscriptionsWithinCurrentMonth() {
        YearMonth now = YearMonth.now(PMSUtils.getZoneId());
        ZonedDateTime start = now.atDay(1).atStartOfDay(PMSUtils.getZoneId());
        return pmsInvoiceRepo.getSumOfPaidInvoicesUsingTypeAndDateRange(start, ZonedDateTime.now(PMSUtils.getZoneId()), "SUBSCRIPTION");//TODO create an abstraction of invoice types
    }
}
