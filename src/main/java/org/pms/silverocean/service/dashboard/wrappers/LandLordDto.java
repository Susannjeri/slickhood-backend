package org.pms.silverocean.service.dashboard.wrappers;

import java.math.BigDecimal;

public record LandLordDto(int totalProperties, int totalUnits, int activeTenants, BigDecimal monthlyRevenue) implements ReportDto {
}
