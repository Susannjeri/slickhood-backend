package org.pms.silverocean.service.wealth;

import org.pms.silverocean.database.pms.entities.*;
import java.math.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.pms.silverocean.service.wealth.WealthModels.*;

public final class WealthAnalytics {
    private static final BigDecimal HUNDRED=BigDecimal.valueOf(100);
    private WealthAnalytics() {}
    public static Dashboard calculate(List<WealthAsset> assets,List<WealthCashFlow> flows,
            List<WealthLiability> liabilities,List<WealthObligation> obligations,List<WealthGoal> goals,
            int years,BigDecimal valueGrowthPct,BigDecimal incomeGrowthPct,BigDecimal expenseGrowthPct){
        return calculate(assets,flows,liabilities,obligations,goals,Map.of(),years,valueGrowthPct,incomeGrowthPct,expenseGrowthPct);
    }
    public static Dashboard calculate(List<WealthAsset> assets,List<WealthCashFlow> flows,
            List<WealthLiability> liabilities,List<WealthObligation> obligations,List<WealthGoal> goals,
            Map<Long,OperatingInput> operating,int years,BigDecimal valueGrowthPct,BigDecimal incomeGrowthPct,BigDecimal expenseGrowthPct){
        Map<Long,List<WealthCashFlow>> flowsByAsset=flows.stream().collect(Collectors.groupingBy(WealthCashFlow::getAssetId));
        Map<Long,BigDecimal> debtByAsset=liabilities.stream().collect(Collectors.groupingBy(WealthLiability::getAssetId,
                Collectors.reducing(BigDecimal.ZERO,WealthLiability::getOutstandingPrincipal,BigDecimal::add)));
        Map<Long,BigDecimal> debtServiceByAsset=liabilities.stream().collect(Collectors.groupingBy(WealthLiability::getAssetId,
                Collectors.reducing(BigDecimal.ZERO,l->Optional.ofNullable(l.getMonthlyPayment()).orElse(BigDecimal.ZERO).multiply(BigDecimal.valueOf(12)),BigDecimal::add)));
        BigDecimal totalValue=sum(assets.stream().map(WealthAsset::getCurrentValue).toList());
        List<AssetPerformance> performance=assets.stream().map(a->performance(a,flowsByAsset.getOrDefault(a.getId(),List.of()),debtByAsset.getOrDefault(a.getId(),BigDecimal.ZERO),debtServiceByAsset.getOrDefault(a.getId(),BigDecimal.ZERO),totalValue,operating.getOrDefault(a.getId(),new OperatingInput(0,0,BigDecimal.ZERO)))).toList();
        BigDecimal debt=sum(performance.stream().map(AssetPerformance::debt).toList());
        BigDecimal income=sum(performance.stream().map(AssetPerformance::income).toList());
        BigDecimal expenses=sum(performance.stream().map(AssetPerformance::expenses).toList());
        BigDecimal debtService=sum(performance.stream().map(AssetPerformance::annualDebtService).toList());
        BigDecimal arrears=sum(performance.stream().map(AssetPerformance::arrears).toList());
        int totalUnits=performance.stream().mapToInt(AssetPerformance::totalUnits).sum(),occupiedUnits=performance.stream().mapToInt(AssetPerformance::occupiedUnits).sum();
        BigDecimal noi=income.subtract(expenses),netWorth=totalValue.subtract(debt);
        long overdue=obligations.stream().filter(o->!"COMPLETED".equals(o.getStatus())&&deadline(o)!=null&&deadline(o).isBefore(LocalDate.now())).count();
        long upcoming=obligations.stream().filter(o->!"COMPLETED".equals(o.getStatus())&&deadline(o)!=null&&!deadline(o).isBefore(LocalDate.now())&&deadline(o).isBefore(LocalDate.now().plusDays(o.getReminderDays()+1L))).count();
        String currency=assets.stream().map(WealthAsset::getCurrency).findFirst().orElse("KES");
        PortfolioSummary summary=new PortfolioSummary(currency,totalValue,debt,netWorth,income,expenses,noi,debtService,noi.subtract(debtService),
                netWorth,sum(performance.stream().map(AssetPerformance::appreciation).toList()),pct(noi,totalValue),pct(debt,totalValue),
                pct(BigDecimal.valueOf(occupiedUnits),BigDecimal.valueOf(totalUnits)),arrears,assets.size(),totalUnits,occupiedUnits,(int)upcoming,(int)overdue);
        List<Insight> insights=insights(assets,performance,obligations);
        List<ProjectionYear> projection=project(summary,Math.max(1,Math.min(years,30)),valueGrowthPct,incomeGrowthPct,expenseGrowthPct);
        List<GoalProgress> progress=goals.stream().map(g->goal(g,summary)).toList();
        return new Dashboard(summary,performance,obligations,goals,progress,insights,projection);
    }
    private static AssetPerformance performance(WealthAsset a,List<WealthCashFlow> flows,BigDecimal debt,BigDecimal annualDebtService,BigDecimal total,OperatingInput operating){
        BigDecimal income=sum(flows.stream().filter(f->"INCOME".equals(f.getFlowType())).map(WealthCashFlow::getAmount).toList());
        BigDecimal expense=sum(flows.stream().filter(f->"EXPENSE".equals(f.getFlowType())).map(WealthCashFlow::getAmount).toList());
        BigDecimal noi=income.subtract(expense),value=a.getCurrentValue(),equity=value.subtract(debt);
        BigDecimal cost=Optional.ofNullable(a.getAcquisitionCost()).orElse(BigDecimal.ZERO);
        return new AssetPerformance(a.getId(),a.getName(),a.getAssetType(),a.getCurrency(),value,debt,equity,income,expense,noi,annualDebtService,noi.subtract(annualDebtService),pct(noi,value),value.subtract(cost),pct(debt,value),pct(value,total),operating.totalUnits(),operating.occupiedUnits(),pct(BigDecimal.valueOf(operating.occupiedUnits()),BigDecimal.valueOf(operating.totalUnits())),operating.arrears());
    }
    private static List<Insight> insights(List<WealthAsset> assets,List<AssetPerformance> rows,List<WealthObligation> obligations){
        List<Insight> result=new ArrayList<>(); LocalDate today=LocalDate.now();
        for(AssetPerformance row:rows){
            if(row.loanToValuePercent().compareTo(BigDecimal.valueOf(70))>0)result.add(new Insight("HIGH","HIGH_LTV","High leverage",row.name()+" has an LTV above 70%.",row.assetId(),"Review refinancing or debt-reduction options."));
            if(row.netOperatingIncome().signum()<0) result.add(new Insight("HIGH","NEGATIVE_CASH_FLOW","Negative cash flow",row.name()+" costs more than it earns in the selected period.",row.assetId(),"Review rent, vacancy and controllable operating costs."));
            if(row.concentrationPercent().compareTo(BigDecimal.valueOf(40))>0&&rows.size()>1)result.add(new Insight("MEDIUM","CONCENTRATION","Portfolio concentration",row.name()+" represents over 40% of portfolio value.",row.assetId(),"Consider whether this concentration matches your risk strategy."));
            if(row.totalUnits()>0&&row.occupancyPercent().compareTo(BigDecimal.valueOf(80))<0)result.add(new Insight("MEDIUM","LOW_OCCUPANCY","Low occupancy",row.name()+" is below 80% occupancy ("+row.occupiedUnits()+" of "+row.totalUnits()+" units occupied).",row.assetId(),"Review vacancies, pricing, listings and tenant onboarding."));
            if(row.arrears().signum()>0)result.add(new Insight("HIGH","RENT_ARREARS","Outstanding arrears",row.name()+" has unpaid invoices totalling "+row.arrears()+" "+row.currency()+".",row.assetId(),"Review overdue invoices and begin the configured reminder or notice process."));
        }
        for(WealthAsset asset:assets)if(ChronoUnit.DAYS.between(asset.getValuationDate(),today)>365)result.add(new Insight("MEDIUM","STALE_VALUATION","Valuation needs review",asset.getName()+" has not been valued for more than 12 months.",asset.getId(),"Add a current professional or market valuation."));
        for(WealthObligation o:obligations){LocalDate d=deadline(o);if(d!=null&&!"COMPLETED".equals(o.getStatus())&&d.isBefore(today))result.add(new Insight("HIGH","OVERDUE_COMPLIANCE","Overdue: "+o.getTitle(),"Deadline was "+d+".",o.getAssetId(),"Resolve and mark the obligation complete."));else if(d!=null&&!"COMPLETED".equals(o.getStatus())&&!d.isAfter(today.plusDays(o.getReminderDays())))result.add(new Insight("MEDIUM","UPCOMING_COMPLIANCE","Due soon: "+o.getTitle(),"Deadline is "+d+".",o.getAssetId(),"Prepare renewal or payment before the deadline."));}
        if(result.isEmpty())result.add(new Insight("INFO","NO_IMMEDIATE_RISKS","No immediate risks detected","No configured rule triggered for the current data.",null,"Keep valuations, cash flows and compliance dates current."));
        return result;
    }
    private static List<ProjectionYear> project(PortfolioSummary s,int years,BigDecimal vg,BigDecimal ig,BigDecimal eg){
        List<ProjectionYear> out=new ArrayList<>();BigDecimal value=s.totalAssetValue(),debt=s.totalDebt(),income=s.annualIncome(),expense=s.annualExpenses(),debtService=s.annualDebtService();
        BigDecimal vf=factor(vg),inf=factor(ig),ef=factor(eg);
        for(int y=1;y<=years;y++){value=value.multiply(vf);income=income.multiply(inf);expense=expense.multiply(ef);out.add(new ProjectionYear(y,money(value),money(debt),money(value.subtract(debt)),money(income),money(expense),money(income.subtract(expense).subtract(debtService))));}return out;
    }
    private static GoalProgress goal(WealthGoal g,PortfolioSummary s){BigDecimal current=switch(g.getGoalType()){case "INCOME"->s.annualIncome();case "EQUITY"->s.equity();case "DEBT_REDUCTION"->s.totalDebt();default->s.netWorth();};BigDecimal progress="DEBT_REDUCTION".equals(g.getGoalType())?(current.compareTo(g.getTargetAmount())<=0?HUNDRED:pct(g.getTargetAmount(),current)):pct(current,g.getTargetAmount());return new GoalProgress(g.getId(),g.getName(),g.getGoalType(),g.getTargetAmount(),current,progress,g.getTargetDate(),g.getStatus());}
    private static LocalDate deadline(WealthObligation o){return o.getExpiryDate()!=null?o.getExpiryDate():o.getDueDate();}
    private static BigDecimal sum(List<BigDecimal> values){return values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private static BigDecimal pct(BigDecimal n,BigDecimal d){return d==null||d.signum()==0?BigDecimal.ZERO:n.multiply(HUNDRED).divide(d,2,RoundingMode.HALF_UP);}
    private static BigDecimal factor(BigDecimal pct){return BigDecimal.ONE.add(Optional.ofNullable(pct).orElse(BigDecimal.ZERO).divide(HUNDRED,8,RoundingMode.HALF_UP));}
    private static BigDecimal money(BigDecimal n){return n.setScale(2,RoundingMode.HALF_UP);}
}
