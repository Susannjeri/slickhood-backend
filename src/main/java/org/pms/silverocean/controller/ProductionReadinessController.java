package org.pms.silverocean.controller;

import org.pms.silverocean.config.ProductionModuleGuardrails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/actuator/health/production-readiness")
public class ProductionReadinessController {
    private final ProductionModuleGuardrails guardrails;

    public ProductionReadinessController(ProductionModuleGuardrails guardrails) {
        this.guardrails = guardrails;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> readiness() {
        var assessment = guardrails.assess();
        var body = Map.<String, Object>of(
                "status", assessment.ready() ? "UP" : "DOWN",
                "scope", "wealth,insurance,affiliate,services,soko",
                "missingOrUnsafeConfiguration", assessment.missingOrUnsafeConfiguration());
        return ResponseEntity.status(assessment.ready() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
