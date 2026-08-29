package org.pms.silverocean.service.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.database.pms.LoginperiplogsRepo;
import org.pms.silverocean.database.pms.entities.LoginPerIPLog;
import org.pms.silverocean.service.geolocation.GeoLocationResponse;
import org.pms.silverocean.service.geolocation.GeoLocationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class LoginAttemptService {
    private final LoginperiplogsRepo loginperiplogsRepo;
    private final GeoLocationService geolocationService;
    private final HttpServletRequest httpServletRequest;

    @Value("${max.ip.login.attempts:10}")
    private int maxiploginattempts;
    @Value("${max.user.login.attempts:4}")
    private int maxuserloginattempts;
    @Value("${login.attempt.window-minutes:15}")
    private int attemptWindowMinutes;

    public LoginAttemptService(LoginperiplogsRepo loginperiplogsRepo,
                               GeoLocationService geolocationService,
                               HttpServletRequest httpServletRequest) {
        this.loginperiplogsRepo = loginperiplogsRepo;
        this.geolocationService = geolocationService;
        this.httpServletRequest = httpServletRequest;
    }

    @Transactional
    public void loginSuccess(final String username) {
        loginperiplogsRepo.deleteOnSuccessfulLogin(username);
    }

    @Transactional
    public void loginFailed(final String username) {
        String ipaddress = getIPAddress();

        LoginPerIPLog loginPerIPLog = getLoginPerIPLogs(username, ipaddress);
        if (isOutsideAttemptWindow(loginPerIPLog)) {
            loginPerIPLog.setAttemptsCount(0);
            loginPerIPLog.setBlocked(false);
        }
        loginPerIPLog.setLastLoginAttempt(new Date());
        loginPerIPLog.setAttemptsCount(loginPerIPLog.getAttemptsCount() + 1);
        loginperiplogsRepo.save(loginPerIPLog);
    }

    private LoginPerIPLog getLoginPerIPLogs(String username, String ipaddress) {
        return loginperiplogsRepo.findByUsernameAndIpaddress(username, ipaddress).orElseGet(() -> {
            GeoLocationResponse location = geolocationService.getLocation(ipaddress);

            LoginPerIPLog loginPerIPLog = new LoginPerIPLog();
            loginPerIPLog.setAttemptsCount(0);
            loginPerIPLog.setUsername(username);
            loginPerIPLog.setIpaddress(ipaddress);
            loginPerIPLog.setCity(location.city());
            loginPerIPLog.setCountry(location.countryName());
            return loginPerIPLog;
        });
    }

    public int getRemainingLoginAttempts(final String username) {
        LoginPerIPLog attempts = loginperiplogsRepo.findByUsernameAndIpaddress(username, getIPAddress()).orElse(null);
        if (attempts == null || isOutsideAttemptWindow(attempts)) {
            return maxuserloginattempts;
        }
        return Math.max(0, maxuserloginattempts - attempts.getAttemptsCount());
    }

    public void assertLoginAllowed(String username) {
        String ipAddress = getIPAddress();
        Date cutoff = attemptWindowCutoff();
        if (loginperiplogsRepo.countRecentFailedLoginsByIP(ipAddress, cutoff) >= maxiploginattempts) {
            throw new UsernameNotFoundException("Too many failed logins from this IP address!");
        }
        loginperiplogsRepo.findByUsernameAndIpaddress(username, ipAddress)
                .filter(attempts -> !isOutsideAttemptWindow(attempts))
                .filter(attempts -> attempts.getAttemptsCount() >= maxuserloginattempts)
                .ifPresent(attempts -> {
                    throw new UsernameNotFoundException("Too many failed login attempts. Try again later.");
                });
    }

    private boolean isOutsideAttemptWindow(LoginPerIPLog attempts) {
        return attempts.getLastLoginAttempt() == null || attempts.getLastLoginAttempt().before(attemptWindowCutoff());
    }

    private Date attemptWindowCutoff() {
        return Date.from(Instant.now().minus(Math.max(1, attemptWindowMinutes), ChronoUnit.MINUTES));
    }

    private String getIPAddress() {
        return PMSUtils.getIPAddress(httpServletRequest);
    }
}
