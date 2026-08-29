package org.pms.silverocean.service.subscription;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.subscription.enums.BillingCycle;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SubscriptionTermsTest {

    private final ZonedDateTime start = ZonedDateTime.of(
            2026, 1, 31, 10, 15, 0, 0, ZoneId.of("Africa/Nairobi"));

    @Test
    void calculatesEachFixedBillingTermFromTheSuppliedStart() {
        assertEquals(start.plusMonths(1), SubscriptionTerms.endAt(BillingCycle.MONTHLY, start));
        assertEquals(start.plusMonths(3), SubscriptionTerms.endAt(BillingCycle.QUARTERLY, start));
        assertEquals(start.plusYears(1), SubscriptionTerms.endAt(BillingCycle.YEARLY, start));
    }

    @Test
    void lifetimeAndLegacyMissingCyclesHaveNoExpiry() {
        assertNull(SubscriptionTerms.endAt(BillingCycle.LIFETIME, start));
        assertNull(SubscriptionTerms.endAt(null, start));
    }
}
