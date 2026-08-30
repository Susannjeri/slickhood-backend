package org.pms.silverocean.service.auth;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.RegistrationChannel;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.EmailPasswordDTO;
import org.pms.silverocean.controller.wrappers.LoginResponseDTO;
import org.pms.silverocean.controller.wrappers.RegistrationDTO;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.affiliate.AffiliateService;
import org.pms.silverocean.service.auth.roles.RoleService;
import org.pms.silverocean.service.auth.totp.TotpService;
import org.pms.silverocean.service.auth.totp.TotpServiceFactory;
import org.pms.silverocean.service.auth.totp.impl.OtpType;
import org.pms.silverocean.service.geolocation.GeoLocationResponse;
import org.pms.silverocean.service.geolocation.GeoLocationService;
import org.pms.silverocean.service.kyc.AccountStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
public class UserAuthenticationService {
    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final GeoLocationService geolocationService;
    private final RoleService roleService;
    private final GoogleAuthService googleAuthService;

    private final I18NService i18NService;

    private final LoginAttemptService loginAttemptService;

    private final JwtService jwtService;

    private final TotpService totpService;
    private final AffiliateService affiliateService;
    private final Set<String> signUpCache = Collections.synchronizedSet(new HashSet<>());

    @Autowired
    public UserAuthenticationService(UserDao userDao, PasswordEncoder passwordEncoder, GeoLocationService geolocationService, RoleService roleService, GoogleAuthService googleAuthService, I18NService i18NService, LoginAttemptService loginAttemptService, JwtService jwtService, TotpServiceFactory totpServiceFactory, AffiliateService affiliateService) {
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
        this.geolocationService = geolocationService;
        this.roleService = roleService;
        this.googleAuthService = googleAuthService;
        this.i18NService = i18NService;
        this.loginAttemptService = loginAttemptService;
        this.jwtService = jwtService;
        this.totpService = totpServiceFactory.getService(OtpType.EMAIL).orElseThrow();
        this.affiliateService = affiliateService;
    }

