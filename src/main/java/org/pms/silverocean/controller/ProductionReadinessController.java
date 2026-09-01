package org.pms.silverocean.controller;

import org.pms.silverocean.config.ProductionModuleGuardrails;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("productionReadiness")
public class ProductionReadinessController implements HealthIndicator {
    private final ProductionModuleGuardrails guardrails;

    public ProductionReadinessController(ProductionModuleGuardrails guardrails) {
        this.guardrails = guardrails;
    }

    @Override
    public Health health() {
        var assessment = guardrails.assess();
        var builder = assessment.ready() ? Health.up() : Health.down();
        return builder
                .withDetail("scope", "wealth,insurance,affiliate,services,soko,helpdesk")
                .withDetail("missingOrUnsafeConfiguration", assessment.missingOrUnsafeConfiguration())
                .build();
    }
}
