package org.pms.silverocean.controller;

import org.pms.silverocean.config.ProductionModuleGuardrails;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component("productionReadiness")
public class ProductionReadinessController implements HealthIndicator {
    private final ProductionModuleGuardrails guardrails;
    private final Environment environment;

    public ProductionReadinessController(ProductionModuleGuardrails guardrails, Environment environment) {
        this.guardrails = guardrails;
        this.environment = environment;
    }

    @Override
    public Health health() {
        var assessment = guardrails.assess();
        var builder = assessment.ready() ? Health.up() : Health.down();
        boolean whatsappEnabled = environment.getProperty("whatsapp.enabled", Boolean.class, false);
        String scope = "wealth,insurance,affiliate,services,soko,helpdesk,notifications"
                + (whatsappEnabled ? ",whatsapp" : "");
        return builder
                .withDetail("scope", scope)
                .withDetail("whatsappStatus", whatsappEnabled ? "enabled" : "on-hold")
                .withDetail("missingOrUnsafeConfiguration", assessment.missingOrUnsafeConfiguration())
                .build();
    }
}
