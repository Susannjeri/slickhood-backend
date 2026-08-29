package org.pms.silverocean.service.payment.ledger;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.database.pms.FinancialJournalRepo;
import org.pms.silverocean.database.pms.FinancialLedgerLineRepo;
import org.pms.silverocean.database.pms.entities.FinancialJournal;
import org.pms.silverocean.database.pms.entities.FinancialLedgerLine;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PaymentOperation;
import org.pms.silverocean.database.pms.entities.Lease;
import org.pms.silverocean.database.pms.entities.LeaseFinancialEvent;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.UnitTenant;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FinancialLedgerService {
    private final FinancialJournalRepo journals;
    private final FinancialLedgerLineRepo lines;

    public boolean recordInvoiceIssued(PMSInvoice invoice) {
        BigDecimal amount=money(invoice.getAmount());
        if(invoice.getId()==null||amount.signum()<=0)return false;
        String creditAccount="COMMUNITY_FUND".equals(billingType(invoice))?"RESTRICTED_FUND_LIABILITY":"REVENUE_"+billingType(invoice);
        return post("INVOICE_ISSUED:"+invoice.getId(),"INVOICE_ISSUED",invoice,null,amount,
                "ACCOUNTS_RECEIVABLE",invoice.getBilledUserId(),creditAccount,invoice.getPayToUserId());
    }

    public boolean recordPaymentApplied(PMSInvoice invoice,String providerReference,BigDecimal amount) {
        if(invoice.getId()==null||amount==null||amount.signum()<=0)return false;
        String reference=StringUtils.trimToNull(providerReference);
        if(reference==null)throw new IllegalArgumentException("A provider transaction reference is required for ledger idempotency");
        return post("PAYMENT_APPLIED:"+invoice.getId()+":"+reference,"PAYMENT_APPLIED",invoice,reference,money(amount),
                "CASH_CLEARING",invoice.getPayToUserId(),"ACCOUNTS_RECEIVABLE",invoice.getBilledUserId());
    }

    public boolean recordUnappliedCredit(PMSInvoice invoice,String providerReference,BigDecimal amount) {
        if(invoice.getId()==null||amount==null||amount.signum()<=0)return false;
        String reference=StringUtils.trimToNull(providerReference);
        if(reference==null)throw new IllegalArgumentException("A provider transaction reference is required for ledger idempotency");
        return post("UNAPPLIED_CREDIT:"+invoice.getId()+":"+reference,"UNAPPLIED_CREDIT",invoice,reference,money(amount),
                "CASH_CLEARING",invoice.getPayToUserId(),"CUSTOMER_CREDIT_LIABILITY",invoice.getBilledUserId());
    }

    public boolean recordPaymentOperation(PMSInvoice invoice, PaymentOperation operation) {
        BigDecimal amount=money(operation.getAmount()); String eventKey="PAYMENT_OPERATION:"+operation.getIdempotencyKey();
        return switch(operation.getOperationType()){
            case "PROVIDER_FEE" -> post(eventKey,"PROVIDER_FEE",invoice,operation.getProviderReference(),amount,"PAYMENT_PROCESSING_EXPENSE",invoice.getPayToUserId(),"CASH_CLEARING",invoice.getPayToUserId());
            case "SETTLEMENT" -> post(eventKey,"SETTLEMENT",invoice,operation.getProviderReference(),amount,"BANK_CASH",invoice.getPayToUserId(),"CASH_CLEARING",invoice.getPayToUserId());
            case "REFUND" -> post(eventKey,"REFUND",invoice,operation.getProviderReference(),amount,"CUSTOMER_REFUNDS",invoice.getPayToUserId(),"CASH_CLEARING",invoice.getPayToUserId());
            case "REVERSAL" -> post(eventKey,"REVERSAL",invoice,operation.getProviderReference(),amount,"ACCOUNTS_RECEIVABLE",invoice.getBilledUserId(),"CASH_CLEARING",invoice.getPayToUserId());
            case "CHARGEBACK" -> post(eventKey,"CHARGEBACK",invoice,operation.getProviderReference(),amount,"CHARGEBACK_RECEIVABLE",invoice.getBilledUserId(),"CASH_CLEARING",invoice.getPayToUserId());
            default -> false;
        };
    }

    public boolean recordLeaseFinancialEvent(Lease lease, UnitTenant tenancy, Unit unit, LeaseFinancialEvent event){
        BigDecimal amount=money(event.getAmount());String key="LEASE_FINANCE:"+event.getIdempotencyKey();long owner=unit.getCreatedBy(),tenant=tenancy.getUserId();
        return switch(event.getEventType()){
            case "DEPOSIT_RECEIVED" -> postContext(key,event.getEventType(),"LEASE",Long.toString(lease.getId()),event.getExternalReference(),unit.getPropertyId(),unit.getId(),event.getCurrency(),amount,"CASH_CLEARING",owner,"TENANT_DEPOSIT_LIABILITY",tenant);
            case "DEPOSIT_DEDUCTION" -> postContext(key,event.getEventType(),"LEASE",Long.toString(lease.getId()),event.getExternalReference(),unit.getPropertyId(),unit.getId(),event.getCurrency(),amount,"TENANT_DEPOSIT_LIABILITY",tenant,"DAMAGE_RECOVERY_REVENUE",owner);
            case "DEPOSIT_REFUND" -> postContext(key,event.getEventType(),"LEASE",Long.toString(lease.getId()),event.getExternalReference(),unit.getPropertyId(),unit.getId(),event.getCurrency(),amount,"TENANT_DEPOSIT_LIABILITY",tenant,"CASH_CLEARING",owner);
            case "LATE_FEE_CHARGED" -> postContext(key,event.getEventType(),"LEASE",Long.toString(lease.getId()),event.getExternalReference(),unit.getPropertyId(),unit.getId(),event.getCurrency(),amount,"ACCOUNTS_RECEIVABLE",tenant,"LATE_FEE_REVENUE",owner);
            case "LATE_FEE_WAIVED" -> postContext(key,event.getEventType(),"LEASE",Long.toString(lease.getId()),event.getExternalReference(),unit.getPropertyId(),unit.getId(),event.getCurrency(),amount,"LATE_FEE_WAIVER",owner,"ACCOUNTS_RECEIVABLE",tenant);
            case "CREDIT_NOTE_ISSUED" -> postContext(key,event.getEventType(),"LEASE",Long.toString(lease.getId()),event.getExternalReference(),unit.getPropertyId(),unit.getId(),event.getCurrency(),amount,"SALES_RETURNS",owner,"CUSTOMER_CREDIT_LIABILITY",tenant);
            case "CREDIT_NOTE_APPLIED" -> postContext(key,event.getEventType(),"LEASE",Long.toString(lease.getId()),event.getExternalReference(),unit.getPropertyId(),unit.getId(),event.getCurrency(),amount,"CUSTOMER_CREDIT_LIABILITY",tenant,"ACCOUNTS_RECEIVABLE",tenant);
            default -> false;
        };}

    public boolean recordCommunityFundExpenditure(long expenditureId,long fundId,long propertyId,String currency,
                                                   BigDecimal amount,long custodianUserId,String paymentReference){
        return postContext("COMMUNITY_FUND_EXPENDITURE:"+expenditureId,"COMMUNITY_FUND_EXPENDITURE",
                "COMMUNITY_FUND",Long.toString(fundId),paymentReference,propertyId,null,currency,money(amount),
                "RESTRICTED_FUND_LIABILITY",custodianUserId,"BANK_CASH",custodianUserId);
    }

    private boolean postContext(String eventKey,String eventType,String sourceType,String sourceId,String providerReference,Long propertyId,Long unitId,String currency,BigDecimal amount,String debitAccount,long debitUser,String creditAccount,long creditUser){if(journals.existsByEventKey(eventKey))return false;ZonedDateTime occurred=ZonedDateTime.now(PMSUtils.getZoneId());FinancialJournal journal=journals.save(new FinancialJournal(eventKey,eventType,sourceType,sourceId,providerReference,occurred));String description=eventType.replace('_',' ')+" "+sourceId;lines.saveAll(List.of(new FinancialLedgerLine(journal.getId(),1,debitAccount,debitUser,propertyId,unitId,currency,amount,BigDecimal.ZERO.setScale(2),description),new FinancialLedgerLine(journal.getId(),2,creditAccount,creditUser,propertyId,unitId,currency,BigDecimal.ZERO.setScale(2),amount,description)));return true;}

    private boolean post(String eventKey,String eventType,PMSInvoice invoice,String providerReference,BigDecimal amount,
                         String debitAccount,long debitUser,String creditAccount,long creditUser) {
        if(journals.existsByEventKey(eventKey))return false;
        ZonedDateTime occurred=ZonedDateTime.now(PMSUtils.getZoneId());
        FinancialJournal journal=journals.save(new FinancialJournal(eventKey,eventType,"INVOICE",Long.toString(invoice.getId()),providerReference,occurred));
        Long propertyId=invoice.getPropertyId()>0?invoice.getPropertyId():null;
        Long unitId=invoice.getUnitId()>0?invoice.getUnitId():null;
        String currency=StringUtils.defaultIfBlank(invoice.getCurrency(),"UNSPECIFIED").toUpperCase(Locale.ROOT);
        String description=eventType.replace('_',' ')+" "+invoice.getRef();
        lines.saveAll(List.of(
                new FinancialLedgerLine(journal.getId(),1,debitAccount,debitUser,propertyId,unitId,currency,amount,BigDecimal.ZERO.setScale(2),description),
                new FinancialLedgerLine(journal.getId(),2,creditAccount,creditUser,propertyId,unitId,currency,BigDecimal.ZERO.setScale(2),amount,description)));
        return true;
    }

    private String billingType(PMSInvoice invoice){return StringUtils.defaultIfBlank(invoice.getBillingType(),"GENERAL").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]","_");}
    private BigDecimal money(double value){return BigDecimal.valueOf(value).setScale(2,RoundingMode.HALF_UP);}
    private BigDecimal money(BigDecimal value){return value.setScale(2,RoundingMode.HALF_UP);}
}
