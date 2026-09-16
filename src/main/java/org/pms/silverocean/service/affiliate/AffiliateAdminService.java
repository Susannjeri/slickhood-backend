package org.pms.silverocean.service.affiliate;

import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.kyc.AccountStatus;
import org.pms.silverocean.service.notification.BusinessNotificationService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class AffiliateAdminService {
    private final AffiliateQueryRepo queries;
    private final AffiliateProfileRepo profiles;
    private final AffiliateReferralRepo referrals;
    private final UserDao users;
    private final AuditLogService audit;
    private final BusinessNotificationService alerts;
    public record Summary(long userId, String name, String email, AffiliateModels.Profile profile, long referrals, long conversions) {}
    public record Balance(String currency, BigDecimal available, BigDecimal pending, BigDecimal lifetime, BigDecimal pendingPayouts, boolean payoutSupported) {}
    public record Detail(Summary affiliate, List<Balance> balances) {}
    public record PayoutRow(AffiliateModels.AdminPayout payout, String affiliateName, String affiliateEmail) {}
    public record Edit(@NotBlank @Pattern(regexp="ACTIVE|SUSPENDED|BLACKLISTED|INACTIVE|REJECTED") String status,
                       @NotNull @DecimalMin(value="0",inclusive=false) @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal commissionRate,
                       @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=2) BigDecimal minimumPayout,
                       @NotBlank @Size(max=1000) String reason) {}

    public Page<Summary> directory(String query, String status, Pageable pageable) {
        requireAdmin();
        var page = queries.directory(search(query), state(status, Set.of("PENDING_APPROVAL","ACTIVE","SUSPENDED","BLACKLISTED","INACTIVE","REJECTED")), bounded(pageable));
        var ids = page.getContent().stream().map(AffiliateProfile::getUserId).toList();
        Map<Long,Users> identities = identities(ids);
        Map<Long,ReferralTally> counts = referralCounts(ids);
        return page.map(p -> summary(p, identities.get(p.getUserId()), counts.getOrDefault(p.getUserId(), ReferralTally.NONE)));
    }
    public Detail detail(long userId) {
        requireAdmin();
        var profile = profile(userId);
        return new Detail(summary(profile, users.findById(userId).orElse(null)), balances(userId,profile.getCurrency()));
    }
    public List<Balance> ownBalances() {
        if(!users.hasRole(PMSRole.AFFILIATE)&&!users.hasRole(PMSRole.SUPER_ADMIN))throw new PMSCustomException(ResponseCode.INVALID_ROLE);
        var p=profile(users.getUserId());return balances(p.getUserId(),p.getCurrency());
    }
    List<Balance> balances(long userId, String payoutCurrency) {
        return queries.balances(userId).stream().map(b -> new Balance(b.getCurrency(), b.getEarned().subtract(b.getClawback()),
                b.getPending(), b.getLifetime(), queries.pendingPayouts(userId,b.getCurrency()), b.getCurrency().equals(payoutCurrency))).toList();
    }
    @Transactional("pmsDBTransactionManager")
    public Detail edit(long userId, Edit request) {
        requireAdmin();
        var p = profiles.findForUpdateByUserId(userId).orElseThrow(this::notFound);
        String before = p.toAuditJSON();
        String nextStatus=request.status().toUpperCase(Locale.ROOT);
        if ("ACTIVE".equals(nextStatus)) {
            var applicant = users.findById(userId).orElseThrow(this::notFound);
            if (!AccountStatus.ACTIVE.name().equals(applicant.getAccountStatus())) {
                throw new PMSCustomException(ResponseCode.KYC_ACCOUNT_RESTRICTED);
            }
        }
        p.setStatus(nextStatus); p.setCommissionRate(request.commissionRate()); p.setMinimumPayout(request.minimumPayout());
        p.setReviewedAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")));p.setReviewedByUserId(users.getUserId());p.setReviewNotes(request.reason().trim());
        p.setLastModifiedDate(LocalDateTime.now()); profiles.save(p);
        audit.createAuditLog(p,"AFFILIATE_PROFILE_UPDATE", "Before: " + before + "; reason: " + request.reason().trim(),true);
        alerts.publish(userId,"affiliate-profile:"+p.getId()+":"+UUID.randomUUID(),"AFFILIATE_STATUS",
                statusNotice(nextStatus),"/dashboard/affiliate");
        return detail(userId);
    }
    public Page<?> history(long userId, String ledger, Pageable pageable, boolean admin) {
        if(admin) requireAdmin();
        else if(userId != users.getUserId() || (!users.hasRole(PMSRole.AFFILIATE) && !users.hasRole(PMSRole.SUPER_ADMIN)))
            throw new PMSCustomException(ResponseCode.INVALID_ROLE);
        profile(userId);
        var page = bounded(pageable);
        return switch(ledger) {
            case "commissions" -> queries.commissions(userId,page).map(AffiliateModels.Commission::new);
            case "referrals" -> queries.referrals(userId,page).map(AffiliateModels.Referral::new);
            case "payouts" -> queries.payouts(userId,page).map(AffiliateModels.Payout::new);
            default -> throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        };
    }
    public Page<PayoutRow> payoutQueue(String query, String status, Pageable pageable) {
        requireAdmin();
        var page = queries.payoutQueue(search(query),state(status,Set.of("REQUESTED","PROCESSING","PAID","REJECTED")),bounded(pageable));
        var identities = identities(page.getContent().stream().map(AffiliatePayout::getAffiliateUserId).toList());
        return page.map(p -> {var u=identities.get(p.getAffiliateUserId());return new PayoutRow(new AffiliateModels.AdminPayout(p),u==null?null:u.getFullName(),u==null?null:u.getEmail());});
    }
    private Summary summary(AffiliateProfile p, Users u) {return new Summary(p.getUserId(),u==null?null:u.getFullName(),u==null?null:u.getEmail(),
            new AffiliateModels.Profile(p),referrals.countByAffiliateUserIdAndActiveTrue(p.getUserId()),referrals.countByAffiliateUserIdAndStatusAndActiveTrue(p.getUserId(),"CONVERTED"));}
    private Summary summary(AffiliateProfile p, Users u, ReferralTally tally) {return new Summary(p.getUserId(),u==null?null:u.getFullName(),u==null?null:u.getEmail(),
            new AffiliateModels.Profile(p),tally.referrals(),tally.conversions());}
    private record ReferralTally(long referrals,long conversions) {private static final ReferralTally NONE=new ReferralTally(0,0);}
    private Map<Long,ReferralTally> referralCounts(List<Long> ids) {if(ids.isEmpty())return Map.of();var result=new HashMap<Long,ReferralTally>();queries.referralCounts(ids).forEach(row->result.put(row.getAffiliateUserId(),new ReferralTally(row.getReferrals(),row.getConversions())));return result;}
    private Map<Long,Users> identities(List<Long> ids) {var result=new HashMap<Long,Users>();users.findAllById(ids).forEach(u->result.put(u.getId(),u));return result;}
    private AffiliateProfile profile(long id) {return profiles.findByUserIdAndActiveTrue(id).orElseThrow(this::notFound);}
    private void requireAdmin() {if(!users.hasRole(PMSRole.SUPER_ADMIN))throw new PMSCustomException(ResponseCode.INVALID_ROLE);}
    private String statusNotice(String status) {return switch(status) {
        case "ACTIVE" -> "Your affiliate application is approved and programme access is active. Existing commission entries have not been recalculated.";
        case "REJECTED" -> "Your affiliate application was not approved. Open your affiliate workspace for the review outcome or contact support.";
        case "PENDING_APPROVAL" -> "Your affiliate application is waiting for Superadmin approval. Referral and payout functions remain locked until approval.";
        case "SUSPENDED" -> "Your affiliate programme access is temporarily suspended. New referrals, commissions and payout requests are blocked; existing records remain available.";
        case "BLACKLISTED" -> "Your affiliate programme access is blacklisted. New referrals, commissions and payout requests are blocked. Contact support if you believe this is incorrect.";
        default -> "Your affiliate programme access is inactive. New referrals, commissions and payout requests are blocked; existing records remain available.";
    };}
    private PMSCustomException notFound(){return new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND);}
    public static Pageable bounded(Pageable p) {return PageRequest.of(Math.min(Math.max(0,p.getPageNumber()),100000),Math.min(Math.max(1,p.getPageSize()),100),Sort.by(Sort.Direction.DESC,"createdOn").and(Sort.by(Sort.Direction.DESC,"id")));}
    private String search(String query) {if(query==null||query.isBlank())return null;if(query.length()>160)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);return "%"+query.trim().toLowerCase(Locale.ROOT).replace("%","").replace("_","")+"%";}
    private String state(String state, Set<String> allowed) {if(state==null||state.isBlank())return null;String value=state.toUpperCase(Locale.ROOT);if(!allowed.contains(value))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);return value;}
}
