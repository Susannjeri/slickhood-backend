package org.pms.silverocean.service.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.EmailPasswordDTO;
import org.pms.silverocean.controller.wrappers.LoginResponseDTO;
import org.pms.silverocean.controller.wrappers.RegistrationDTO;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.affiliate.AffiliateService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.RoleService;
import org.pms.silverocean.service.auth.totp.TotpService;
import org.pms.silverocean.service.auth.totp.TotpServiceFactory;
import org.pms.silverocean.service.auth.totp.impl.OtpType;
import org.pms.silverocean.service.geolocation.GeoLocationService;
import org.pms.silverocean.service.users.ProfileType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class UserAuthenticationServiceTest {
    @Mock UserDao userDao;
    @Mock PasswordEncoder passwordEncoder;
    @Mock GeoLocationService geoLocationService;
    @Mock RoleService roleService;
    @Mock GoogleAuthService googleAuthService;
    @Mock I18NService i18NService;
    @Mock LoginAttemptService loginAttemptService;
    @Mock JwtService jwtService;
    @Mock TotpServiceFactory totpServiceFactory;
    @Mock TotpService totpService;
    @Mock AffiliateService affiliateService;
    @Mock RefreshTokenReplayService refreshTokenReplayService;

    private UserAuthenticationService service;

    @BeforeEach
    void setUp() {
        when(totpServiceFactory.getService(OtpType.EMAIL)).thenReturn(Optional.of(totpService));
        service = new UserAuthenticationService(userDao, passwordEncoder, geoLocationService, roleService,
                googleAuthService, i18NService, loginAttemptService, jwtService, totpServiceFactory, affiliateService,
                refreshTokenReplayService);
    }

    @Test
    void unknownUserLoginCountsAsFailedAttempt() {
        EmailPasswordDTO request = new EmailPasswordDTO();
        request.setEmail("missing@example.com");
        request.setPassword("Password1!");
        when(userDao.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        var response = service.login(request);

        assertFalse(response.isSuccess());
        assertEquals(ResponseCode.LOGIN_FAILURE_INVALID_USER.getCode(), response.getCode());
        verify(loginAttemptService).loginFailed("missing@example.com");
    }

    @Test
    void verifiedRecoveryNormalizesEmailAndAcceptsInvitationForExistingTenant() {
        Users tenant = Users.builder().email("tenant@example.com").build();
        when(userDao.findByEmail("tenant@example.com")).thenReturn(Optional.of(tenant));

        service.acceptInvitationForVerifiedUser("  Tenant@Example.COM ", "tenant-invite-token");

        verify(roleService).assignRoleFromInvite("tenant-invite-token", tenant);
    }

    @Test
    void loginNormalizesEmailBeforeRateLimitAndLookup() {
        EmailPasswordDTO request = new EmailPasswordDTO();
        request.setEmail("  Owner@Example.COM ");
        request.setPassword("Password1!");
        when(userDao.findByEmail("owner@example.com")).thenReturn(Optional.empty());

        var response = service.login(request);

        assertFalse(response.isSuccess());
        assertEquals("owner@example.com", request.getEmail());
        verify(loginAttemptService).assertLoginAllowed("owner@example.com");
        verify(loginAttemptService).loginFailed("owner@example.com");
    }

    @Test
    void activeUserLoginIssuesJwtAndPersistsRefreshSession() {
        EmailPasswordDTO request = new EmailPasswordDTO();
        request.setEmail("owner@example.com");
        request.setPassword("Password1!");
        Users active = Users.builder().email("owner@example.com").password("encoded").build();
        active.setActive(true);
        when(userDao.findByEmail("owner@example.com")).thenReturn(Optional.of(active));
        when(passwordEncoder.matches("Password1!", "encoded")).thenReturn(true);
        when(jwtService.generateJWT("owner@example.com")).thenReturn("signed-jwt");

        var response = service.login(request);

        assertTrue(response.isSuccess());
        assertEquals(ResponseCode.LOGIN_SUCCESS.getCode(), response.getCode());
        verify(userDao).save(active);
        verify(loginAttemptService).loginSuccess("owner@example.com");
        assertTrue(active.getLastLogin() != null);
        assertTrue(active.getRefreshToken() != null && !active.getRefreshToken().isBlank());
        LoginResponseDTO session = (LoginResponseDTO) response.getData().getFirst();
        assertTrue(session.refreshRequestId() != null && !session.refreshRequestId().isBlank());
        assertEquals(org.pms.silverocean.common.PMSUtils.hashToken(session.refreshRequestId()),
                active.getRefreshTokenRequestHash());
        verifyNoInteractions(geoLocationService);
    }

    @Test
    void inactiveAccountCannotRotateARefreshToken() {
        Users inactive = Users.builder().email("inactive@example.com").build();
        inactive.setActive(false);
        when(userDao.findByCurrentOrReplayRefreshTokenForUpdate(anyString())).thenReturn(Optional.of(inactive));

        var response = service.loginByRefreshToken("refresh-token");

        assertFalse(response.isSuccess());
        assertEquals(ResponseCode.LOGIN_FAILURE_INACTIVE_USER.getCode(), response.getCode());
        verify(userDao, never()).save(inactive);
    }

    @Test
    void replacedOrExpiredRefreshTokenReturnsActionableMessage() {
        when(userDao.findByCurrentOrReplayRefreshTokenForUpdate(anyString())).thenReturn(Optional.empty());
        when(i18NService.getLocalizedMessage(ResponseCode.SESSION_REPLACED_OR_EXPIRED))
                .thenReturn("This session is no longer active. It may have been replaced by a newer sign-in. Please sign in again.");

        var response = service.loginByRefreshToken("old-refresh-token");

        assertFalse(response.isSuccess());
        assertEquals(ResponseCode.SESSION_REPLACED_OR_EXPIRED.getCode(), response.getCode());
        assertEquals("This session is no longer active. It may have been replaced by a newer sign-in. Please sign in again.",
                response.getDescription());
    }

    @Test
    void refreshRotationMovesTheCurrentPairIntoTheBoundedReplaySlot() {
        String refreshToken = "current-refresh-token";
        String requestId = "15dbef77-2325-4b15-b35b-af05312a6879";
        var replacement = new RefreshTokenReplayService.Replacement(
                "replacement-refresh-token", "61a9b4b6-a44e-4647-bc7f-c76076a74160");
        Users active = Users.builder().email("owner@example.com").build();
        active.setActive(true);
        active.setRefreshToken(org.pms.silverocean.common.PMSUtils.hashToken(refreshToken));
        active.setRefreshTokenRequestHash(org.pms.silverocean.common.PMSUtils.hashToken(requestId));
        when(userDao.findByCurrentOrReplayRefreshTokenForUpdate(active.getRefreshToken()))
                .thenReturn(Optional.of(active));
        when(refreshTokenReplayService.issue(refreshToken, requestId)).thenReturn(replacement);
        when(jwtService.generateJWT(active.getEmail())).thenReturn("access-jwt");
        ZonedDateTime before = ZonedDateTime.now();

        var response = service.loginByRefreshToken(refreshToken, requestId);

        assertTrue(response.isSuccess());
        LoginResponseDTO session = (LoginResponseDTO) response.getData().getFirst();
        assertEquals(replacement.refreshToken(), session.refreshToken());
        assertEquals(replacement.requestId(), session.refreshRequestId());
        assertEquals(org.pms.silverocean.common.PMSUtils.hashToken(session.refreshToken()), active.getRefreshToken());
        assertEquals(org.pms.silverocean.common.PMSUtils.hashToken(session.refreshRequestId()),
                active.getRefreshTokenRequestHash());
        assertEquals(org.pms.silverocean.common.PMSUtils.hashToken(refreshToken), active.getRefreshTokenReplayHash());
        assertEquals(org.pms.silverocean.common.PMSUtils.hashToken(requestId), active.getRefreshTokenReplayRequestHash());
        assertTrue(active.getRefreshTokenReplayExpiresAt().isAfter(before.plusSeconds(59)));
        assertTrue(active.getRefreshTokenReplayExpiresAt().isBefore(ZonedDateTime.now().plusSeconds(61)));
        verify(userDao).save(active);
    }

    @Test
    void exactRetryReturnsTheCommittedReplacementWithoutRotatingAgain() {
        String consumedToken = "consumed-refresh-token";
        String replacementToken = "already-committed-replacement";
        String requestId = "b5d307e5-434c-4bdf-91cf-c83e8f137601";
        String replacementRequestId = "ec1f64df-5b88-4a98-b84d-04dcf31f99b2";
        var replacement = new RefreshTokenReplayService.Replacement(replacementToken, replacementRequestId);
        Users active = replayableUser(consumedToken, replacement, requestId, ZonedDateTime.now().plusMinutes(1));
        when(userDao.findByCurrentOrReplayRefreshTokenForUpdate(
                org.pms.silverocean.common.PMSUtils.hashToken(consumedToken))).thenReturn(Optional.of(active));
        when(refreshTokenReplayService.recover(consumedToken, requestId, active.getRefreshToken(),
                active.getRefreshTokenRequestHash())).thenReturn(Optional.of(replacement));
        when(jwtService.generateJWT(active.getEmail())).thenReturn("retry-access-jwt");

        var response = service.loginByRefreshToken(consumedToken, requestId);

        assertTrue(response.isSuccess());
        LoginResponseDTO session = (LoginResponseDTO) response.getData().getFirst();
        assertEquals(replacementToken, session.refreshToken());
        assertEquals(replacementRequestId, session.refreshRequestId());
        assertEquals("retry-access-jwt", session.jwt());
        verify(userDao, never()).save(any());
    }

    @Test
    void consumedTokenWithDifferentRequestIdIsRejectedAsReplay() {
        String consumedToken = "consumed-refresh-token";
        String originalRequestId = "0766627b-8582-4f3f-97c1-b29f46bb9551";
        var replacement = new RefreshTokenReplayService.Replacement(
                "replacement", "896c86e8-429a-40f9-8bb1-d9692e4faebd");
        Users active = replayableUser(consumedToken, replacement, originalRequestId,
                ZonedDateTime.now().plusMinutes(1));
        when(userDao.findByCurrentOrReplayRefreshTokenForUpdate(
                org.pms.silverocean.common.PMSUtils.hashToken(consumedToken))).thenReturn(Optional.of(active));

        var response = service.loginByRefreshToken(consumedToken,
                "fb4de9a9-3566-4993-8bcf-a8fae65df719");

        assertFalse(response.isSuccess());
        assertEquals(ResponseCode.SESSION_REPLACED_OR_EXPIRED.getCode(), response.getCode());
        verifyNoInteractions(refreshTokenReplayService);
        verifyNoInteractions(jwtService);
    }

    @Test
    void exactRetryIsRejectedAfterTheBoundedWindow() {
        String consumedToken = "consumed-refresh-token";
        String requestId = "182fa655-5960-4ec8-8e28-45d62ebbf271";
        var replacement = new RefreshTokenReplayService.Replacement(
                "replacement", "3f4a45c6-6383-415a-b42e-5d352c6a366e");
        Users active = replayableUser(consumedToken, replacement, requestId, ZonedDateTime.now().minusNanos(1));
        when(userDao.findByCurrentOrReplayRefreshTokenForUpdate(
                org.pms.silverocean.common.PMSUtils.hashToken(consumedToken))).thenReturn(Optional.of(active));

        var response = service.loginByRefreshToken(consumedToken, requestId);

        assertFalse(response.isSuccess());
        assertEquals(ResponseCode.SESSION_REPLACED_OR_EXPIRED.getCode(), response.getCode());
        assertNull(active.getRefreshTokenReplayHash());
        assertNull(active.getRefreshTokenReplayRequestHash());
        assertNull(active.getRefreshTokenReplayExpiresAt());
        verify(userDao).save(active);
        verifyNoInteractions(refreshTokenReplayService);
        verifyNoInteractions(jwtService);
    }

    @Test
    void derivedRetryPairMustStillMatchTheCurrentSessionState() {
        String consumedToken = "consumed-refresh-token";
        String requestId = "07df9685-30ab-4489-91b6-440c7653e416";
        var expected = new RefreshTokenReplayService.Replacement(
                "expected-replacement", "557094fc-3c8d-4de8-b39c-26006b31ec30");
        Users active = replayableUser(consumedToken, expected, requestId, ZonedDateTime.now().plusMinutes(1));
        when(userDao.findByCurrentOrReplayRefreshTokenForUpdate(
                org.pms.silverocean.common.PMSUtils.hashToken(consumedToken))).thenReturn(Optional.of(active));
        when(refreshTokenReplayService.recover(consumedToken, requestId, active.getRefreshToken(),
                active.getRefreshTokenRequestHash())).thenReturn(Optional.empty());

        var response = service.loginByRefreshToken(consumedToken, requestId);

        assertFalse(response.isSuccess());
        assertEquals(ResponseCode.SESSION_REPLACED_OR_EXPIRED.getCode(), response.getCode());
        verifyNoInteractions(jwtService);
    }

    @Test
    void legacyRefreshStillRotatesButDoesNotLeaveReplayableState() {
        String refreshToken = "legacy-refresh-token";
        Users active = Users.builder().email("owner@example.com").build();
        active.setActive(true);
        active.setRefreshToken(org.pms.silverocean.common.PMSUtils.hashToken(refreshToken));
        active.setRefreshTokenReplayHash(org.pms.silverocean.common.PMSUtils.hashToken("older-token"));
        active.setRefreshTokenReplayRequestHash(org.pms.silverocean.common.PMSUtils.hashToken(
                "ce995e8e-57bb-4019-85cd-e54abdd37026"));
        active.setRefreshTokenReplayExpiresAt(ZonedDateTime.now().plusMinutes(1));
        when(userDao.findByCurrentOrReplayRefreshTokenForUpdate(anyString()))
                .thenReturn(Optional.of(active));
        when(jwtService.generateJWT(active.getEmail())).thenReturn("access-jwt");

        var response = service.loginByRefreshToken(refreshToken);

        assertTrue(response.isSuccess());
        LoginResponseDTO session = (LoginResponseDTO) response.getData().getFirst();
        assertNotEquals(refreshToken, session.refreshToken());
        assertNull(session.refreshRequestId());
        assertNull(active.getRefreshTokenRequestHash());
        assertNull(active.getRefreshTokenReplayHash());
        assertNull(active.getRefreshTokenReplayRequestHash());
        assertNull(active.getRefreshTokenReplayExpiresAt());

        var nextResponse = service.loginByRefreshToken(session.refreshToken());

        assertTrue(nextResponse.isSuccess());
        LoginResponseDTO nextSession = (LoginResponseDTO) nextResponse.getData().getFirst();
        assertNotEquals(session.refreshToken(), nextSession.refreshToken());
        assertNull(nextSession.refreshRequestId());
        assertNull(active.getRefreshTokenRequestHash());
        verifyNoInteractions(refreshTokenReplayService);
    }

    @Test
    void oldClientCanDowngradeItsCurrentPairedTokenDuringRollingDeployment() {
        String refreshToken = "paired-token-issued-at-login";
        Users active = Users.builder().email("owner@example.com").build();
        active.setActive(true);
        active.setRefreshToken(org.pms.silverocean.common.PMSUtils.hashToken(refreshToken));
        active.setRefreshTokenRequestHash(org.pms.silverocean.common.PMSUtils.hashToken(
                "580bf594-a9d3-470d-a92f-23eaa4522274"));
        when(userDao.findByCurrentOrReplayRefreshTokenForUpdate(active.getRefreshToken()))
                .thenReturn(Optional.of(active));
        when(jwtService.generateJWT(active.getEmail())).thenReturn("access-jwt");

        var response = service.loginByRefreshToken(refreshToken);

        assertTrue(response.isSuccess());
        LoginResponseDTO session = (LoginResponseDTO) response.getData().getFirst();
        assertNotEquals(refreshToken, session.refreshToken());
        assertNull(session.refreshRequestId());
        assertNull(active.getRefreshTokenRequestHash());
        assertNull(active.getRefreshTokenReplayHash());
        assertNull(active.getRefreshTokenReplayRequestHash());
        assertNull(active.getRefreshTokenReplayExpiresAt());
        verifyNoInteractions(refreshTokenReplayService);
    }

    @Test
    void malformedSuppliedRequestIdCannotDowngradeAPairedSession() {
        String refreshToken = "current-refresh-token";
        Users active = Users.builder().email("owner@example.com").build();
        active.setActive(true);
        active.setRefreshToken(org.pms.silverocean.common.PMSUtils.hashToken(refreshToken));
        active.setRefreshTokenRequestHash(org.pms.silverocean.common.PMSUtils.hashToken(
                "23c97a7e-b6e2-4a4f-bb61-2e61f51469ee"));
        when(userDao.findByCurrentOrReplayRefreshTokenForUpdate(active.getRefreshToken()))
                .thenReturn(Optional.of(active));

        var response = service.loginByRefreshToken(refreshToken, "                                ");

        assertFalse(response.isSuccess());
        assertEquals(ResponseCode.SESSION_REPLACED_OR_EXPIRED.getCode(), response.getCode());
        verify(userDao, never()).save(any());
        verifyNoInteractions(refreshTokenReplayService, jwtService);
    }

    @Test
    void legacySessionWithTransitionIdGetsRecoverableFirstRotation() {
        String refreshToken = "legacy-refresh-token";
        String transitionRequestId = "0a110a37-d73f-4319-bb8d-f80d4905278a";
        var replacement = new RefreshTokenReplayService.Replacement(
                "paired-refresh-token", "80554805-d597-43ac-9a9e-8365be183c87");
        Users active = Users.builder().email("owner@example.com").build();
        active.setActive(true);
        active.setRefreshToken(org.pms.silverocean.common.PMSUtils.hashToken(refreshToken));
        when(userDao.findByCurrentOrReplayRefreshTokenForUpdate(active.getRefreshToken()))
                .thenReturn(Optional.of(active));
        when(refreshTokenReplayService.issue(refreshToken, transitionRequestId)).thenReturn(replacement);
        when(jwtService.generateJWT(active.getEmail())).thenReturn("access-jwt");

        var response = service.loginByRefreshToken(refreshToken, transitionRequestId);

        assertTrue(response.isSuccess());
        assertEquals(org.pms.silverocean.common.PMSUtils.hashToken(refreshToken),
                active.getRefreshTokenReplayHash());
        assertEquals(org.pms.silverocean.common.PMSUtils.hashToken(transitionRequestId),
                active.getRefreshTokenReplayRequestHash());
        assertEquals(org.pms.silverocean.common.PMSUtils.hashToken(replacement.refreshToken()),
                active.getRefreshToken());
        assertEquals(org.pms.silverocean.common.PMSUtils.hashToken(replacement.requestId()),
                active.getRefreshTokenRequestHash());
    }

    @Test
    void currentPairedSessionRejectsARefreshWithTheWrongRequestId() {
        String refreshToken = "current-refresh-token";
        String wrongRequestId = "559f9666-74f8-4c74-b5d7-5e511592c33c";
        Users active = Users.builder().email("owner@example.com").build();
        active.setActive(true);
        active.setRefreshToken(org.pms.silverocean.common.PMSUtils.hashToken(refreshToken));
        active.setRefreshTokenRequestHash(org.pms.silverocean.common.PMSUtils.hashToken(
                "23c97a7e-b6e2-4a4f-bb61-2e61f51469ee"));
        when(userDao.findByCurrentOrReplayRefreshTokenForUpdate(active.getRefreshToken()))
                .thenReturn(Optional.of(active));

        var response = service.loginByRefreshToken(refreshToken, wrongRequestId);

        assertFalse(response.isSuccess());
        assertEquals(ResponseCode.SESSION_REPLACED_OR_EXPIRED.getCode(), response.getCode());
        verify(userDao, never()).save(any());
        verify(refreshTokenReplayService).isLegacyTransitionRequestId(refreshToken, wrongRequestId);
        verifyNoInteractions(jwtService);
    }

    @Test
    void derivedTransitionIdRepairsAStaleCompanionHashLeftByAnOldBackend() {
        String refreshToken = "token-rotated-by-old-backend";
        String transitionRequestId = "JDjwzzlg0ufgb2vPi_O-3wzQLjW3ExlcYnUGYqw4sFU";
        var replacement = new RefreshTokenReplayService.Replacement(
                "paired-refresh-token", "f15984f0-c693-4b3f-bb11-9e4859c0a6ea");
        Users active = Users.builder().email("owner@example.com").build();
        active.setActive(true);
        active.setRefreshToken(org.pms.silverocean.common.PMSUtils.hashToken(refreshToken));
        active.setRefreshTokenRequestHash(org.pms.silverocean.common.PMSUtils.hashToken(
                "stale-request-id-from-newer-instance"));
        when(userDao.findByCurrentOrReplayRefreshTokenForUpdate(active.getRefreshToken()))
                .thenReturn(Optional.of(active));
        when(refreshTokenReplayService.isLegacyTransitionRequestId(refreshToken, transitionRequestId))
                .thenReturn(true);
        when(refreshTokenReplayService.issue(refreshToken, transitionRequestId)).thenReturn(replacement);
        when(jwtService.generateJWT(active.getEmail())).thenReturn("access-jwt");

        var response = service.loginByRefreshToken(refreshToken, transitionRequestId);

        assertTrue(response.isSuccess());
        assertEquals(org.pms.silverocean.common.PMSUtils.hashToken(refreshToken),
                active.getRefreshTokenReplayHash());
        assertEquals(org.pms.silverocean.common.PMSUtils.hashToken(transitionRequestId),
                active.getRefreshTokenReplayRequestHash());
        assertEquals(org.pms.silverocean.common.PMSUtils.hashToken(replacement.refreshToken()),
                active.getRefreshToken());
        assertEquals(org.pms.silverocean.common.PMSUtils.hashToken(replacement.requestId()),
                active.getRefreshTokenRequestHash());
    }

    @Test
    void freshLoginReplacesBothCurrentSecretsAndClearsReplayState() {
        EmailPasswordDTO request = new EmailPasswordDTO();
        request.setEmail("owner@example.com");
        request.setPassword("Password1!");
        Users active = replayableUser("consumed-token",
                new RefreshTokenReplayService.Replacement("old-current", "old-current-request-id-value"),
                "69db74dc-39c7-4331-b6d9-f7ee1595de2b", ZonedDateTime.now().plusMinutes(1));
        active.setPassword("encoded");
        when(userDao.findByEmail("owner@example.com")).thenReturn(Optional.of(active));
        when(passwordEncoder.matches("Password1!", "encoded")).thenReturn(true);
        when(jwtService.generateJWT("owner@example.com")).thenReturn("signed-jwt");

        var response = service.login(request);

        assertTrue(response.isSuccess());
        LoginResponseDTO session = (LoginResponseDTO) response.getData().getFirst();
        assertEquals(org.pms.silverocean.common.PMSUtils.hashToken(session.refreshToken()), active.getRefreshToken());
        assertEquals(org.pms.silverocean.common.PMSUtils.hashToken(session.refreshRequestId()),
                active.getRefreshTokenRequestHash());
        assertNull(active.getRefreshTokenReplayHash());
        assertNull(active.getRefreshTokenReplayRequestHash());
        assertNull(active.getRefreshTokenReplayExpiresAt());
    }

    @Test
    void googleLoginAlsoReplacesBothSecretsAndClearsReplayState() {
        Users googleIdentity = Users.builder().email("owner@example.com").build();
        Users existing = replayableUser("consumed-token",
                new RefreshTokenReplayService.Replacement("old-current", "old-current-request-id-value"),
                "eefab2b4-ec7b-426a-9770-abf7af18e3e3", ZonedDateTime.now().plusMinutes(1));
        when(googleAuthService.verifyIdToken("google-token")).thenReturn(Optional.of(googleIdentity));
        when(userDao.findByEmail("owner@example.com")).thenReturn(Optional.of(existing));
        when(jwtService.generateJWT("owner@example.com")).thenReturn("signed-jwt");

        var response = service.googleLogin("google-token", null, null, null, null,
                null, null, "127.0.0.1");

        assertTrue(response.isSuccess());
        LoginResponseDTO session = (LoginResponseDTO) response.getData().getFirst();
        assertEquals(org.pms.silverocean.common.PMSUtils.hashToken(session.refreshToken()), existing.getRefreshToken());
        assertEquals(org.pms.silverocean.common.PMSUtils.hashToken(session.refreshRequestId()),
                existing.getRefreshTokenRequestHash());
        assertNull(existing.getRefreshTokenReplayHash());
        assertNull(existing.getRefreshTokenReplayRequestHash());
        assertNull(existing.getRefreshTokenReplayExpiresAt());
    }

    @Test
    void pendingRegistrationWithMatchingPasswordResendsVerificationCode() {
        Users pending = Users.builder().email("pending@example.com").password("encoded").build();
        pending.setActive(false);
        pending.setEmailVerified(false);
        RegistrationDTO request = registration("pending@example.com", "Password1!");
        when(userDao.findByEmail("pending@example.com")).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("Password1!", "encoded")).thenReturn(true);
        when(totpService.generateOTPCode("pending@example.com")).thenReturn("Use OTP sent to email");

        var response = service.register(request, "127.0.0.1");

        assertTrue(response.isSuccess());
        assertEquals(ResponseCode.EMAIL_OTP_GENERATED.getCode(), response.getCode());
        verify(totpService).generateOTPCode("pending@example.com");
    }

    @Test
    void pendingRegistrationPreservesAndAppliesBoundInvitationBeforeOtpRetry() {
        Users pending = Users.builder().email("pending@example.com").password("encoded").build();
        pending.setActive(false);
        pending.setEmailVerified(false);
        RegistrationDTO request = registration("pending@example.com", "Password1!");
        request.setToken("bound-invite");
        when(userDao.findByEmail("pending@example.com")).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("Password1!", "encoded")).thenReturn(true);
        when(totpService.generateOTPCode("pending@example.com")).thenReturn("Use OTP sent to email");

        var response = service.register(request, "127.0.0.1");

        assertTrue(response.isSuccess());
        verify(roleService).assignRoleFromInvite("bound-invite", pending);
        verify(totpService).generateOTPCode("pending@example.com");
    }

    @Test
    void pendingRegistrationDoesNotReconsumeItsAlreadyAppliedInvitation() {
        Users pending = Users.builder().email("pending@example.com").password("encoded").inviteId(91L).build();
        pending.setActive(false);
        pending.setEmailVerified(false);
        RegistrationDTO request = registration("pending@example.com", "Password1!");
        request.setToken("already-consumed-invite");
        when(userDao.findByEmail("pending@example.com")).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("Password1!", "encoded")).thenReturn(true);
        when(totpService.generateOTPCode("pending@example.com")).thenReturn("Use OTP sent to email");

        var response = service.register(request, "127.0.0.1");

        assertTrue(response.isSuccess());
        verify(roleService, never()).assignRoleFromInvite(anyString(), org.mockito.ArgumentMatchers.any(Users.class));
        verify(totpService).generateOTPCode("pending@example.com");
    }

    @Test
    void pendingAccountLoginAppliesBoundInvitationBeforeOtpHandoff() {
        Users pending = Users.builder().email("pending@example.com").password("encoded").build();
        pending.setActive(false);
        pending.setEmailVerified(false);
        EmailPasswordDTO request = new EmailPasswordDTO();
        request.setEmail("pending@example.com");
        request.setPassword("Password1!");
        request.setToken("bound-invite");
        when(userDao.findByEmail("pending@example.com")).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("Password1!", "encoded")).thenReturn(true);
        when(totpService.generateOTPCode("pending@example.com")).thenReturn("Use OTP sent to email");

        var response = service.login(request);

        assertTrue(response.isSuccess());
        assertEquals(ResponseCode.EMAIL_OTP_GENERATED.getCode(), response.getCode());
        verify(roleService).assignRoleFromInvite("bound-invite", pending);
        verify(totpService).generateOTPCode("pending@example.com");
    }

    @Test
    void pendingRegistrationWithWrongPasswordRemainsDuplicate() {
        Users pending = Users.builder().email("pending@example.com").password("encoded").build();
        pending.setActive(false);
        pending.setEmailVerified(false);
        RegistrationDTO request = registration("pending@example.com", "WrongPass1!");
        when(userDao.findByEmail("pending@example.com")).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("WrongPass1!", "encoded")).thenReturn(false);

        var response = service.register(request, "127.0.0.1");

        assertFalse(response.isSuccess());
        assertEquals(ResponseCode.DUPLICATE_USER_DETAILS.getCode(), response.getCode());
        verify(totpService, never()).generateOTPCode(anyString());
    }

    @Test
    void pendingRegistrationDoesNotClaimSuccessWhenOtpCannotBeSent() {
        Users pending = Users.builder().email("pending@example.com").password("encoded").build();
        pending.setActive(false);
        pending.setEmailVerified(false);
        RegistrationDTO request = registration("pending@example.com", "Password1!");
        when(userDao.findByEmail("pending@example.com")).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("Password1!", "encoded")).thenReturn(true);
        when(totpService.generateOTPCode("pending@example.com")).thenThrow(new RuntimeException("mail unavailable"));

        var response = service.register(request, "127.0.0.1");

        assertFalse(response.isSuccess());
        assertEquals(ResponseCode.LOGIN_FAILURE_VERIFICATION_REQUIRED.getCode(), response.getCode());
    }

    @Test
    void geolocationOutageDoesNotBlockARealRegistration() {
        RegistrationDTO request = registration("  New.User@Example.COM ", "Password1!");
        when(userDao.findByEmail("new.user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password1!")).thenReturn("encoded");
        when(geoLocationService.getLocation("203.0.113.8")).thenThrow(new RuntimeException("location provider unavailable"));
        when(totpService.generateOTPCode("new.user@example.com")).thenReturn("Use OTP sent to email");

        var response = service.register(request, "203.0.113.8");

        assertTrue(response.isSuccess());
        assertEquals(ResponseCode.EMAIL_OTP_GENERATED.getCode(), response.getCode());
        assertEquals("new.user@example.com", request.getEmail());
        verify(roleService).saveUserAndAssignRoleOnRegistration(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(Users.class));
        verify(totpService).generateOTPCode("new.user@example.com");
    }

    @Test
    void companyRegistrationPersistsLegalOrganizationAndRepresentativeSeparately() {
        RegistrationDTO request = registration("owner@company.example", "Password1!");
        request.setFullName("Jane Authorized Representative");
        request.setProfileType(ProfileType.COMPANY);
        request.setOrganizationName("  Mitero Hope SHG  ");
        when(userDao.findByEmail("owner@company.example")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password1!")).thenReturn("encoded");
        when(totpService.generateOTPCode("owner@company.example")).thenReturn("OTP sent");

        var response = service.register(request, "127.0.0.1");

        assertTrue(response.isSuccess());
        ArgumentCaptor<Users> user = ArgumentCaptor.forClass(Users.class);
        verify(roleService).saveUserAndAssignRoleOnRegistration(org.mockito.ArgumentMatchers.eq(1L), user.capture());
        assertEquals(ProfileType.COMPANY.name(), user.getValue().getProfileType());
        assertEquals("Mitero Hope SHG", user.getValue().getOrganizationName());
        assertEquals("Jane Authorized Representative", user.getValue().getFullName());
    }

    @Test
    void companyRegistrationWithoutOrganizationNameIsRejectedBeforePersistence() {
        RegistrationDTO request = registration("owner@company.example", "Password1!");
        request.setProfileType(ProfileType.COMPANY);
        request.setOrganizationName("   ");

        var response = service.register(request, "127.0.0.1");

        assertFalse(response.isSuccess());
        assertEquals(ResponseCode.REGISTRATION_FAILED.getCode(), response.getCode());
        verify(userDao, never()).save(org.mockito.ArgumentMatchers.any());
        verify(roleService, never()).saveUserAndAssignRoleOnRegistration(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registrationDefaultsToIndividualForBackwardCompatibleClients() {
        RegistrationDTO request = registration("person@example.com", "Password1!");
        request.setProfileType(null);
        when(userDao.findByEmail("person@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password1!")).thenReturn("encoded");
        when(totpService.generateOTPCode("person@example.com")).thenReturn("OTP sent");

        assertTrue(service.register(request, "127.0.0.1").isSuccess());
        ArgumentCaptor<Users> user = ArgumentCaptor.forClass(Users.class);
        verify(roleService).saveUserAndAssignRoleOnRegistration(org.mockito.ArgumentMatchers.eq(1L), user.capture());
        assertEquals(ProfileType.INDIVIDUAL.name(), user.getValue().getProfileType());
        assertEquals(null, user.getValue().getOrganizationName());
    }

    @Test
    void emailBoundInvitationRecoversWhenBrowserLosesTheToken() {
        RegistrationDTO request = registration("invited@example.com", "Password1!");
        request.setRoleId(null);
        when(roleService.activeInviteTokenForRecipient("invited@example.com"))
                .thenReturn(Optional.of("recovered-invite"));
        when(userDao.findByEmail("invited@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password1!")).thenReturn("encoded");
        when(totpService.generateOTPCode("invited@example.com")).thenReturn("OTP sent");

        var response = service.register(request, "127.0.0.1");

        assertTrue(response.isSuccess());
        assertEquals("recovered-invite", request.getToken());
        verify(roleService).saveUserAndAssignRoleFromInvite(
                org.mockito.ArgumentMatchers.eq("recovered-invite"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(Users.class));
    }

    @Test
    void registrationWithoutRoleOrMatchingInvitationStillFailsClosed() {
        RegistrationDTO request = registration("unknown@example.com", "Password1!");
        request.setRoleId(null);
        when(roleService.activeInviteTokenForRecipient("unknown@example.com"))
                .thenReturn(Optional.empty());
        when(userDao.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        var response = service.register(request, "127.0.0.1");

        assertFalse(response.isSuccess());
        assertEquals(ResponseCode.REGISTRATION_FAILED.getCode(), response.getCode());
        verify(roleService, never()).saveUserAndAssignRoleFromInvite(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(Users.class));
    }

    @Test
    void passwordResetPersistsTheNewPasswordBeforeReturning() {
        Users user = Users.builder().email("owner@example.com").password("old-hash").build();
        when(userDao.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPassword1!" )).thenReturn("new-hash");

        service.updatePassword("  OWNER@EXAMPLE.COM ", "NewPassword1!");

        assertEquals("new-hash", user.getPassword());
        verify(userDao).save(user);
    }

    private Users replayableUser(String consumedToken, RefreshTokenReplayService.Replacement replacement,
                                 String requestId, ZonedDateTime expiresAt) {
        Users user = Users.builder().email("owner@example.com").build();
        user.setActive(true);
        user.setRefreshToken(org.pms.silverocean.common.PMSUtils.hashToken(replacement.refreshToken()));
        user.setRefreshTokenRequestHash(org.pms.silverocean.common.PMSUtils.hashToken(replacement.requestId()));
        user.setRefreshTokenReplayHash(org.pms.silverocean.common.PMSUtils.hashToken(consumedToken));
        user.setRefreshTokenReplayRequestHash(org.pms.silverocean.common.PMSUtils.hashToken(requestId));
        user.setRefreshTokenReplayExpiresAt(expiresAt);
        return user;
    }

    private RegistrationDTO registration(String email, String password) {
        RegistrationDTO request = new RegistrationDTO();
        request.setEmail(email);
        request.setPassword(password);
        request.setFullName("Pending User");
        request.setRoleId(1L);
        return request;
    }
}
