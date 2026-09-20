package org.pms.silverocean.config;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.pms.silverocean.common.PMSUtils;

import java.time.LocalDate;

public class BusinessDateValidator implements ConstraintValidator<BusinessDate, LocalDate> {
    private BusinessDate.Direction direction;

    @Override
    public void initialize(BusinessDate constraint) {
        direction = constraint.direction();
    }

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        LocalDate businessToday = context.getClockProvider().getClock().instant()
                .atZone(PMSUtils.getZoneId())
                .toLocalDate();
        return direction == BusinessDate.Direction.PAST_OR_PRESENT
                ? !value.isAfter(businessToday)
                : !value.isBefore(businessToday);
    }
}
