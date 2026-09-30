package org.pms.silverocean.service.auth;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.HelpRateLimitRepo;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicEndpointRateLimiterTest {
    @Test
    void hashesSubjectsAndReturnsReadableTooManyRequestsResponse() {
        HelpRateLimitRepo repository = mock(HelpRateLimitRepo.class);
        PublicEndpointRateLimiter limiter = new PublicEndpointRateLimiter(repository);
        when(repository.requestCount(any(), any())).thenReturn(4);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> limiter.check("otp-send-email", "Person@Example.com", 3, "Please wait and try again."));

        assertEquals(429, error.getStatusCode().value());
        assertEquals("Please wait and try again.", error.getReason());
        verify(repository).increment(any(), any());
        verify(repository).requestCount(any(), any());
    }

    @Test
    void permitsRequestsWithinTheConfiguredLimit() {
        HelpRateLimitRepo repository = mock(HelpRateLimitRepo.class);
        PublicEndpointRateLimiter limiter = new PublicEndpointRateLimiter(repository);
        when(repository.requestCount(any(), any())).thenReturn(3);

        limiter.check("otp-send-email", "person@example.com", 3, "Please wait.");

        verify(repository).increment(any(), any());
        verify(repository).requestCount(any(), any());
    }
}
