package org.pms.silverocean.service.dashboard.wrappers;

public record PropertyManagerDto(int totalManagedUnits, int totalManagedProperties, int totalOccupiedUnits, int totalPendingLeaseSigns) implements ReportDto {
}
