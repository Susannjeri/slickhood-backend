package org.pms.silverocean.service.wealth;

import org.pms.silverocean.database.pms.entities.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class WealthModels {
    private WealthModels() {}
    public record AssetView(long id,Long propertyId,String assetType,String name,String reference,
            String location,String currency,BigDecimal acquisitionCost,LocalDate acquisitionDate,
            BigDecimal currentValue,LocalDate valuationDate,String status,String exchangeCode,
            String instrumentSymbol,BigDecimal quantity,BigDecimal averageUnitCost,String pricingMode,
            BigDecimal marketPrice,String quoteProvider,String quoteStatus,String quoteAsOf) {
        public AssetView(WealthAsset asset) {
            this(asset.getId(),asset.getPropertyId(),asset.getAssetType(),asset.getName(),asset.getReference(),
                    asset.getLocation(),asset.getCurrency(),asset.getAcquisitionCost(),asset.getAcquisitionDate(),
                    asset.getCurrentValue(),asset.getValuationDate(),asset.getStatus(),asset.getExchangeCode(),
                    asset.getInstrumentSymbol(),asset.getQuantity(),asset.getAverageUnitCost(),asset.getPricingMode(),
                    asset.getMarketPrice(),asset.getQuoteProvider(),asset.getQuoteStatus(),
                    asset.getQuoteAsOf()==null?null:asset.getQuoteAsOf().toString());
        }
    }
    public record AssetPerformance(long assetId,String name,String assetType,String currency,
            BigDecimal value,BigDecimal debt,BigDecimal equity,BigDecimal income,
            BigDecimal expenses,BigDecimal netOperatingIncome,BigDecimal annualDebtService,BigDecimal cashFlow,BigDecimal rentalYieldPercent,
            BigDecimal appreciation,BigDecimal loanToValuePercent,BigDecimal concentrationPercent,
            int totalUnits,int occupiedUnits,BigDecimal occupancyPercent,BigDecimal arrears) {}
    public record PortfolioSummary(String currency,BigDecimal totalAssetValue,BigDecimal totalDebt,
            BigDecimal netWorth,BigDecimal annualIncome,BigDecimal annualExpenses,BigDecimal netOperatingIncome,
            BigDecimal annualDebtService,BigDecimal cashFlow,BigDecimal equity,BigDecimal appreciation,BigDecimal portfolioYieldPercent,
            BigDecimal loanToValuePercent,BigDecimal occupancyPercent,BigDecimal arrears,
            int assetCount,int totalUnits,int occupiedUnits,int upcomingDeadlines,int overdueDeadlines) {}
    public record OperatingInput(int totalUnits,int occupiedUnits,BigDecimal arrears) {}
    public record Insight(String severity,String code,String title,String explanation,Long assetId,String recommendedAction) {}
    public record ProjectionYear(int year,BigDecimal assetValue,BigDecimal debt,BigDecimal netWorth,
            BigDecimal income,BigDecimal expenses,BigDecimal cashFlow) {}
    public record GoalProgress(long goalId,String name,String goalType,BigDecimal targetAmount,
            BigDecimal currentAmount,BigDecimal progressPercent,LocalDate targetDate,String status) {}
    /** Public vault metadata. Storage keys are deliberately never part of the API model. */
    public record VaultDocumentMetadata(long id,Long assetId,String category,String displayName,
            String contentType,long fileSize,String checksumSha256,LocalDate documentDate,
            LocalDate expiryDate,String notes) {
        public VaultDocumentMetadata(WealthVaultDocument document) {
            this(document.getId(),document.getAssetId(),document.getCategory(),document.getDisplayName(),
                    document.getContentType(),document.getFileSize(),document.getChecksumSha256(),
                    document.getDocumentDate(),document.getExpiryDate(),document.getNotes());
        }
    }
    public record VaultDocumentView(VaultDocumentMetadata document,String downloadUrl) {}
    public record AssetLedger(List<WealthValuation> valuations,List<WealthCashFlow> cashFlows,
            List<WealthLiability> liabilities,List<WealthObligation> obligations,
            List<VaultDocumentView> documents) {}
    public record MarketQuoteView(long assetId,String symbol,String exchange,String currency,BigDecimal unitPrice,
            BigDecimal quantity,BigDecimal marketValue,BigDecimal changeAmount,BigDecimal changePercent,
            String provider,String freshness,String asOf) {}
    public record MarketDataStatus(boolean configured,String lastRunAt,int lastProcessed,int lastFailures,String lastError) {}
    public record AdvisorProfile(int completenessScore,String headline,List<String> nextBestActions,
            int marketPricedAssets,int staleValuations,boolean hasWill,boolean hasTrust) {}
    public record Dashboard(PortfolioSummary summary,List<AssetPerformance> assets,
            List<WealthObligation> obligations,List<WealthGoal> goals,List<GoalProgress> goalProgress,
            List<Insight> insights,List<ProjectionYear> projection,AdvisorProfile advisor) {}
}
