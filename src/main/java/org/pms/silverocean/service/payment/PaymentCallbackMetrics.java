package org.pms.silverocean.service.payment;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/** Low-cardinality operational telemetry for externally initiated payment callbacks. */
@Component
public class PaymentCallbackMetrics {
    private final MeterRegistry meters;

    public PaymentCallbackMetrics(MeterRegistry meters) {
        this.meters = meters;
    }

    public void rejected(String provider, String reason) {
        meters.counter("slickhood.payment.callback.rejected",
                "provider", provider, "reason", reason).increment();
    }

    public <T> T observe(String provider, Supplier<T> callback) {
        Timer.Sample sample = Timer.start(meters);
        String outcome = "success";
        try {
            return callback.get();
        } catch (RuntimeException exception) {
            outcome = "failure";
            throw exception;
        } finally {
            sample.stop(Timer.builder("slickhood.payment.callback.duration")
                    .tag("provider", provider)
                    .tag("outcome", outcome)
                    .register(meters));
        }
    }
}
