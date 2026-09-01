package org.pms.silverocean.service.helpdesk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.HelpRateLimitRepo;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HelpDeskRateLimiterTest {
    @Mock HelpRateLimitRepo repository;

    @Test
    void exceedingLimitProducesHttp429() {
        when(repository.requestCount(eq("subject"), any())).thenReturn(21);
        HelpDeskRateLimiter limiter = new HelpDeskRateLimiter(repository);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> limiter.check("subject", 20));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
    }
}
