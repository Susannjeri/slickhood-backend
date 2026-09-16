package org.pms.silverocean.service.affiliate;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.AffiliatePolicy;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.PMSCustomException;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AffiliatePolicyServiceTest {
 private final AffiliatePolicyRepo repo=mock(AffiliatePolicyRepo.class);private final UserDao users=mock(UserDao.class);private final AuditLogService audit=mock(AuditLogService.class);private final AffiliatePolicyService service=new AffiliatePolicyService(repo,users,audit);
 private AffiliatePolicyService.Edit edit(long version){return new AffiliatePolicyService.Edit(new BigDecimal("12.50"),4,new BigDecimal("2000"),7,version,"Approved programme changes");}
 @Test void onlySuperadminMayManagePolicy(){assertThrows(PMSCustomException.class,()->service.edit(edit(-1)));verifyNoInteractions(repo,audit);}
 @Test void readsPreserveExistingDeploymentDefaultsUntilPublished(){ReflectionTestUtils.setField(service,"rate",new BigDecimal("25"));ReflectionTestUtils.setField(service,"count",3);ReflectionTestUtils.setField(service,"minimum",new BigDecimal("1000"));ReflectionTestUtils.setField(service,"hold",14);assertEquals(-1,service.current().version());assertEquals(new BigDecimal("25"),service.current().commissionRate());verify(repo,never()).saveAndFlush(any());}
 @Test void publicationIsVersionCheckedAndAudited(){when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);var p=new AffiliatePolicy();p.setVersion(2);p.setCommissionRate(new BigDecimal("25"));when(repo.lockGlobal()).thenReturn(Optional.of(p));var result=service.edit(edit(2));assertEquals(new BigDecimal("12.50"),result.commissionRate());assertEquals(4,result.eligiblePaymentCount());verify(audit).createAuditLog(eq(p),eq("AFFILIATE_POLICY_UPDATE"),contains("before="),eq(true));}
 @Test void stalePublicationDoesNotMutateOrRecalculateLedgers(){when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);var p=new AffiliatePolicy();p.setVersion(2);when(repo.lockGlobal()).thenReturn(Optional.of(p));assertThrows(PMSCustomException.class,()->service.edit(edit(1)));verify(repo,never()).saveAndFlush(any());verifyNoInteractions(audit);}
 @Test void requestValidationRejectsInvalidFinancialParameters(){try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()){var validator=factory.getValidator();assertTrue(validator.validate(edit(-1)).isEmpty());assertFalse(validator.validate(new AffiliatePolicyService.Edit(BigDecimal.valueOf(101),0,BigDecimal.ZERO,-1,-2,"")).isEmpty());}}
}
