package org.pms.silverocean.service.dashboard.wrappers;

public record TenantDto(int totalOccupiedUnits, int totalPendingLeases, int totalUnpaidInvoices, int totalPaidInvoices) implements ReportDto {
}
