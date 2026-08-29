package org.pms.silverocean.service.dashboard.wrappers;

public record GuardDto(int totalInsideProperty, int totalDeliveryToday, int totalContractorsToday, int totalGuestsToday) implements ReportDto {
}
