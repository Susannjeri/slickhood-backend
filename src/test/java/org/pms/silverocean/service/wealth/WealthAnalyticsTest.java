package org.pms.silverocean.service.wealth;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WealthAnalyticsTest {
 @Test void calculatesNetWorthYieldLtvAndExplainableRisks(){
  WealthAsset a=asset(1,"Rental A","10000000","8000000",LocalDate.now().minusYears(2));
  WealthAsset b=asset(2,"Land B","2000000","1000000",LocalDate.now());
  WealthCashFlow income=flow(1,"INCOME","1200000"),expense=flow(1,"EXPENSE","300000");
  WealthLiability debt=new WealthLiability();debt.setAssetId(1);debt.setOutstandingPrincipal(new BigDecimal("7500000"));debt.setMonthlyPayment(new BigDecimal("10000"));debt.setActive(true);
  WealthObligation overdue=new WealthObligation();overdue.setAssetId(1);overdue.setTitle("Land rates");overdue.setDueDate(LocalDate.now().minusDays(1));overdue.setStatus("OPEN");overdue.setReminderDays(30);overdue.setActive(true);
  var operating=java.util.Map.of(1L,new WealthModels.OperatingInput(10,7,new BigDecimal("50000")));
  var result=WealthAnalytics.calculate(List.of(a,b),List.of(income,expense),List.of(debt),List.of(overdue),List.of(),operating,5,new BigDecimal("5"),new BigDecimal("3"),new BigDecimal("3"));
  assertEquals(new BigDecimal("12000000"),result.summary().totalAssetValue());
  assertEquals(new BigDecimal("4500000"),result.summary().netWorth());
  assertEquals(new BigDecimal("900000"),result.summary().netOperatingIncome());
  assertEquals(new BigDecimal("120000"),result.summary().annualDebtService());
  assertEquals(new BigDecimal("780000"),result.summary().cashFlow());
  assertEquals(new BigDecimal("780000"),result.assets().getFirst().cashFlow());
  assertEquals(new BigDecimal("7.50"),result.summary().portfolioYieldPercent());
  assertEquals(1,result.summary().overdueDeadlines());
  assertEquals(new BigDecimal("70.00"),result.summary().occupancyPercent());
  assertEquals(new BigDecimal("50000"),result.summary().arrears());
  assertTrue(result.insights().stream().anyMatch(i->i.code().equals("HIGH_LTV")));
  assertTrue(result.insights().stream().anyMatch(i->i.code().equals("STALE_VALUATION")));
  assertTrue(result.insights().stream().anyMatch(i->i.code().equals("OVERDUE_COMPLIANCE")));
  assertTrue(result.insights().stream().anyMatch(i->i.code().equals("LOW_OCCUPANCY")));
  assertTrue(result.insights().stream().anyMatch(i->i.code().equals("RENT_ARREARS")));
  assertEquals(new BigDecimal("12600000.00"),result.projection().getFirst().assetValue());
  assertEquals(new BigDecimal("807000.00"),result.projection().getFirst().cashFlow());
 }
 @Test void debtReductionGoalImprovesAsDebtFallsAndCompletesAtTarget(){
  WealthAsset a=asset(1,"Rental A","1000000","800000",LocalDate.now());
  WealthLiability debt=new WealthLiability();debt.setAssetId(1);debt.setOutstandingPrincipal(new BigDecimal("400000"));debt.setActive(true);
  WealthGoal goal=new WealthGoal();goal.setId(9L);goal.setGoalType("DEBT_REDUCTION");goal.setName("Reduce debt");goal.setTargetAmount(new BigDecimal("200000"));goal.setTargetDate(LocalDate.now().plusYears(1));goal.setStatus("ACTIVE");
  var result=WealthAnalytics.calculate(List.of(a),List.of(),List.of(debt),List.of(),List.of(goal),3,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
  assertEquals(new BigDecimal("50.00"),result.goalProgress().getFirst().progressPercent());
  debt.setOutstandingPrincipal(new BigDecimal("150000"));
  result=WealthAnalytics.calculate(List.of(a),List.of(),List.of(debt),List.of(),List.of(goal),3,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
  assertEquals(new BigDecimal("100"),result.goalProgress().getFirst().progressPercent());
 }
 @Test void handlesAnEmptyPortfolioWithoutDivisionErrors(){var result=WealthAnalytics.calculate(List.of(),List.of(),List.of(),List.of(),List.of(),3,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);assertEquals(BigDecimal.ZERO,result.summary().netWorth());assertEquals(3,result.projection().size());assertEquals("NO_IMMEDIATE_RISKS",result.insights().getFirst().code());}
 private WealthAsset asset(long id,String name,String value,String cost,LocalDate valuation){WealthAsset a=new WealthAsset();a.setId(id);a.setName(name);a.setAssetType("PROPERTY");a.setCurrency("KES");a.setCurrentValue(new BigDecimal(value));a.setAcquisitionCost(new BigDecimal(cost));a.setValuationDate(valuation);a.setActive(true);return a;}
 private WealthCashFlow flow(long asset,String type,String amount){WealthCashFlow f=new WealthCashFlow();f.setAssetId(asset);f.setFlowType(type);f.setAmount(new BigDecimal(amount));f.setActive(true);return f;}
}
