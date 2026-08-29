package org.pms.silverocean.service.lease.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeaseTemplateDTO(Long id, String name, PMSLeaseMode leaseMode, boolean selfRenew, Integer leaseDurationInMonths,
                               Integer noticePeriodInMonths, Integer depositReturnDays, Integer rentDueDayOfMonth, Integer entryNoticeDays,
                               Double repairThreshold,
                               String petsPolicy) {
}
