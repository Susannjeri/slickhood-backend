package org.pms.silverocean.service.estate;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.common.PMSUtils;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;

class ServiceChargeViewTest {
    @Test void overdueBoundaryUsesNairobiBusinessDateNotUtcDatabaseDate() {
        Instant instant = Instant.parse("2026-09-06T21:01:00Z");
        LocalDate due = LocalDate.of(2026, 9, 6);
        assertEquals(due, instant.atZone(ZoneOffset.UTC).toLocalDate());
        LocalDate today = instant.atZone(PMSUtils.getZoneId()).toLocalDate();
        assertEquals("OVERDUE", ServiceChargeView.statusOn(false, due, today));
        assertEquals("DUE", ServiceChargeView.statusOn(false, today, today));
        assertEquals("PAID", ServiceChargeView.statusOn(true, due, today));
    }

    @Test void staleDatabaseStatusIsNotShownToHomeowner() {
        LocalDate today = LocalDate.now(PMSUtils.getZoneId());
        ServiceChargeView view = new ServiceChargeView(1,2,"Estate",3,"A1",4,5,"INV-1",
                java.math.BigDecimal.TEN,"KES",today.minusDays(1),"Service charge",false,10,"DUE",null);
        assertEquals("OVERDUE", view.status());
    }
}
