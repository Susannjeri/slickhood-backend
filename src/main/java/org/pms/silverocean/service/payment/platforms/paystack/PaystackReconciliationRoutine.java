package org.pms.silverocean.service.payment.platforms.paystack;

import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.service.payment.PaymentDao;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class PaystackReconciliationRoutine {
    private static final List<String> RECOVERABLE = List.of("initialized", "pending", "ongoing", "processing", "queued");
    private final PaymentDao payments;
    private final PaystackPlatform paystack;

    @Value("${payment.paystack.reconciliation-enabled:true}")
    private boolean enabled;
    @Value("${payment.paystack.reconciliation-batch-size:25}")
    private int batchSize;
    @Value("${payment.paystack.reconciliation-min-age-seconds:30}")
    private int minimumAgeSeconds;
    @Value("${payment.paystack.reconciliation-max-attempts:60}")
    private int maxAttempts;

    public PaystackReconciliationRoutine(PaymentDao payments, PaystackPlatform paystack) {
        this.payments = payments;
        this.paystack = paystack;
    }

    @Scheduled(fixedDelayString = "${payment.paystack.reconciliation-delay-ms:60000}")
    public void reconcile() {
        if (!enabled || !paystack.isActive()) return;
        var ids = payments.findPendingReconciliationIds(PaymentChannel.PAYSTACK, RECOVERABLE, maxAttempts,
                LocalDateTime.now().minusSeconds(minimumAgeSeconds), PageRequest.of(0, Math.max(1, Math.min(batchSize, 100))));
        for (Long id : ids) {
            try {
                paystack.reconcilePendingPayment(id);
            } catch (RuntimeException exception) {
                log.warn("Paystack reconciliation deferred for payment {}: {}", id, exception.getMessage());
            }
        }
    }
}
