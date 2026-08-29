package org.pms.silverocean.service.leasefinance;
import jakarta.validation.constraints.*;import java.math.BigDecimal;import java.util.List;
public final class LeaseFinanceModels{private LeaseFinanceModels(){}public enum Type{DEPOSIT_RECEIVED,DEPOSIT_DEDUCTION,DEPOSIT_REFUND,LATE_FEE_CHARGED,LATE_FEE_WAIVED,CREDIT_NOTE_ISSUED,CREDIT_NOTE_APPLIED}
 public record Create(@NotBlank @Size(max=190)String idempotencyKey,long leaseId,Long invoiceId,@NotNull Type type,@NotNull @DecimalMin("0.01")BigDecimal amount,@Size(max=120)String externalReference,@NotBlank @Size(max=1000)String reason){}
 public record Balance(BigDecimal depositHeld,BigDecimal lateFeeOutstanding,BigDecimal unappliedCredit){}
 public record Ledger(List<org.pms.silverocean.database.pms.entities.LeaseFinancialEvent> events,Balance balance){}
 public record Rule(@NotNull @DecimalMin("0.00")BigDecimal flatAmount,@NotNull @DecimalMin("0.00")BigDecimal percentageRate,@Min(0)int graceDays,@DecimalMin("0.01")BigDecimal maximumAmount,boolean enabled){}
 public record Assess(@NotBlank @Size(max=190)String idempotencyKey,long leaseId,long invoiceId){}
}
