package org.pms.silverocean.service.dashboard.wrappers;

import java.math.BigDecimal;

public record SuperAdminReportDto(double inActiveUserPercent, int userLoggedInWithinCurrentMonth, int totalActiveProperties, BigDecimal totalSubscriptionPaidWithinCurrentMonth) implements ReportDto {
}
