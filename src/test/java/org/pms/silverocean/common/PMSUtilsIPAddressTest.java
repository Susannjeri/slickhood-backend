package org.pms.silverocean.common;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PMSUtilsIPAddressTest {
    @Test
    void acceptsForwardedAddressFromLoopbackProxy() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.20, 127.0.0.1");

        assertEquals("198.51.100.20", PMSUtils.getIPAddress(request));
    }

    @Test
    void acceptsForwardedAddressFromPrivateReverseProxy() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("172.26.1.67");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.21");

        assertEquals("198.51.100.21", PMSUtils.getIPAddress(request));
    }

    @Test
    void rejectsSpoofedForwardedAddressFromDirectClient() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.17");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.99");

        assertEquals("203.0.113.17", PMSUtils.getIPAddress(request));
    }
}
