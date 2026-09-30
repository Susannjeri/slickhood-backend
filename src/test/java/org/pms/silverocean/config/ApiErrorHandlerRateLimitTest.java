package org.pms.silverocean.config;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.I18NService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class ApiErrorHandlerRateLimitTest {
    @Test
    void returnsStandardReadablePayloadAndRetryHintForRateLimits() {
        ApiErrorHandler handler = new ApiErrorHandler(mock(I18NService.class));

        var response = handler.handleResponseStatusException(new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "Please wait a minute and try again."));

        assertEquals(429, response.getStatusCode().value());
        assertEquals("60", response.getHeaders().getFirst("Retry-After"));
        assertFalse(response.getBody().isSuccess());
        assertEquals("RATE_LIMITED", response.getBody().getCode());
        assertEquals("Please wait a minute and try again.", response.getBody().getDescription());
    }
}
