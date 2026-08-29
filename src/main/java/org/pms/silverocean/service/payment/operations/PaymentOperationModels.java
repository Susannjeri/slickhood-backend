package org.pms.silverocean.service.payment.operations;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class PaymentOperationModels {
    private PaymentOperationModels(){}
    public enum Type { PROVIDER_FEE, SETTLEMENT, REFUND, REVERSAL, DISPUTE, CHARGEBACK }
    public enum Status { INITIATED, PENDING, CONFIRMED, FAILED, CANCELLED }
    public record Create(@NotBlank @Size(max=190) String idempotencyKey,
                         @NotBlank @Size(max=120) String caseReference,
                         long paymentId,@NotNull Type type,@NotNull Status status,
                         @NotNull @DecimalMin("0.01") BigDecimal amount,
                         @Size(max=50) String provider,@Size(max=120) String providerReference,
                         @Size(max=1000) String reason){}
}
