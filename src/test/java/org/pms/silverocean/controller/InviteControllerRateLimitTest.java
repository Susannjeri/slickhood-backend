package org.pms.silverocean.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.auth.PublicEndpointRateLimiter;
import org.pms.silverocean.service.invites.InviteService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InviteControllerRateLimitTest {
    @Test
    void publicValidationIsLimitedByClientAndOpaqueToken() {
        InviteService service = mock(InviteService.class);
        PublicEndpointRateLimiter limiter = mock(PublicEndpointRateLimiter.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.24");
        when(service.validateToken("opaque-token")).thenReturn(new ResponseDTO(true, "OK", "Valid"));
        InviteController controller = new InviteController(service, mock(I18NService.class), limiter);

        controller.validateInviteToken(request, "opaque-token");

        verify(limiter).check("invite-inspect-ip", "203.0.113.24", 60,
                "Too many invitation checks. Please wait a minute and try again.");
        verify(limiter).check("invite-inspect-token", "opaque-token", 15,
                "This invitation has been checked too many times. Please wait a minute and try again.");
        verify(service).validateToken("opaque-token");
    }
}
