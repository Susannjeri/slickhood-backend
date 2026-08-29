package org.pms.silverocean.service.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.LoginperiplogsRepo;
import org.pms.silverocean.database.pms.entities.LoginPerIPLog;
import org.pms.silverocean.service.geolocation.GeoLocationService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {
    @Mock LoginperiplogsRepo logs;
    @Mock GeoLocationService geoLocation;
    @Mock HttpServletRequest request;
    LoginAttemptService service;

    @BeforeEach
    void setup() {
        service = new LoginAttemptService(logs, geoLocation, request);
        ReflectionTestUtils.setField(service, "maxiploginattempts", 10);
        ReflectionTestUtils.setField(service, "maxuserloginattempts", 4);
        ReflectionTestUtils.setField(service, "attemptWindowMinutes", 15);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    void blocksOnlyTheUsernameAndIpPairWithinTheWindow() {
        LoginPerIPLog attempts = attempts(4, new Date());
        when(logs.countRecentFailedLoginsByIP(eq("127.0.0.1"), any(Date.class))).thenReturn(4);
        when(logs.findByUsernameAndIpaddress("owner@example.test", "127.0.0.1")).thenReturn(Optional.of(attempts));

        assertThrows(UsernameNotFoundException.class,
                () -> service.assertLoginAllowed("owner@example.test"));
    }

    @Test
    void anExpiredWindowDoesNotKeepTheUserLocked() {
        LoginPerIPLog attempts = attempts(4, Date.from(Instant.now().minus(20, ChronoUnit.MINUTES)));
        when(logs.countRecentFailedLoginsByIP(eq("127.0.0.1"), any(Date.class))).thenReturn(0);
        when(logs.findByUsernameAndIpaddress("owner@example.test", "127.0.0.1")).thenReturn(Optional.of(attempts));

        assertDoesNotThrow(() -> service.assertLoginAllowed("owner@example.test"));
        assertEquals(4, service.getRemainingLoginAttempts("owner@example.test"));
    }

    @Test
    void staleCountersAreResetBeforeRecordingANewFailure() {
        LoginPerIPLog attempts = attempts(4, Date.from(Instant.now().minus(20, ChronoUnit.MINUTES)));
        when(logs.findByUsernameAndIpaddress("owner@example.test", "127.0.0.1")).thenReturn(Optional.of(attempts));

        service.loginFailed("owner@example.test");

        assertEquals(1, attempts.getAttemptsCount());
        verify(logs).save(attempts);
    }

    private LoginPerIPLog attempts(int count, Date lastAttempt) {
        LoginPerIPLog value = new LoginPerIPLog();
        value.setUsername("owner@example.test");
        value.setIpaddress("127.0.0.1");
        value.setAttemptsCount(count);
        value.setLastLoginAttempt(lastAttempt);
        return value;
    }
}
