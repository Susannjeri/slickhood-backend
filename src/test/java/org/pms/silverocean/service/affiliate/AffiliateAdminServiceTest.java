package org.pms.silverocean.service.affiliate;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.notification.BusinessNotificationService;
import org.pms.silverocean.service.kyc.AccountStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AffiliateAdminServiceTest {
 final AffiliateQueryRepo queries=mock(AffiliateQueryRepo.class);
 final AffiliateProfileRepo profiles=mock(AffiliateProfileRepo.class);
 final AffiliateReferralRepo referrals=mock(AffiliateReferralRepo.class);
 final UserDao users=mock(UserDao.class);
 final AuditLogService audit=mock(AuditLogService.class);
 final BusinessNotificationService alerts=mock(BusinessNotificationService.class);
 final AffiliateAdminService service=new AffiliateAdminService(queries,profiles,referrals,users,audit,alerts);
 @Test void nonAdminCannotReadOtherAffiliates(){assertThrows(PMSCustomException.class,()->service.directory(null,null,PageRequest.of(0,20)));verifyNoInteractions(queries);}
 @Test void affiliateCannotReadAnotherPersonsHistory(){when(users.getUserId()).thenReturn(7L);when(users.hasRole(PMSRole.AFFILIATE)).thenReturn(true);assertThrows(PMSCustomException.class,()->service.history(8L,"payouts",PageRequest.of(0,20),false));verifyNoInteractions(queries);}
 @Test void queriesAreBoundedAndUseStableOrdering(){var page=AffiliateAdminService.bounded(PageRequest.of(200001,2000));assertEquals(100,page.getPageSize());assertEquals(100000,page.getPageNumber());assertNotNull(page.getSort().getOrderFor("id"));}
 @Test void directoryAggregatesReferralCountsOnceForThePage(){when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);var first=new AffiliateProfile();first.setUserId(7L);first.setStatus("ACTIVE");first.setCurrency("KES");var second=new AffiliateProfile();second.setUserId(8L);second.setStatus("ACTIVE");second.setCurrency("KES");when(queries.directory(isNull(),isNull(),any())).thenReturn(new PageImpl<>(List.of(first,second)));var count=mock(AffiliateQueryRepo.ReferralCount.class);when(count.getAffiliateUserId()).thenReturn(7L);when(count.getReferrals()).thenReturn(4L);when(count.getConversions()).thenReturn(2L);when(queries.referralCounts(List.of(7L,8L))).thenReturn(List.of(count));var page=service.directory(null,null,PageRequest.of(0,20));assertEquals(4,page.getContent().getFirst().referrals());assertEquals(0,page.getContent().getLast().referrals());verify(queries,times(1)).referralCounts(List.of(7L,8L));verifyNoInteractions(referrals);}
 @Test void fullHundredRowDirectoryPageKeepsReferralQueryVolumeConstant(){
  when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);var rows=new ArrayList<AffiliateProfile>();
  for(long id=1;id<=100;id++){var profile=new AffiliateProfile();profile.setUserId(id);profile.setStatus("ACTIVE");profile.setCurrency("KES");rows.add(profile);}
  when(queries.directory(isNull(),isNull(),any())).thenReturn(new PageImpl<>(rows));when(queries.referralCounts(any())).thenReturn(List.of());
  assertEquals(100,service.directory(null,null,PageRequest.of(0,100)).getNumberOfElements());
  verify(queries,times(1)).directory(isNull(),isNull(),any());verify(queries,times(1)).referralCounts(any());verify(users,times(1)).findAllById(any());verifyNoInteractions(referrals);
 }
 @Test void balancesNeverCombineCurrencies(){
  var kes=balance("KES",BigDecimal.valueOf(600),BigDecimal.valueOf(100));var usd=balance("USD",BigDecimal.valueOf(20),BigDecimal.ZERO);
  when(queries.balances(7L)).thenReturn(List.of(kes,usd));when(queries.pendingPayouts(7L,"KES")).thenReturn(BigDecimal.valueOf(300));when(queries.pendingPayouts(7L,"USD")).thenReturn(BigDecimal.ZERO);
  var result=service.balances(7L,"KES");assertEquals(2,result.size());assertEquals(BigDecimal.valueOf(500),result.getFirst().available());assertTrue(result.getFirst().payoutSupported());assertFalse(result.getLast().payoutSupported());assertEquals(BigDecimal.valueOf(20),result.getLast().available());
 }
 @Test void superAdminApprovalRequiresCompletedCommonKyc(){
  when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);var p=new AffiliateProfile();p.setId(1L);p.setUserId(7L);p.setStatus("PENDING_APPROVAL");p.setCurrency("KES");p.setCommissionRate(BigDecimal.valueOf(25));p.setMinimumPayout(BigDecimal.valueOf(1000));
  var applicant=new Users();applicant.setId(7L);applicant.setAccountStatus(AccountStatus.PENDING_KYC.name());
  when(profiles.findForUpdateByUserId(7L)).thenReturn(Optional.of(p));when(users.findById(7L)).thenReturn(Optional.of(applicant));
  assertThrows(PMSCustomException.class,()->service.edit(7L,new AffiliateAdminService.Edit("ACTIVE",BigDecimal.valueOf(25),BigDecimal.valueOf(1000),"Approve after review")));
  verify(profiles,never()).save(any());
 }
 @Test void superAdminCanApproveVerifiedAffiliateAndApplicantIsNotified(){
  when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);when(users.getUserId()).thenReturn(99L);var p=new AffiliateProfile();p.setId(1L);p.setUserId(7L);p.setStatus("PENDING_APPROVAL");p.setCurrency("KES");p.setCommissionRate(BigDecimal.valueOf(25));p.setMinimumPayout(BigDecimal.valueOf(1000));p.setActive(true);
  var applicant=new Users();applicant.setId(7L);applicant.setAccountStatus(AccountStatus.ACTIVE.name());
  when(profiles.findForUpdateByUserId(7L)).thenReturn(Optional.of(p));when(profiles.findByUserIdAndActiveTrue(7L)).thenReturn(Optional.of(p));when(users.findById(7L)).thenReturn(Optional.of(applicant));when(queries.balances(7L)).thenReturn(List.of());
  service.edit(7L,new AffiliateAdminService.Edit("ACTIVE",BigDecimal.valueOf(25),BigDecimal.valueOf(1000),"Identity and application approved"));
  assertEquals("ACTIVE",p.getStatus());assertEquals(99L,p.getReviewedByUserId());assertEquals("Identity and application approved",p.getReviewNotes());assertNotNull(p.getReviewedAt());
  verify(alerts).publish(eq(7L),anyString(),eq("AFFILIATE_STATUS"),contains("approved"),eq("/dashboard/affiliate"));
 }
 @Test void deactivationPreservesLedgerAndAuditsTheDecision(){
  when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);var p=new AffiliateProfile();p.setId(1L);p.setUserId(7L);p.setStatus("ACTIVE");p.setCurrency("KES");p.setCommissionRate(BigDecimal.valueOf(25));p.setMinimumPayout(BigDecimal.valueOf(1000));
  when(profiles.findForUpdateByUserId(7L)).thenReturn(Optional.of(p));when(profiles.findByUserIdAndActiveTrue(7L)).thenReturn(Optional.of(p));when(users.findById(7L)).thenReturn(Optional.empty());when(queries.balances(7L)).thenReturn(List.of());
  service.edit(7L,new AffiliateAdminService.Edit("INACTIVE",BigDecimal.valueOf(25),BigDecimal.valueOf(1000),"Approved suspension"));
  assertEquals("INACTIVE",p.getStatus());verify(profiles).save(p);verify(audit).createAuditLog(eq(p),eq("AFFILIATE_PROFILE_UPDATE"),contains("Approved suspension"),eq(true));verify(alerts).publish(eq(7L),anyString(),eq("AFFILIATE_STATUS"),anyString(),eq("/dashboard/affiliate"));verify(queries).balances(7L);verifyNoMoreInteractions(queries);
 }
 @Test void suspensionAndBlacklistAreDistinctAuditedStates(){
  when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);var p=new AffiliateProfile();p.setId(1L);p.setUserId(7L);p.setStatus("ACTIVE");p.setCurrency("KES");p.setCommissionRate(BigDecimal.valueOf(25));p.setMinimumPayout(BigDecimal.valueOf(1000));
  when(profiles.findForUpdateByUserId(7L)).thenReturn(Optional.of(p));when(profiles.findByUserIdAndActiveTrue(7L)).thenReturn(Optional.of(p));when(users.findById(7L)).thenReturn(Optional.empty());when(queries.balances(7L)).thenReturn(List.of());
  service.edit(7L,new AffiliateAdminService.Edit("SUSPENDED",BigDecimal.valueOf(25),BigDecimal.valueOf(1000),"Temporary compliance review"));
  assertEquals("SUSPENDED",p.getStatus());
  service.edit(7L,new AffiliateAdminService.Edit("BLACKLISTED",BigDecimal.valueOf(25),BigDecimal.valueOf(1000),"Confirmed programme abuse"));
  assertEquals("BLACKLISTED",p.getStatus());verify(profiles,times(2)).save(p);verify(audit,times(2)).createAuditLog(eq(p),eq("AFFILIATE_PROFILE_UPDATE"),anyString(),eq(true));verify(alerts,times(2)).publish(eq(7L),anyString(),eq("AFFILIATE_STATUS"),anyString(),eq("/dashboard/affiliate"));
 }
 private AffiliateQueryRepo.CurrencyBalance balance(String currency,BigDecimal earned,BigDecimal clawback){var b=mock(AffiliateQueryRepo.CurrencyBalance.class);when(b.getCurrency()).thenReturn(currency);when(b.getEarned()).thenReturn(earned);when(b.getClawback()).thenReturn(clawback);when(b.getPending()).thenReturn(BigDecimal.ZERO);when(b.getLifetime()).thenReturn(earned);return b;}
}
