package org.pms.silverocean.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Secret-safe release checks for the Wealth, Insurance, Affiliate, Services,
 * Soko and Help Desk production capabilities. The deployment endpoint exposes property names,
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
        boundedInt("helpdesk.ai.max-input-chars", 4_000, 500, 8_000);
        boundedInt("helpdesk.ai.max-context-messages", 12, 2, 30);
        boundedInt("helpdesk.guest-session-hours", 24, 1, 72);
        boundedInt("helpdesk.rate-limit-per-minute", 20, 5, 60);
        boundedInt("helpdesk.guest-start-limit-per-minute", 10, 2, 30);
        boundedDuration("helpdesk.ai.connect-timeout", "PT3S", Duration.ofMillis(250), Duration.ofSeconds(10));
        boundedDuration("helpdesk.ai.read-timeout", "PT20S", Duration.ofSeconds(2), Duration.ofSeconds(45));
        boundedDuration("helpdesk.sla.urgent", "PT15M", Duration.ofMinutes(5), Duration.ofHours(2));
        boundedDuration("helpdesk.sla.high", "PT1H", Duration.ofMinutes(15), Duration.ofHours(8));
        boundedDuration("helpdesk.sla.normal", "PT4H", Duration.ofMinutes(30), Duration.ofDays(1));
        boundedDuration("helpdesk.sla.low", "PT8H", Duration.ofHours(1), Duration.ofDays(2));
        boundedInt("helpdesk.sla-scan-delay-ms", 60_000, 10_000, 3_600_000);
    }

    public Assessment assess() {
        List<String> failures = new ArrayList<>();

        require(failures, "garage.s3.access.key");
        require(failures, "garage.s3.secret.key");
        require(failures, "garage.s3.region");
        require(failures, "garage.s3.bucket");
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
        requireReachable(failures, "wealth.vault.antivirus.host", "wealth.vault.antivirus.port",
                3310, "wealth.vault.antivirus");

        requireTrue(failures, "app.insurance.imap.enabled");
        require(failures, "app.insurance.imap.host");
        require(failures, "app.insurance.imap.username");
        require(failures, "app.insurance.imap.password");
        require(failures, "app.insurance.mail.from");
        require(failures, "app.insurance.mail.reply-to");

        requireDecimal(failures, "affiliate.commission-rate", new BigDecimal("25"));
        requireInteger(failures, "affiliate.eligible-payment-count", 3);

        requireTrue(failures, "helpdesk.ai.enabled");
        if (!hasText("helpdesk.ai.api-key") && !hasText("OPENAI_API_KEY")) failures.add("helpdesk.ai.api-key / OPENAI_API_KEY");
        String helpdeskBaseUrl = hasText("helpdesk.ai.base-url") ? environment.getProperty("helpdesk.ai.base-url")
                : environment.getProperty("HELPDESK_AI_BASE_URL");
        if (!isHttps(helpdeskBaseUrl)) failures.add("helpdesk.ai.base-url / HELPDESK_AI_BASE_URL (HTTPS required)");
        requireExplicit(failures, "affiliate.minimum-payout");
        requireExplicit(failures, "affiliate.commission-hold-days");

        boolean paystackEnabled = Boolean.parseBoolean(value("payment.paystack.enabled", "false"));
        boolean paystackReady = paystackEnabled && hasText("payment.paystack.secret-key");
        boolean mpesaReady = hasText("payment.mpesa.callback-token");
        if (!paystackReady && !mpesaReady) {
            failures.add("M-Pesa or enabled Paystack verified callback configuration");
        }
        if (paystackEnabled) {
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

    private void requireDecimal(List<String> failures, String key, BigDecimal required) {
        requireExplicit(failures, key);
        if (!hasText(key)) return;
        try {
            if (new BigDecimal(value(key, "")).compareTo(required) != 0) {
                failures.add(key + "=" + required.stripTrailingZeros().toPlainString() + " (required)");
            }
        } catch (NumberFormatException ignored) {
            failures.add(key + " (valid decimal required)");
        }
    }

    private void requireInteger(List<String> failures, String key, int required) {
        requireExplicit(failures, key);
        if (!hasText(key)) return;
        try {
            if (Integer.parseInt(value(key, "")) != required) {
                failures.add(key + "=" + required + " (required)");
            }
        } catch (NumberFormatException ignored) {
            failures.add(key + " (valid integer required)");
        }
    }

    private void requireReachable(List<String> failures, String hostKey, String portKey,
                                  int defaultPort, String capability) {
        if (!hasText(hostKey)) return;
        int port = environment.getProperty(portKey, Integer.class, defaultPort);
        int configuredTimeout = environment.getProperty("wealth.vault.antivirus.timeout-ms", Integer.class, 5_000);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(value(hostKey, ""), port), Math.min(configuredTimeout, 1_500));
        } catch (Exception ignored) {
            failures.add(capability + " (unreachable)");
        }
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
