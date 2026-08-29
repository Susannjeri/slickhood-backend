package org.pms.silverocean.service.communityfund;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.payment.invoice.InvoiceService;
import org.pms.silverocean.service.payment.ledger.FinancialLedgerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

import static org.pms.silverocean.service.communityfund.CommunityFundModels.*;

@Service
@RequiredArgsConstructor
public class CommunityFundService {
    private static final Set<String> FUND_TYPES=Set.of("WELFARE","PROJECT","RESERVE","EMERGENCY","OTHER");
    private static final Set<String> SCOPES=Set.of("HOMEOWNERS","TENANTS","ALL_OCCUPANTS");
    private final CommunityFundRepo funds;
    private final CommunityFundContributionRepo contributions;
    private final CommunityFundExpenditureRepo expenditures;
    private final CommunityFundTransactionRepo transactions;
    private final PropertyRepo properties;
    private final PropertyOwnershipRepo ownerships;
    private final UnitTenantRepo tenancies;
    private final AccountDao accounts;
    private final UserDao users;
    private final InvoiceService invoices;
    private final FinancialLedgerService ledger;

    @Transactional
    public CommunityFund create(CreateFundRequest request){
        long userId=users.getUserId();requireManagedProperty(request.propertyId(),userId);
        String type=normal(request.fundType()),scope=normal(request.contributorScope());
        if(!FUND_TYPES.contains(type)||!SCOPES.contains(scope)||request.dueDate().isBefore(request.opensOn())||
                request.closesOn()!=null&&request.closesOn().isBefore(request.dueDate()))invalid();
        PaymentAccount account=requireFundAccount(request.paymentAccountId(),userId,false);
        CommunityFund fund=new CommunityFund();fund.setPropertyId(request.propertyId());fund.setName(request.name().trim());
        fund.setFundType(type);fund.setContributorScope(scope);fund.setDescription(request.description().trim());
        fund.setCurrency(normal(request.currency()));fund.setTargetAmount(money(request.targetAmount()));
        fund.setDefaultContribution(money(request.defaultContribution()));fund.setOpensOn(request.opensOn());
        fund.setDueDate(request.dueDate());fund.setClosesOn(request.closesOn());fund.setStatus("DRAFT");
        fund.setPaymentAccountId(account.getId());fund.setCustodianUserId(account.getCreatedBy());
        fund.setDualApprovalRequired(request.dualApprovalRequired());fund.setCreatedBy(userId);fund.setActive(true);
        return funds.save(fund);
    }

    public List<CommunityFund> list(){long id=users.getUserId();return canManage()?funds.findManaged(id):funds.findForContributor(id);}

    @Transactional
    public CommunityFund open(long fundId){
        CommunityFund fund=requireManagedFund(fundId);if(!"DRAFT".equals(fund.getStatus()))invalid();
        requireFundAccount(fund.getPaymentAccountId(),fund.getCustodianUserId(),true);
        LinkedHashMap<String,Member> members=new LinkedHashMap<>();
        if(!"TENANTS".equals(fund.getContributorScope()))for(PropertyOwnership o:ownerships.findAllByPropertyIdAndActiveTrue(fund.getPropertyId()))
            if(o.getUnitId()!=null)members.put(o.getHomeownerUserId()+":"+o.getUnitId(),new Member(o.getHomeownerUserId(),o.getUnitId()));
        if(!"HOMEOWNERS".equals(fund.getContributorScope()))for(UnitTenant t:tenancies.findActiveByPropertyId(fund.getPropertyId()))
            members.put(t.getUserId()+":"+t.getUnitId(),new Member(t.getUserId(),t.getUnitId()));
        for(Member member:members.values())enrol(fund,member);
        fund.setStatus("OPEN");return funds.save(fund);
    }

    @Transactional
    public CommunityFundContribution pledge(long contributionId,PledgeRequest request){
        CommunityFundContribution contribution=contributions.findById(contributionId).filter(CommunityFundContribution::isActive)
                .filter(c->c.getContributorUserId().equals(users.getUserId())).orElseThrow(this::notFound);
        CommunityFund fund=funds.findById(contribution.getFundId()).filter(f->f.isActive()&&"OPEN".equals(f.getStatus())).orElseThrow(this::notFound);
        if(contribution.getInvoiceId()!=null)invalid();BigDecimal amount=money(request.amount());
        PMSInvoice invoice=invoices.createFundInvoice(contribution.getUnitId(),contribution.getContributorUserId(),fund.getCustodianUserId(),fund.getPaymentAccountId(),
                Map.of(fund.getName()+" contribution",amount.doubleValue()),fund.getDueDate());
        contribution.setAssessedAmount(amount);contribution.setInvoiceId(invoice.getId());contribution.setStatus("ASSESSED");
        return contributions.save(contribution);
    }

