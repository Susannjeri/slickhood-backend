package org.pms.silverocean.service.auth.totp.impl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Allows predictable OTPs only for explicitly configured synthetic identities.
 *
 * <p>The reserved {@code qa.slickhood.test} domain cannot overlap a real customer
 * mailbox. Configuration is fail-closed: enabling the feature with a real email,
 * malformed code, duplicate identity or no identities prevents application
 * startup. The code is deliberately absent from source, logs and API responses.</p>
 */
@Component
public class ControlledTestOtpPolicy {
    static final String RESERVED_DOMAIN = "@qa.slickhood.test";
    private final Map<String, String> codes;

    public ControlledTestOtpPolicy(
            @Value("${security.test-otp.enabled:false}") boolean enabled,
            @Value("${security.test-otp.accounts:}") String configuredAccounts) {
        this.codes = enabled ? parse(configuredAccounts) : Map.of();
    }

    public Optional<String> codeFor(String username) {
        if (StringUtils.isBlank(username)) return Optional.empty();
        return Optional.ofNullable(codes.get(username.trim().toLowerCase(Locale.ROOT)));
    }

    private Map<String, String> parse(String configuredAccounts) {
        if (StringUtils.isBlank(configuredAccounts)) {
            throw new IllegalStateException("Controlled test OTP is enabled without synthetic accounts");
        }
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String entry : configuredAccounts.split(",")) {
            String[] parts = entry.trim().split("=", 2);
            String email = parts.length == 2 ? parts[0].trim().toLowerCase(Locale.ROOT) : "";
            String code = parts.length == 2 ? parts[1].trim() : "";
            if (!email.matches("[a-z0-9._+-]+@qa\\.slickhood\\.test")) {
                throw new IllegalStateException("Controlled test OTP accounts must use the reserved qa.slickhood.test domain");
            }
            if (!code.matches("[0-9]{6}")) {
                throw new IllegalStateException("Controlled test OTP codes must contain exactly six digits");
            }
            if (parsed.putIfAbsent(email, code) != null) {
                throw new IllegalStateException("Controlled test OTP accounts must be unique");
            }
            if (parsed.size() > 20) {
                throw new IllegalStateException("Controlled test OTP is limited to 20 synthetic accounts");
            }
        }
        return Map.copyOf(parsed);
    }
}
