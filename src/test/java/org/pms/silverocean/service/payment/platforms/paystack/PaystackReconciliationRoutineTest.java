package org.pms.silverocean.service.payment.platforms.paystack;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.payment.PaymentDao;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaystackReconciliationRoutineTest {
    @Test void stalePendingPaymentsAreReconciledInABoundedBatch() {
        PaymentDao payments = mock(PaymentDao.class);
        PaystackPlatform paystack = mock(PaystackPlatform.class);
        when(paystack.isActive()).thenReturn(true);
        when(payments.findPendingReconciliationIds(eq(PaymentChannel.PAYSTACK), anyList(), eq(60),
                any(LocalDateTime.class), any(Pageable.class))).thenReturn(List.of(210L, 211L));
        PaystackReconciliationRoutine routine = new PaystackReconciliationRoutine(payments, paystack);
        ReflectionTestUtils.setField(routine, "enabled", true);
        ReflectionTestUtils.setField(routine, "batchSize", 25);
        ReflectionTestUtils.setField(routine, "minimumAgeSeconds", 30);
        ReflectionTestUtils.setField(routine, "maxAttempts", 60);

        routine.reconcile();

        verify(paystack).reconcilePendingPayment(210L);
        verify(paystack).reconcilePendingPayment(211L);
    }
}