    public FundDashboard dashboard(long fundId){
        CommunityFund fund=funds.findById(fundId).filter(CommunityFund::isActive).orElseThrow(this::notFound);
        boolean manager=isManaged(fund);long userId=users.getUserId();
        List<CommunityFundContribution> all=contributions.findByFundIdAndActiveTrueOrderByCreatedOnAsc(fundId);
        List<CommunityFundContribution> mine=all.stream().filter(c->c.getContributorUserId().equals(userId)).toList();
        if(!manager&&mine.isEmpty())throw notFound();
        List<CommunityFundExpenditure> expenseList=expenditures.findByFundIdAndActiveTrueOrderByCreatedOnDesc(fundId);
        BigDecimal assessed=sumContributions(all,false),collected=sumContributions(all,true);
        BigDecimal spent=expenseList.stream().filter(e->"PAID".equals(e.getStatus())).map(CommunityFundExpenditure::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal committed=expenseList.stream().filter(e->"APPROVED".equals(e.getStatus())).map(CommunityFundExpenditure::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        PaymentAccount account=accounts.getAccountById(fund.getPaymentAccountId());
        return new FundDashboard(fund,new PaymentAccountView(account.getId(),account.getName(),account.getChannel().name(),account.isActive(),account.isVerified()),
                assessed,collected,committed,spent,collected.subtract(spent).subtract(committed),all.size(),(int)all.stream().filter(c->"PAID".equals(c.getStatus())).count(),
                mine,manager?all:List.of(),expenseList,transactions.findByFundIdAndActiveTrueOrderByOccurredAtDesc(fundId),manager);
    }

    @Transactional
    public CommunityFundExpenditure requestExpenditure(long fundId,ExpenditureRequest request){
        CommunityFund fund=requireManagedFund(fundId);if(!"OPEN".equals(fund.getStatus()))invalid();
        FundDashboard view=dashboard(fundId);BigDecimal amount=money(request.amount());if(view.available().compareTo(amount)<0)invalid();
        CommunityFundExpenditure expense=new CommunityFundExpenditure();expense.setFundId(fundId);expense.setPurpose(request.purpose().trim());
        expense.setCategory(normal(request.category()));expense.setAmount(amount);expense.setBeneficiaryType(normal(request.beneficiaryType()));
        expense.setBeneficiaryUserId(request.beneficiaryUserId());expense.setBeneficiaryName(request.beneficiaryName().trim());
        expense.setBeneficiaryReference(StringUtils.trimToNull(request.beneficiaryReference()));expense.setEvidenceFileRef(StringUtils.trimToNull(request.evidenceFileRef()));
        expense.setStatus("REQUESTED");expense.setCreatedBy(users.getUserId());expense.setActive(true);return expenditures.save(expense);
    }

    @Transactional
    public CommunityFundExpenditure approve(long expenditureId){
        CommunityFundExpenditure expense=requireManagedExpense(expenditureId);CommunityFund fund=funds.findById(expense.getFundId()).orElseThrow(this::notFound);
        if(!"REQUESTED".equals(expense.getStatus())||fund.isDualApprovalRequired()&&Objects.equals(expense.getCreatedBy(),users.getUserId()))invalid();
        BigDecimal available=dashboard(fund.getId()).available();if(available.compareTo(expense.getAmount())<0)invalid();
        expense.setStatus("APPROVED");expense.setApprovedBy(users.getUserId());expense.setApprovedAt(LocalDateTime.now());return expenditures.save(expense);
    }

    @Transactional
    public CommunityFundExpenditure reject(long expenditureId,RejectRequest request){
        CommunityFundExpenditure expense=requireManagedExpense(expenditureId);if(!Set.of("REQUESTED","APPROVED").contains(expense.getStatus()))invalid();
        expense.setStatus("REJECTED");expense.setRejectionReason(request.reason().trim());return expenditures.save(expense);
    }

    @Transactional
    public CommunityFundExpenditure disburse(long expenditureId,DisbursementRequest request){
        CommunityFundExpenditure expense=requireManagedExpense(expenditureId);if(!"APPROVED".equals(expense.getStatus()))invalid();
        CommunityFund fund=funds.findById(expense.getFundId()).orElseThrow(this::notFound);
        expense.setStatus("PAID");expense.setPaidBy(users.getUserId());expense.setPaidAt(LocalDateTime.now());
        expense.setPaymentReference(request.paymentReference().trim());if(StringUtils.isNotBlank(request.evidenceFileRef()))expense.setEvidenceFileRef(request.evidenceFileRef().trim());
        expense=expenditures.save(expense);recordExpenseTransaction(fund,expense);
        ledger.recordCommunityFundExpenditure(expense.getId(),fund.getId(),fund.getPropertyId(),fund.getCurrency(),expense.getAmount(),fund.getCustodianUserId(),expense.getPaymentReference());
        return expense;
    }

    @Transactional
    public void completePaidInvoice(long invoiceId,String providerReference,BigDecimal amount,String currency,LocalDateTime paidAt){
        contributions.findByInvoiceIdAndActiveTrue(invoiceId).ifPresent(contribution->{
            String key="COMMUNITY_FUND_CONTRIBUTION:"+invoiceId;if(transactions.existsByEventKey(key))return;
            CommunityFund fund=funds.findById(contribution.getFundId()).orElseThrow(this::notFound);
            contribution.setPaidAmount(money(amount));contribution.setPaidAt(paidAt==null?LocalDateTime.now():paidAt);
            contribution.setPaymentReference(providerReference);contribution.setStatus("PAID");contributions.save(contribution);
            CommunityFundTransaction transaction=new CommunityFundTransaction();transaction.setFundId(fund.getId());transaction.setEventKey(key);
            transaction.setTransactionType("CONTRIBUTION");transaction.setAmount(money(amount));transaction.setCurrency(StringUtils.defaultIfBlank(currency,fund.getCurrency()).toUpperCase());
            transaction.setDescription("Contribution received");transaction.setSourceType("INVOICE");transaction.setSourceId(invoiceId);
            transaction.setContributorUserId(contribution.getContributorUserId());transaction.setExternalReference(providerReference);
            transaction.setOccurredAt(contribution.getPaidAt());transaction.setCreatedBy(fund.getCustodianUserId());transaction.setActive(true);transactions.save(transaction);
        });
    }

    private void enrol(CommunityFund fund,Member member){
        if(contributions.existsByFundIdAndContributorUserIdAndUnitIdAndActiveTrue(fund.getId(),member.userId(),member.unitId()))return;
        CommunityFundContribution contribution=new CommunityFundContribution();contribution.setFundId(fund.getId());contribution.setContributorUserId(member.userId());
        contribution.setUnitId(member.unitId());contribution.setAssessedAmount(fund.getDefaultContribution());contribution.setPaidAmount(BigDecimal.ZERO.setScale(2));
        contribution.setStatus(fund.getDefaultContribution().signum()>0?"ASSESSED":"INVITED");contribution.setCreatedBy(users.getUserId());contribution.setActive(true);
        if(fund.getDefaultContribution().signum()>0){PMSInvoice invoice=invoices.createFundInvoice(member.unitId(),member.userId(),fund.getCustodianUserId(),fund.getPaymentAccountId(),
                Map.of(fund.getName()+" contribution",fund.getDefaultContribution().doubleValue()),fund.getDueDate());contribution.setInvoiceId(invoice.getId());}
        contributions.save(contribution);
    }

    private void recordExpenseTransaction(CommunityFund fund,CommunityFundExpenditure expense){String key="COMMUNITY_FUND_EXPENDITURE:"+expense.getId();if(transactions.existsByEventKey(key))return;
        CommunityFundTransaction transaction=new CommunityFundTransaction();transaction.setFundId(fund.getId());transaction.setEventKey(key);transaction.setTransactionType("EXPENDITURE");
        transaction.setAmount(expense.getAmount());transaction.setCurrency(fund.getCurrency());transaction.setDescription(expense.getPurpose());transaction.setSourceType("EXPENDITURE");transaction.setSourceId(expense.getId());
        transaction.setBeneficiaryUserId(expense.getBeneficiaryUserId());transaction.setBeneficiaryName(expense.getBeneficiaryName());transaction.setExternalReference(expense.getPaymentReference());
        transaction.setOccurredAt(expense.getPaidAt());transaction.setCreatedBy(users.getUserId());transaction.setActive(true);transactions.save(transaction);}
    private CommunityFund requireManagedFund(long id){CommunityFund fund=funds.findById(id).filter(CommunityFund::isActive).orElseThrow(this::notFound);if(!isManaged(fund))throw notFound();return fund;}
    private CommunityFundExpenditure requireManagedExpense(long id){CommunityFundExpenditure e=expenditures.findById(id).filter(CommunityFundExpenditure::isActive).orElseThrow(this::notFound);requireManagedFund(e.getFundId());return e;}
    private boolean isManaged(CommunityFund fund){try{requireManagedProperty(fund.getPropertyId(),users.getUserId());return true;}catch(PMSCustomException e){return false;}}
    private Property requireManagedProperty(long propertyId,long userId){PMSRole role=users.getActiveRole();return(role==PMSRole.LANDLORD?properties.findByIdAndCreatedByAndActiveTrue(propertyId,userId):properties.findByIdAndManagerRole(propertyId,userId,role.name())).orElseThrow(this::notFound);}
    private PaymentAccount requireFundAccount(long accountId,long custodian,boolean verified){PaymentAccount account=accounts.getAccountById(accountId);if(!account.isActive()||account.getCategory()!=AccountCategory.COMMUNITY_FUND||!Objects.equals(account.getCreatedBy(),custodian)||verified&&!account.isVerified())throw new PMSCustomException(ResponseCode.ACCOUNT_UNAUTHORIZED);return account;}
    private boolean canManage(){return users.hasPermission(org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_COMMUNITY_FUNDS);}
    private BigDecimal sumContributions(List<CommunityFundContribution> values,boolean paid){return values.stream().map(value->paid?value.getPaidAmount():value.getAssessedAmount()).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private String normal(String value){return value.trim().toUpperCase(Locale.ROOT).replace(' ','_');}
    private BigDecimal money(BigDecimal amount){return amount.setScale(2,RoundingMode.HALF_UP);}
    private PMSCustomException notFound(){return new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);}
    private void invalid(){throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);}
    private record Member(Long userId,Long unitId){}
}
