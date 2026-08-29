package org.pms.silverocean.service.wealth;

import org.pms.silverocean.database.pms.entities.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class WealthModels {
    private WealthModels() {}
    public record AssetPerformance(long assetId,String name,String assetType,String currency,
            BigDecimal value,BigDecimal debt,BigDecimal equity,BigDecimal income,
            BigDecimal expenses,BigDecimal netOperatingIncome,BigDecimal rentalYieldPercent,
            BigDecimal appreciation,BigDecimal loanToValuePercent,BigDecimal concentrationPercent,
            int totalUnits,int occupiedUnits,BigDecimal occupancyPercent,BigDecimal arrears) {}
    public record PortfolioSummary(String currency,BigDecimal totalAssetValue,BigDecimal totalDebt,
            BigDecimal netWorth,BigDecimal annualIncome,BigDecimal annualExpenses,BigDecimal netOperatingIncome,
            BigDecimal cashFlow,BigDecimal equity,BigDecimal appreciation,BigDecimal portfolioYieldPercent,
            BigDecimal loanToValuePercent,BigDecimal occupancyPercent,BigDecimal arrears,
            int assetCount,int totalUnits,int occupiedUnits,int upcomingDeadlines,int overdueDeadlines) {}
    public record OperatingInput(int totalUnits,int occupiedUnits,BigDecimal arrears) {}
    public record Insight(String severity,String code,String title,String explanation,Long assetId,String recommendedAction) {}
    public record ProjectionYear(int year,BigDecimal assetValue,BigDecimal debt,BigDecimal netWorth,
            BigDecimal income,BigDecimal expenses,BigDecimal cashFlow) {}
    public record GoalProgress(long goalId,String name,String goalType,BigDecimal targetAmount,
            BigDecimal currentAmount,BigDecimal progressPercent,LocalDate targetDate,String status) {}
    public record VaultDocumentView(WealthVaultDocument document,String downloadUrl) {}
    public record Dashboard(PortfolioSummary summary,List<AssetPerformance> assets,
            List<WealthObligation> obligations,List<WealthGoal> goals,List<GoalProgress> goalProgress,
            List<Insight> insights,List<ProjectionYear> projection) {}
}
