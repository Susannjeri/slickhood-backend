package org.pms.silverocean.service.wealth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.currencyexchange.CurrencyConversionService;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.wealth.vault.VaultMalwareScanner;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WealthJourneyRegressionTest {
 @Mock WealthAssetRepo assetRepo; @Mock WealthValuationRepo valuationRepo;
 @Mock WealthCashFlowRepo cashFlowRepo; @Mock WealthLiabilityRepo liabilityRepo;
 @Mock WealthObligationRepo obligationRepo; @Mock WealthVaultDocumentRepo vaultRepo;
 @Mock WealthGoalRepo goalRepo; @Mock UserDao userDao; @Mock GarageService garageService;
 @Mock CurrencyConversionService currencyConversionService; @Mock VaultMalwareScanner malwareScanner;
 @Mock WealthAdminService wealthAdminService; @Mock UnitRepo unitRepo;
 @Mock PMSInvoiceRepo invoiceRepo; @Mock PropertyRepo propertyRepo;
 @InjectMocks WealthService service;
 @BeforeEach void setup(){ReflectionTestUtils.setField(service,"baseCurrency","KES");lenient().when(userDao.getUserId()).thenReturn(7L);}
 private WealthAsset asset(){var a=new WealthAsset();a.setId(1L);a.setOwnerUserId(7L);a.setActive(true);a.setAssetType("CASH");a.setName("Savings");a.setCurrency("KES");a.setCurrentValue(new BigDecimal("100000"));a.setAcquisitionCost(new BigDecimal("90000"));a.setValuationDate(LocalDate.now());a.setStatus("ACTIVE");return a;}
 private void owned(WealthAsset a){when(assetRepo.findByIdAndOwnerUserIdAndActiveTrue(1L,7L)).thenReturn(Optional.of(a));}
 private WealthRequests.AssetRequest request(String currency){return new WealthRequests.AssetRequest(null,"CASH","Savings",null,null,currency,BigDecimal.TEN,null,BigDecimal.TEN,LocalDate.now(),"ACTIVE",null,null,null,null,"MANUAL");}
 @Test void createsAssetAndOpeningValuationTogether(){
  var type=new WealthAssetType();type.setCode("CASH");type.setActive(true);
  when(wealthAdminService.requireForAsset("CASH",null)).thenReturn(type);
  when(assetRepo.save(any())).thenAnswer(call->{WealthAsset a=call.getArgument(0);a.setId(1L);return a;});
  assertThat(service.createAsset(request("KES")).name()).isEqualTo("Savings");
  verify(valuationRepo).save(argThat(v->v.getAssetId()==1L&&v.getAmount().compareTo(BigDecimal.TEN)==0&&"OPENING_VALUE".equals(v.getSource())));
 }
 @Test void historicalValuationDoesNotOverwriteLatest(){var a=asset();owned(a);when(valuationRepo.save(any())).thenAnswer(call->call.getArgument(0));service.addValuation(1,new WealthRequests.ValuationRequest(BigDecimal.TEN,LocalDate.now().minusYears(1),"STATEMENT",null));assertThat(a.getCurrentValue()).isEqualByComparingTo("100000");verify(assetRepo,never()).save(any());verify(valuationRepo).save(any());}
 @Test void currentValuationUpdatesAndMarksManualOverride(){var a=asset();owned(a);service.addValuation(1,new WealthRequests.ValuationRequest(BigDecimal.TEN,LocalDate.now(),"STATEMENT",null));assertThat(a.getCurrentValue()).isEqualByComparingTo("10");assertThat(a.getQuoteStatus()).isEqualTo("MANUAL_OVERRIDE");}
 @Test void currencyCannotReinterpretExistingHistory(){owned(asset());assertThatThrownBy(()->service.updateAsset(1,request("USD"))).isInstanceOf(PMSCustomException.class);verify(assetRepo,never()).save(any());}
 @Test void otherOwnersCannotReadLedger(){when(assetRepo.findByIdAndOwnerUserIdAndActiveTrue(1L,7L)).thenReturn(Optional.empty());assertThatThrownBy(()->service.ledger(1)).isInstanceOf(PMSCustomException.class);verifyNoInteractions(valuationRepo,cashFlowRepo,liabilityRepo,vaultRepo);}
 @Test void dashboardRetainsConvertedMonthlyDebtServiceAndOnlyOverdueRent(){
  var a=asset();a.setPropertyId(9L);when(assetRepo.findAllByOwnerUserIdAndActiveTrueOrderByName(7L)).thenReturn(List.of(a));
  var debt=new WealthLiability();debt.setAssetId(1L);debt.setCurrency("USD");debt.setOutstandingPrincipal(new BigDecimal("100"));debt.setMonthlyPayment(new BigDecimal("10"));
  when(liabilityRepo.findAllByAssetIdInAndActiveTrue(List.of(1L))).thenReturn(List.of(debt));
  when(currencyConversionService.convert(any(),anyString(),eq("KES"))).thenAnswer(call->((BigDecimal)call.getArgument(0)).multiply("USD".equals(call.getArgument(1))?new BigDecimal("100"):BigDecimal.ONE));
  var overdue=invoice("RENTAL",LocalDate.now().minusDays(1),50);var future=invoice("RENTAL",LocalDate.now().plusDays(1),1000);var sale=invoice("SALE",LocalDate.now().minusDays(1),9999);var cleared=invoice("RENTAL",LocalDate.now().minusDays(1),0);var noDate=invoice("RENTAL",null,999);
  when(propertyRepo.findByIdAndStaffOrOwner(9L,7L)).thenReturn(Optional.of(new Property()));
  when(invoiceRepo.findAllByPropertyIdInAndActiveTrueAndPaidFalse(List.of(9L))).thenReturn(List.of(overdue,future,sale,cleared,noDate));
  var result=service.dashboard(5,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
  assertThat(result.summary().annualDebtService()).isEqualByComparingTo("12000");assertThat(result.summary().cashFlow()).isEqualByComparingTo("-12000");assertThat(result.summary().arrears()).isEqualByComparingTo("50");assertThat(result.insights()).anyMatch(i->i.code().equals("NEGATIVE_CASH_FLOW"));
 }
 private PMSInvoice invoice(String type,LocalDate due,double balance){var i=new PMSInvoice();i.setPropertyId(9L);i.setBillingType(type);i.setCurrency("KES");i.setDueDate(due);i.setPendingAmount(balance);i.setAmount(10000);return i;}
 @Test void scannerUnavailableDoesNotStoreOrMislabelTheDocument(){var file=new MockMultipartFile("file","statement.pdf","application/pdf","%PDF-safe-fixture".getBytes());when(malwareScanner.scan(any())).thenReturn(VaultMalwareScanner.Result.UNAVAILABLE);assertThatThrownBy(()->service.upload((Long)null,"PENSION_STATEMENT",null,null,null,file)).isInstanceOf(PMSCustomException.class);verifyNoInteractions(garageService,vaultRepo);}
 @Test void infectedFileNeverReachesStorage(){var file=new MockMultipartFile("file","statement.pdf","application/pdf","%PDF-test-fixture".getBytes());when(malwareScanner.scan(any())).thenReturn(VaultMalwareScanner.Result.INFECTED);assertThatThrownBy(()->service.upload((Long)null,"PENSION_STATEMENT",null,null,null,file)).isInstanceOf(PMSCustomException.class);verifyNoInteractions(garageService,vaultRepo);}
 @Test void missingCategoryIsValidationFailureNotNullPointer(){assertThatThrownBy(()->service.upload((Long)null,null,null,null,null,null)).isInstanceOf(PMSCustomException.class);}
 @Test void aDebtFreeGoalCanTargetZeroButOtherGoalsCannot(){var date=LocalDate.now().plusYears(1);service.addGoal(new WealthRequests.GoalRequest("DEBT_REDUCTION","Debt free",BigDecimal.ZERO,"KES",date));verify(goalRepo).save(any());assertThatThrownBy(()->service.addGoal(new WealthRequests.GoalRequest("NET_WORTH","Invalid",BigDecimal.ZERO,"KES",date))).isInstanceOf(PMSCustomException.class);}
 @Test void revokedPropertyMembershipCannotReadLiveOperations(){var a=asset();a.setPropertyId(9L);when(assetRepo.findAllByOwnerUserIdAndActiveTrueOrderByName(7L)).thenReturn(List.of(a));when(currencyConversionService.convert(any(),anyString(),anyString())).thenAnswer(call->call.getArgument(0));service.dashboard(1,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);verifyNoInteractions(unitRepo,invoiceRepo);}
 @Test void earliestDeadlineIsNotHiddenByLaterExpiry(){var o=new WealthObligation();o.setAssetId(1L);o.setTitle("Licence");o.setStatus("OPEN");o.setDueDate(LocalDate.now().minusDays(1));o.setExpiryDate(LocalDate.now().plusYears(1));o.setReminderDays(30);var d=WealthAnalytics.calculate(List.of(asset()),List.of(),List.of(),List.of(o),List.of(),1,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);assertThat(d.summary().overdueDeadlines()).isEqualTo(1);}
}
