package org.pms.silverocean.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Secret-safe release checks for the Wealth, Insurance, Affiliate, Services and
 * Soko production capabilities. The deployment endpoint exposes property names,
 * never configured values.
 */
@Component
public class ProductionModuleGuardrails {
    private final Environment environment;

    public ProductionModuleGuardrails(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validateSafeBounds() {
        boundedInt("soko.stock-reservation-minutes", 20, 1, 1_440);
        boundedInt("wealth.market.batch-size", 50, 1, 200);
        boundedDuration("wealth.market.minimum-refresh", "PT15M", Duration.ofMinutes(1), Duration.ofDays(1));
        boundedInt("wealth.vault.antivirus.port", 3310, 1, 65_535);
        boundedInt("wealth.vault.antivirus.timeout-ms", 5_000, 250, 60_000);
        boundedInt("app.insurance.imap.port", 993, 1, 65_535);
        boundedInt("app.insurance.imap.max-per-poll", 50, 1, 200);
        boundedInt("app.insurance.imap.poll-delay-ms", 60_000, 10_000, 3_600_000);
    }

    public Assessment assess() {
        List<String> failures = new ArrayList<>();

        require(failures, "garage.s3.access.key");
        require(failures, "garage.s3.secret.key");
        requireHttps(failures, "garage.presigner.url");
        rejectLocal(failures, "garage.s3.url");

        require(failures, "spring.mail.host");
        require(failures, "spring.mail.username");
        require(failures, "spring.mail.password");
        requireHttps(failures, "app.public-url");
        requireHttpsOrigins(failures, "app.cors.allowed-origins");

        requireTrue(failures, "wealth.market.enabled");
        require(failures, "wealth.market.alpha-vantage.api-key");
        requireHttps(failures, "wealth.market.alpha-vantage.base-url");
        requireTrue(failures, "wealth.vault.antivirus.enabled");
        requireTrue(failures, "wealth.vault.antivirus.required");
        require(failures, "wealth.vault.antivirus.host");

        requireTrue(failures, "app.insurance.imap.enabled");
        require(failures, "app.insurance.imap.host");
        require(failures, "app.insurance.imap.username");
        require(failures, "app.insurance.imap.password");
        require(failures, "app.insurance.mail.from");
        require(failures, "app.insurance.mail.reply-to");

        requireExplicit(failures, "affiliate.commission-rate");
        requireExplicit(failures, "affiliate.minimum-payout");
        requireExplicit(failures, "affiliate.commission-hold-days");

        if (!hasText("payment.paystack.secret-key")
                && !hasText("payment.flutterwave.webhook-secret")
                && !hasText("payment.mpesa.callback-token")) {
            failures.add("one verified payment callback secret");
        }
        if (Boolean.parseBoolean(value("payment.paystack.enabled", "false"))) {
            require(failures, "payment.paystack.secret-key");
            requireHttps(failures, "payment.paystack.callback-url");
        }

        return new Assessment(failures.isEmpty(), List.copyOf(failures));
    }

    private void require(List<String> failures, String key) {
        if (!hasText(key)) failures.add(key);
    }

    private void requireExplicit(List<String> failures, String key) {
        if (!environment.containsProperty(key) || !hasText(key)) failures.add(key + " (must be explicit)");
    }

    private void requireTrue(List<String> failures, String key) {
        if (!Boolean.parseBoolean(value(key, "false"))) failures.add(key + "=true");
    }

    private void requireHttps(List<String> failures, String key) {
        String configured = environment.getProperty(key);
        if (!isHttps(configured)) failures.add(key + " (HTTPS required)");
    }

    private void rejectLocal(List<String> failures, String key) {
        String configured = environment.getProperty(key);
        if (!hasText(key) || isLocal(configured)) failures.add(key + " (non-local endpoint required)");
    }

    private void requireHttpsOrigins(List<String> failures, String key) {
        String configured = environment.getProperty(key);
        if (configured == null || configured.isBlank()) {
            failures.add(key + " (HTTPS origins required)");
            return;
        }
        for (String origin : configured.split(",")) {
            if (!isHttps(origin.trim()) || "*".equals(origin.trim())) {
                failures.add(key + " (HTTPS origins required)");
                return;
            }
        }
    }

    private boolean isHttps(String configured) {
        try {
            URI uri = URI.create(configured == null ? "" : configured.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !isLocal(configured);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean isLocal(String configured) {
        String normalized = configured == null ? "" : configured.toLowerCase(Locale.ROOT);
        return normalized.contains("localhost") || normalized.contains("127.0.0.1") || normalized.contains("0.0.0.0");
    }

    private boolean hasText(String key) {
        String configured = environment.getProperty(key);
        return configured != null && !configured.isBlank();
    }

    private String value(String key, String fallback) {
        return environment.getProperty(key, fallback);
    }

    private void boundedInt(String key, int fallback, int minimum, int maximum) {
        int configured = environment.getProperty(key, Integer.class, fallback);
        if (configured < minimum || configured > maximum) {
            throw new IllegalStateException(key + " must be between " + minimum + " and " + maximum);
        }
    }

    private void boundedDuration(String key, String fallback, Duration minimum, Duration maximum) {
        Duration configured = Duration.parse(value(key, fallback));
        if (configured.compareTo(minimum) < 0 || configured.compareTo(maximum) > 0) {
            throw new IllegalStateException(key + " must be between " + minimum + " and " + maximum);
        }
    }

    public record Assessment(boolean ready, List<String> missingOrUnsafeConfiguration) {}
}