    public ResponseDTO register(RegistrationDTO registrationDTO, String ipAddress) {
        String normalizedEmail = StringUtils.trimToEmpty(registrationDTO.getEmail()).toLowerCase(Locale.ROOT);
        registrationDTO.setEmail(normalizedEmail);
        Optional<Users> checkIfUserExists;
        try {
            if (!signUpCache.add(normalizedEmail)) {
                return new ResponseDTO(false, ResponseCode.DUPLICATE_USER_DETAILS.getCode(), i18NService.getLocalizedMessage(ResponseCode.DUPLICATE_USER_DETAILS));
            }
            checkIfUserExists = userDao.findByEmail(normalizedEmail);
        } catch (Exception e) {
            log.error("Failed running SQL to find user", e);
            signUpCache.remove(normalizedEmail);
            return new ResponseDTO(false, ResponseCode.LOGIN_ERROR.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOGIN_ERROR));
        }
        if (checkIfUserExists.isPresent()) {
            signUpCache.remove(normalizedEmail);
            Users existingUser = checkIfUserExists.get();
            if (!existingUser.isActive()
                    && !existingUser.isEmailVerified()
                    && passwordEncoder.matches(registrationDTO.getPassword(), existingUser.getPassword())) {
                return sendRegistrationVerification(normalizedEmail);
            }
            return new ResponseDTO(false, ResponseCode.DUPLICATE_USER_DETAILS.getCode(), i18NService.getLocalizedMessage(ResponseCode.DUPLICATE_USER_DETAILS));
        }
        if (registrationDTO.getRoleId() == null && StringUtils.isBlank(registrationDTO.getToken())) {
            log.error("Local Register; could not register new user, missing role id");
            signUpCache.remove(normalizedEmail);
            return new ResponseDTO(false, ResponseCode.REGISTRATION_FAILED.getCode(), i18NService.getLocalizedMessage(ResponseCode.REGISTRATION_FAILED));
        }
        if (StringUtils.isNotBlank(registrationDTO.getReferralCode())) affiliateService.resolve(registrationDTO.getReferralCode());
        Users user = Users.builder().email(normalizedEmail)
                .password(passwordEncoder.encode(registrationDTO.getPassword()))
                .registrationIP(ipAddress)
                .source(RegistrationChannel.LOCAL.name())
                .locale(LocaleContextHolder.getLocale().getLanguage())
                .accountStatus(AccountStatus.PENDING_EMAIL_VERIFICATION.name())
                .fullName(registrationDTO.getFullName()).build();
        try {
            setLocationDetailsBasedOnIP(user);
            saveAndAssignRole(registrationDTO.getRoleId(), user, registrationDTO.getToken());
            if (StringUtils.isNotBlank(registrationDTO.getReferralCode())) {
                try {
                    affiliateService.attributeRegistration(registrationDTO.getReferralCode(), user.getId(), registrationDTO.getReferralCampaign());
                } catch (Exception attributionError) {
                    log.warn("User {} registered, but referral attribution could not be completed", user.getId(), attributionError);
                }
            }
        } catch (Exception e) {
            log.error("Could not register new user", e);
            signUpCache.remove(normalizedEmail);
            if (e instanceof PMSCustomException) {
                throw (PMSCustomException) e;
            }
            return new ResponseDTO(false, ResponseCode.REGISTRATION_FAILED.getCode(), i18NService.getLocalizedMessage(ResponseCode.REGISTRATION_FAILED));
        }
        try {
            return sendRegistrationVerification(normalizedEmail);
        } finally {
            signUpCache.remove(normalizedEmail);
        }
    }

    private ResponseDTO sendRegistrationVerification(String email) {
        try {
            String message = totpService.generateOTPCode(email);
            return new ResponseDTO(true, ResponseCode.EMAIL_OTP_GENERATED.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.EMAIL_OTP_GENERATED), message);
        } catch (Exception e) {
            log.error("Could not send registration verification code", e);
            return new ResponseDTO(false, ResponseCode.LOGIN_FAILURE_VERIFICATION_REQUIRED.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.LOGIN_FAILURE_VERIFICATION_REQUIRED));
        }
    }

    /**
     * Saves the new user and assigns role. Role is checked against the role selected during registration.
     * In case user was invited as property_manager, guard i.e. roles that can't self assign, role from invite is used.
     * In the case user self assigned a role but was also using an invite link, the self assign role is used.
     * @param roleId
     * @param user
     * @param inviteToken
     */
    private void saveAndAssignRole(Long roleId, Users user, String inviteToken) {
        if (StringUtils.isBlank(inviteToken)) {
            assert roleId != null;
            roleService.saveUserAndAssignRoleOnRegistration(roleId, user);
        } else {
            roleService.saveUserAndAssignRoleFromInvite(inviteToken, roleId, user);
        }
    }

    public ResponseDTO login(EmailPasswordDTO emailPasswordDTO) {
        loginAttemptService.assertLoginAllowed(emailPasswordDTO.getEmail());
        Optional<Users> checkIfUserExists;
        try {
            checkIfUserExists = userDao.findByEmail(emailPasswordDTO.getEmail());
        } catch (Exception e) {
            log.error("Failed running SQL to find user", e);
            loginAttemptService.loginFailed(emailPasswordDTO.getEmail());
            return new ResponseDTO(false, ResponseCode.LOAD_USER_ERROR.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOAD_USER_ERROR));
        }

        if (checkIfUserExists.isEmpty()) {
            loginAttemptService.loginFailed(emailPasswordDTO.getEmail());
            return new ResponseDTO(false, ResponseCode.LOGIN_FAILURE_INVALID_USER.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOGIN_FAILURE_INVALID_USER));
        }
        Users users = checkIfUserExists.get();
        if (passwordEncoder.matches(emailPasswordDTO.getPassword(), users.getPassword())) {
            if (!users.isActive()) {
                if (users.isMfaSetup()) {
                    return new ResponseDTO(false, ResponseCode.LOGIN_FAILURE_INACTIVE_USER.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOGIN_FAILURE_INACTIVE_USER));
                } else {
                    try {
                        String message = totpService.generateOTPCode(emailPasswordDTO.getEmail());
                        return new ResponseDTO(true, ResponseCode.EMAIL_OTP_GENERATED.getCode(), i18NService.getLocalizedMessage(ResponseCode.EMAIL_OTP_GENERATED), message);
                    } catch (Exception e) {
                        return new ResponseDTO(false, ResponseCode.LOGIN_FAILURE_VERIFICATION_REQUIRED.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOGIN_FAILURE_VERIFICATION_REQUIRED));
                    }
                }
            }
            if (StringUtils.isNotBlank(emailPasswordDTO.getToken())) {
                roleService.assignRoleFromInvite(emailPasswordDTO.getToken(), users);
            }
            String refreshToken = PMSUtils.randomMask();
            users.setLastLogin(ZonedDateTime.now());
            users.setRefreshToken(PMSUtils.hashToken(refreshToken));
            userDao.save(users);
            loginAttemptService.loginSuccess(emailPasswordDTO.getEmail());
            return new ResponseDTO(true, ResponseCode.LOGIN_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOGIN_SUCCESS),
                    new LoginResponseDTO(!PMSUtils.isByteArrayEmpty(users.getTotpSecret()),
                    users.isMfaSetup(), jwtService.generateJWT(users.getEmail()), refreshToken));
        }
        loginAttemptService.loginFailed(emailPasswordDTO.getEmail());
        return new ResponseDTO(false, ResponseCode.LOGIN_FAILURE_INCORRECT_PASSWORD.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOGIN_FAILURE_INCORRECT_PASSWORD));
    }

    /**
     * Establishes a complete authenticated session after a successful OTP
     * verification. A JWT without a persisted refresh token is intentionally
     * rejected by {@code JWTFilter}, so OTP verification must issue both.
     */
    public LoginResponseDTO createSessionForVerifiedUser(String email) {
        Users user = userDao.findByEmail(email)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LOGIN_FAILURE_INVALID_USER));
        if (!user.isActive()) {
            throw new PMSCustomException(ResponseCode.LOGIN_FAILURE_INACTIVE_USER);
        }
        String refreshToken = PMSUtils.randomMask();
        user.setRefreshToken(PMSUtils.hashToken(refreshToken));
        user.setLastLogin(ZonedDateTime.now());
        userDao.save(user);
        loginAttemptService.loginSuccess(email);
        return new LoginResponseDTO(!PMSUtils.isByteArrayEmpty(user.getTotpSecret()), user.isMfaSetup(),
                jwtService.generateJWT(user.getEmail()), refreshToken);
    }

    /**
     * Receives a unique Google token from the Angular client, sends to Google for look up and get back a validatedGoogleUser
     * The validatedGoogleUser can either be a new user or a returning user
     *
     * @param googleIdToken The JWT from a successful Google auth
     * @param ipAddress     The IP of the browser sending the request
     * @return a response of success with new JWT or failure
     */
    public ResponseDTO googleLogin(String googleIdToken, Long roleId, String token, String referralCode, String referralCampaign, String ipAddress) {
        Optional<Users> validatedGoogleUser = googleAuthService.verifyIdToken(googleIdToken);
        if (validatedGoogleUser.isPresent()) {
            Optional<Users> checkIfUserExists;
            try {
                Users googleUser = validatedGoogleUser.get();
                if (signUpCache.contains(googleUser.getEmail())) {
                    return new ResponseDTO(false, ResponseCode.DUPLICATE_USER_DETAILS.getCode(), i18NService.getLocalizedMessage(ResponseCode.DUPLICATE_USER_DETAILS));
                }
                signUpCache.add(googleUser.getEmail());
                checkIfUserExists = userDao.findByEmail(googleUser.getEmail());
                String refreshToken = PMSUtils.randomMask();
                if (checkIfUserExists.isEmpty()) {
                    if (roleId == null && StringUtils.isBlank(token)) {
                        log.error("Could not register new user, missing role id");
                        signUpCache.remove(googleUser.getEmail());
                        return new ResponseDTO(false, ResponseCode.REGISTRATION_FAILED.getCode(), i18NService.getLocalizedMessage(ResponseCode.REGISTRATION_FAILED));
                    }
                    try {
                        if (StringUtils.isNotBlank(referralCode)) affiliateService.resolve(referralCode);
                        googleUser.setRegistrationIP(ipAddress);
                        googleUser.setLocale(LocaleContextHolder.getLocale().getLanguage());
                        googleUser.setEmailVerified(true);
                        googleUser.setActive(true);
                        googleUser.setAccountStatus(AccountStatus.PENDING_KYC.name());
                        googleUser.setRefreshToken(PMSUtils.hashToken(refreshToken));
                        googleUser.setLastLogin(ZonedDateTime.now());
                        setLocationDetailsBasedOnIP(googleUser);
                        saveAndAssignRole(roleId, googleUser, token);
                        affiliateService.attributeRegistration(referralCode, googleUser.getId(), referralCampaign);
                    } catch (Exception e) {
                        signUpCache.remove(googleUser.getEmail());
                        log.error("Could not register new user", e);
                        return new ResponseDTO(false, ResponseCode.REGISTRATION_FAILED.getCode(), i18NService.getLocalizedMessage(ResponseCode.REGISTRATION_FAILED));
                    }
                } else {
                    googleUser = checkIfUserExists.get();
                    googleUser.setRefreshToken(PMSUtils.hashToken(refreshToken));
                    googleUser.setLastLogin(ZonedDateTime.now());
                    if (!googleUser.isActive()) {
                        googleUser.setEmailVerified(true);
                        googleUser.setActive(true);
                        if (!AccountStatus.ACTIVE.name().equals(googleUser.getAccountStatus())) {
                            googleUser.setAccountStatus(AccountStatus.PENDING_KYC.name());
                        }
                    }
                    if (StringUtils.isNotBlank(token)) {
                        roleService.assignRoleFromInvite(token, googleUser);
                    }
                    userDao.save(googleUser);
                }
                signUpCache.remove(googleUser.getEmail());
                return new ResponseDTO(true, ResponseCode.LOGIN_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOGIN_SUCCESS),
                        new LoginResponseDTO(!PMSUtils.isByteArrayEmpty(googleUser.getTotpSecret()), googleUser.isMfaSetup(),
                                jwtService.generateJWT(googleUser.getEmail()), refreshToken));
            } catch (Exception e) {
                log.error("Failed running SQL to find user", e);
                return new ResponseDTO(false, ResponseCode.LOGIN_ERROR.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOGIN_ERROR));
            }
        } else {
            return new ResponseDTO(false, ResponseCode.GOOGLE_LOGIN_ERROR.getCode(), i18NService.getLocalizedMessage(ResponseCode.GOOGLE_LOGIN_ERROR));
        }
    }

    @Async
    public void updatePassword(String email, String password) {
        userDao.findByEmail(email).ifPresent(user -> {
            user.setPassword(passwordEncoder.encode(password));
            userDao.save(user);
        });
    }

    public ResponseDTO loginByRefreshToken(String refreshToken) {
        String hashedToken = PMSUtils.hashToken(refreshToken);
        Optional<Users> checkIfUserExists = userDao.findByRefreshToken(hashedToken);
        if (checkIfUserExists.isEmpty()) {
            return new ResponseDTO(false, ResponseCode.LOAD_USER_ERROR.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOAD_USER_ERROR));
        }
        Users users = checkIfUserExists.get();
        if (!users.isActive()) {
            return new ResponseDTO(false, ResponseCode.LOGIN_FAILURE_INACTIVE_USER.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.LOGIN_FAILURE_INACTIVE_USER));
        }
        String newRefreshToken = PMSUtils.randomMask();
        users.setRefreshToken(PMSUtils.hashToken(newRefreshToken));
        users.setLastLogin(ZonedDateTime.now());
        userDao.save(users);
        return new ResponseDTO(true, ResponseCode.LOGIN_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.LOGIN_SUCCESS),
                new LoginResponseDTO(!PMSUtils.isByteArrayEmpty(users.getTotpSecret()), users.isMfaSetup(),
                        jwtService.generateJWT(users.getEmail()), newRefreshToken));
    }

    public void logout() {
        userDao.logoutUserByUserId();
    }

    private void setLocationDetailsBasedOnIP(Users user) {
        try {
            GeoLocationResponse location = geolocationService.getLocation(user.getRegistrationIP());
            if (location == null) return;
            user.setCountry(location.countryName());
            user.setCity(location.city());
            user.setCountryCode(location.countryCode());
        } catch (Exception locationError) {
            // Location is enrichment only. An external lookup outage must never
            // prevent account creation or strand a partially-created user.
            log.warn("Continuing registration without IP geolocation for {}", user.getRegistrationIP(), locationError);
        }
    }
}
