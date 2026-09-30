package org.pms.silverocean.service.auth;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.database.pms.HelpRateLimitRepo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Shared, database-backed protection for unauthenticated endpoints. Subjects are
 * hashed before persistence so email addresses, IP addresses and tokens never
 * become part of the rate-limit audit table.
 */
@Service
@RequiredArgsConstructor
public class PublicEndpointRateLimiter {
    private final HelpRateLimitRepo repository;

    @Transactional
    public void check(String scope, String subject, int limit, String message) {
        String normalizedSubject = StringUtils.defaultString(subject, "unknown")
                .trim().toLowerCase(Locale.ROOT);
        String subjectHash = PMSUtils.hashToken(scope + ":" + normalizedSubject);
        LocalDateTime window = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        repository.increment(subjectHash, window);
        Integer count = repository.requestCount(subjectHash, window);
        if (count != null && count > limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }
}
