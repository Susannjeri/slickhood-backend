package org.pms.silverocean.service.affiliate;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.database.pms.AffiliatePolicyRepo;
import org.pms.silverocean.database.pms.entities.AffiliatePolicy;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.PMSCustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
@Service @RequiredArgsConstructor
public class AffiliatePolicyService {
 private final AffiliatePolicyRepo repo;private final UserDao users;private final AuditLogService audit;
 @Value("${affiliate.commission-rate:25.00}") private BigDecimal rate;
 @Value("${affiliate.eligible-payment-count:3}") private int count;
 @Value("${affiliate.minimum-payout:1000.00}") private BigDecimal minimum;
 @Value("${affiliate.commission-hold-days:14}") private int hold;
 public record Policy(BigDecimal commissionRate,int eligiblePaymentCount,BigDecimal minimumPayout,int holdDays,long version){}
 public record Edit(@NotNull @DecimalMin(value="0",inclusive=false) @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal commissionRate,@Min(1) @Max(100) int eligiblePaymentCount,@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=13,fraction=2) BigDecimal minimumPayout,@Min(0) @Max(365) int holdDays,@Min(-1) long version,@NotBlank @Size(max=1000) String reason){}
 public Policy current(){return repo.findByPolicyKey("GLOBAL").map(this::dto).orElseGet(()->new Policy(rate,count,minimum,hold,-1));}
 public Policy adminView(){admin();return current();}
 @Transactional public Policy edit(Edit r){admin();var existing=repo.lockGlobal();long version=existing.map(AffiliatePolicy::getVersion).orElse(-1L);if(version!=r.version())throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);var p=existing.orElseGet(AffiliatePolicy::new);String before=existing.isPresent()?p.toAuditJSON():"environment defaults";p.setCreatedBy(existing.isPresent()?p.getCreatedBy():users.getUserId());p.setActive(true);p.setCommissionRate(r.commissionRate());p.setEligiblePaymentCount(r.eligiblePaymentCount());p.setMinimumPayout(r.minimumPayout());p.setHoldDays(r.holdDays());repo.saveAndFlush(p);audit.createAuditLog(p,"AFFILIATE_POLICY_UPDATE",r.reason()+"; before="+before,true);return dto(p);}
 private Policy dto(AffiliatePolicy p){return new Policy(p.getCommissionRate(),p.getEligiblePaymentCount(),p.getMinimumPayout(),p.getHoldDays(),p.getVersion());}
 private void admin(){if(!users.hasRole(PMSRole.SUPER_ADMIN))throw new PMSCustomException(ResponseCode.INVALID_ROLE);}
}
