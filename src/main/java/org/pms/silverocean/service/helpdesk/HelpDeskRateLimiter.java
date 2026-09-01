package org.pms.silverocean.service.helpdesk;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.database.pms.HelpRateLimitRepo;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class HelpDeskRateLimiter {
    private final HelpRateLimitRepo repository;

    @Transactional
    public void check(String subjectHash, int limit) {
        LocalDateTime window = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        repository.increment(subjectHash, window);
        Integer count = repository.requestCount(subjectHash, window);
        if (count != null && count > limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait before sending more help messages.");
        }
    }

    @Scheduled(cron = "${helpdesk.rate-limit-cleanup-cron:0 20 3 * * *}")
    @Transactional
    public void cleanup() {
        repository.deleteExpired(LocalDateTime.now().minusDays(2));
    }
}
