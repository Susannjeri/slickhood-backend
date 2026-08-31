package org.pms.silverocean.service.lease.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeaseTemplateDTO(Long id, @NotBlank @Size(max = 120) String name, @NotNull PMSLeaseMode leaseMode,
                               boolean selfRenew, @NotNull @Min(1) @Max(1200) Integer leaseDurationInMonths,
                               @NotNull @Min(0) @Max(120) Integer noticePeriodInMonths,
                               @NotNull @Min(0) @Max(365) Integer depositReturnDays,
                               @NotNull @Min(1) @Max(28) Integer rentDueDayOfMonth,
                               @NotNull @Min(0) @Max(90) Integer entryNoticeDays,
                               @NotNull @PositiveOrZero Double repairThreshold,
                               @NotBlank @Size(max = 10000) String petsPolicy) {
}
