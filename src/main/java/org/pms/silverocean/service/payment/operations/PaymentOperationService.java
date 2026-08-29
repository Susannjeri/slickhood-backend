package org.pms.silverocean.service.payment.operations;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.PaymentOperationRepo;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.database.pms.entities.PaymentOperation;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.payment.PaymentDao;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.pms.silverocean.service.payment.ledger.FinancialLedgerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

@Service @RequiredArgsConstructor
public class PaymentOperationService {
    private final PaymentOperationRepo operations; private final PaymentDao payments; private final InvoiceDao invoices;
    private final FinancialLedgerService ledger; private final UserDao users;

    @Transactional public PaymentOperation append(PaymentOperationModels.Create request){
        requireFinance(); String key=request.idempotencyKey().trim();
        return operations.findByIdempotencyKey(key).map(existing->{if(!sameRequest(existing,request))throw invalid();return existing;}).orElseGet(()->create(request,key));
    }
    public List<PaymentOperation> caseHistory(String caseReference){
        long userId=users.getUserId(); boolean privileged=users.hasRole(PMSRole.SUPER_ADMIN)||users.hasRole(PMSRole.FINANCE);
        return operations.findVisibleCase(caseReference,userId,privileged);
    }
    private PaymentOperation create(PaymentOperationModels.Create request,String key){
        PMSPayment payment=payments.findPaymentByID(request.paymentId()).orElseThrow(this::invalid);
        PMSInvoice invoice=invoices.getInvoiceByRef(payment.getBillReference()).orElseThrow(this::invalid);
        BigDecimal amount=request.amount().setScale(2,RoundingMode.HALF_UP); String providerRef=StringUtils.trimToNull(request.providerReference());
        if(request.status()==PaymentOperationModels.Status.CONFIRMED&&providerRef==null)throw invalid();
        validateTransition(request.caseReference(),payment.getId(),request.type(),request.status()); validateAmount(payment,request.type(),request.status(),amount);
        PaymentOperation operation=new PaymentOperation(key,request.caseReference().trim(),payment.getId(),invoice.getId(),request.type().name(),request.status().name(),amount,
                StringUtils.defaultIfBlank(invoice.getCurrency(),"UNSPECIFIED").toUpperCase(Locale.ROOT),StringUtils.left(StringUtils.trimToNull(request.provider()),50),
                StringUtils.left(providerRef,120),StringUtils.left(StringUtils.trimToNull(request.reason()),1000),ZonedDateTime.now(PMSUtils.getZoneId()),users.getUserId());
        operation=operations.save(operation); if(request.status()==PaymentOperationModels.Status.CONFIRMED)ledger.recordPaymentOperation(invoice,operation); return operation;
    }
    private void validateTransition(String caseReference,long paymentId,PaymentOperationModels.Type type,PaymentOperationModels.Status status){
        List<PaymentOperation> history=operations.findAllByCaseReferenceOrderByOccurredAtAsc(caseReference.trim());
        if(history.stream().anyMatch(o->o.getPaymentId()!=paymentId))throw invalid();
        if(history.stream().anyMatch(o->!o.getOperationType().equals(type.name())))throw invalid();
        if(history.isEmpty()&&List.of(PaymentOperationModels.Status.FAILED,PaymentOperationModels.Status.CANCELLED).contains(status))throw invalid();
        if(history.stream().anyMatch(o->List.of("CONFIRMED","FAILED","CANCELLED").contains(o.getStatus())))throw invalid();
    }
    private boolean sameRequest(PaymentOperation existing,PaymentOperationModels.Create request){
        return existing.getCaseReference().equals(request.caseReference().trim())&&existing.getPaymentId()==request.paymentId()
                &&existing.getOperationType().equals(request.type().name())&&existing.getStatus().equals(request.status().name())
                &&existing.getAmount().compareTo(request.amount().setScale(2,RoundingMode.HALF_UP))==0;
    }
    private void validateAmount(PMSPayment payment,PaymentOperationModels.Type type,PaymentOperationModels.Status status,BigDecimal amount){
        if(status!=PaymentOperationModels.Status.CONFIRMED)return;
        if(List.of(PaymentOperationModels.Type.REFUND,PaymentOperationModels.Type.REVERSAL,PaymentOperationModels.Type.CHARGEBACK).contains(type)){
            BigDecimal already=operations.sumConfirmed(payment.getId(),"REFUND").add(operations.sumConfirmed(payment.getId(),"REVERSAL")).add(operations.sumConfirmed(payment.getId(),"CHARGEBACK"));
            BigDecimal collected=BigDecimal.valueOf(payment.getAmount()).setScale(2,RoundingMode.HALF_UP); if(already.add(amount).compareTo(collected)>0)throw invalid();
        }
    }
    private void requireFinance(){if(!users.hasRole(PMSRole.FINANCE)&&!users.hasRole(PMSRole.SUPER_ADMIN))throw new PMSCustomException(ResponseCode.INVALID_ROLE);}
    private PMSCustomException invalid(){return new PMSCustomException(ResponseCode.GENERAL_FAILURE);}
}
