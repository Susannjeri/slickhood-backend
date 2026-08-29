package org.pms.silverocean.service.dashboard.wrappers;

public record BusinessRoleDashboardDto(String role, long primaryCount, long secondaryCount,
                                       long pendingActions, long completedCount,
                                       String primaryLabel, String secondaryLabel,
                                       String pendingLabel, String completedLabel) implements ReportDto {}
