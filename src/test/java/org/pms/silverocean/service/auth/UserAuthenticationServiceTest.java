package org.pms.silverocean.service.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.EmailPasswordDTO;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private UserAuthenticationService service;

    @BeforeEach
    void setUp() {
        when(totpServiceFactory.getService(OtpType.EMAIL)).thenReturn(Optional.of(totpService));
        service = new UserAuthenticationService(userDao, passwordEncoder, geoLocationService, roleService,
                googleAuthService, i18NService, loginAttemptService, jwtService, totpServiceFactory, affiliateService);
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
        verifyNoInteractions(geoLocationService);
    }

    @Test
    void inactiveAccountCannotRotateARefreshToken() {
        Users inactive = Users.builder().email("inactive@example.com").build();
        inactive.setActive(false);
        when(userDao.findByRefreshToken(anyString())).thenReturn(Optional.of(inactive));

        var response = service.loginByRefreshToken("refresh-token");

        assertFalse(response.isSuccess());
        assertEquals(ResponseCode.LOGIN_FAILURE_INACTIVE_USER.getCode(), response.getCode());
        verify(userDao, never()).save(inactive);
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

    private RegistrationDTO registration(String email, String password) {
        RegistrationDTO request = new RegistrationDTO();
        request.setEmail(email);
        request.setPassword(password);
        request.setFullName("Pending User");
        request.setRoleId(1L);
        return request;
    }
}
