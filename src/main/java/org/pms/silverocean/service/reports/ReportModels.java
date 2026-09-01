package org.pms.silverocean.service.reports;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public final class ReportModels {
    private ReportModels() {}

    public record Definition(
            String code,
            String title,
            String description,
            String category,
            boolean supportsDateRange,
            String dateMode,
            List<String> availableToRoles
    ) {}

    public record Data(
            Definition definition,
            LocalDate from,
            LocalDate to,
            ZonedDateTime generatedAt,
            Map<String, Object> metrics,
            List<String> columns,
            List<Map<String, Object>> rows,
            boolean truncated,
            int rowLimit
    ) {}

    public record CsvExport(byte[] content, boolean truncated, int rowLimit) {}
}
