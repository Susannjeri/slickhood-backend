package org.pms.silverocean.service.dashboard.wrappers;

public record ServiceProviderReportDto(int bookingsWithinCurrentMonth,
                                       int bookingsLastMonth,
                                       double averageRating,
                                       int mostRecentRating) implements ReportDto {
}
