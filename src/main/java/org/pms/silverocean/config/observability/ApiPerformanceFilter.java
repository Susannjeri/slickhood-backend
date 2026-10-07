package org.pms.silverocean.config.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.pms.silverocean.config.JWTFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Adds low-cardinality, privacy-safe API latency metrics and actionable slow-request logs.
 * Route templates are used instead of concrete URLs so identifiers and tokens are never emitted.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class ApiPerformanceFilter extends OncePerRequestFilter {
    static final String DURATION_METRIC = "slickhood.api.request.duration";
    static final String SLOW_METRIC = "slickhood.api.request.slow";
    private static final Logger log = LoggerFactory.getLogger(ApiPerformanceFilter.class);

    private final MeterRegistry meterRegistry;
    private final long slowRequestThresholdMs;

    public ApiPerformanceFilter(
            MeterRegistry meterRegistry,
            @Value("${observability.slow-request-threshold-ms:750}") long slowRequestThresholdMs) {
        this.meterRegistry = meterRegistry;
        this.slowRequestThresholdMs = Math.max(0, slowRequestThresholdMs);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationNanos = System.nanoTime() - startedAt;
            long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);
            String method = safeMethod(request.getMethod());
            String route = routeTemplate(request);
            String status = Integer.toString(response.getStatus());
            String role = activeRole(request);

            Timer.builder(DURATION_METRIC)
                    .description("SlickHood API request duration")
                    .tags("method", method, "route", route, "status", status, "role", role)
                    .register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);

            if (durationMs >= slowRequestThresholdMs) {
                meterRegistry.counter(SLOW_METRIC,
                        "method", method, "route", route, "status", status, "role", role).increment();
                log.warn("Slow API request method={} route={} status={} durationMs={} role={} correlationId={}",
                        method, route, status, durationMs, role, response.getHeader(CorrelationIdFilter.HEADER));
            }
        }
    }

    static String routeTemplate(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String route && route.startsWith("/") && route.length() <= 160) {
            return route;
        }
        return "UNMATCHED";
    }

    private static String safeMethod(String method) {
        if (method == null) return "UNKNOWN";
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD" -> method.toUpperCase(Locale.ROOT);
            default -> "OTHER";
        };
    }

    private static String activeRole(HttpServletRequest request) {
        Object role = request.getAttribute(JWTFilter.ACTIVE_ROLE_ATTRIBUTE);
        if (role == null) return "ANONYMOUS";
        String value = role.toString().toUpperCase(Locale.ROOT);
        return value.matches("[A-Z_]{2,40}") ? value : "UNKNOWN";
    }
}
