package org.pms.silverocean.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessDateValidatorTest {
    private static final Clock NAIROBI_AFTER_MIDNIGHT = Clock.fixed(
            Instant.parse("2026-09-19T21:30:00Z"), ZoneOffset.UTC);

    record PastRequest(@BusinessDate LocalDate date) {}
    record FutureRequest(@BusinessDate(direction=BusinessDate.Direction.FUTURE_OR_PRESENT) LocalDate date) {}

    private Validator validator() {
        return Validation.byDefaultProvider().configure()
                .clockProvider(() -> NAIROBI_AFTER_MIDNIGHT)
                .buildValidatorFactory()
                .getValidator();
    }

    @Test void acceptsTheCurrentNairobiDateWhenUtcIsStillPreviousDay() {
        assertThat(validator().validate(new PastRequest(LocalDate.of(2026,9,20)))).isEmpty();
        assertThat(validator().validate(new FutureRequest(LocalDate.of(2026,9,20)))).isEmpty();
    }

    @Test void rejectsDatesOutsideTheBusinessDateDirection() {
        assertThat(validator().validate(new PastRequest(LocalDate.of(2026,9,21)))).hasSize(1);
        assertThat(validator().validate(new FutureRequest(LocalDate.of(2026,9,19)))).hasSize(1);
    }

    @Test void leavesNullHandlingToNotNullWhenTheFieldRequiresIt() {
        assertThat(validator().validate(new PastRequest(null))).isEmpty();
    }
}
