package org.pms.silverocean.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.EmailPasswordDTO;
import org.pms.silverocean.controller.wrappers.GoogleLoginDTO;
import org.pms.silverocean.controller.wrappers.RefreshTokenDTO;
import org.pms.silverocean.controller.wrappers.RegistrationDTO;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.auth.UserAuthenticationService;
import org.pms.silverocean.service.auth.PublicEndpointRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Validated
public class AuthController {
    private final UserAuthenticationService userAuthenticationService;
    private final PublicEndpointRateLimiter publicEndpointRateLimiter;

    @Autowired
    public AuthController(UserAuthenticationService userAuthenticationService,
                          PublicEndpointRateLimiter publicEndpointRateLimiter) {
        this.userAuthenticationService = userAuthenticationService;
        this.publicEndpointRateLimiter = publicEndpointRateLimiter;
    }

    @PostMapping("/login")
    public ResponseEntity<ResponseDTO> login(@Validated @RequestBody EmailPasswordDTO emailPasswordDTO) {
        ResponseDTO login = userAuthenticationService.login(emailPasswordDTO);
        return login.isSuccess() ? ResponseEntity.ok(login) : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(login);
    }

    @PostMapping("/register")
    public ResponseEntity<ResponseDTO> signup(HttpServletRequest request, @Validated @RequestBody RegistrationDTO registrationDTO) {
        String clientIp = PMSUtils.getIPAddress(request);
        publicEndpointRateLimiter.check("register-ip", clientIp, 5,
                "Too many registration attempts. Please wait a minute and try again.");
        ResponseDTO register = userAuthenticationService.register(registrationDTO, clientIp);
        return register.isSuccess() ? ResponseEntity.status(HttpStatus.CREATED).body(register) : ResponseEntity.badRequest().body(register);
    }

    @PostMapping("/google")
    public ResponseEntity<ResponseDTO> googleLogin(HttpServletRequest request, @Validated @RequestBody GoogleLoginDTO googleLoginDTO) {
        String clientIp = PMSUtils.getIPAddress(request);
        publicEndpointRateLimiter.check("google-login-ip", clientIp, 20,
                "Too many sign-in attempts. Please wait a minute and try again.");
        ResponseDTO login = userAuthenticationService.googleLogin(googleLoginDTO.getIdToken(), googleLoginDTO.getRoleId(), googleLoginDTO.getToken(), googleLoginDTO.getReferralCode(), googleLoginDTO.getReferralCampaign(), googleLoginDTO.getProfileType(), googleLoginDTO.getOrganizationName(), clientIp);
        return login.isSuccess() ? ResponseEntity.ok(login) : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(login);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ResponseDTO> loginByRefreshToken(HttpServletRequest request,
                                                            @Validated @RequestBody RefreshTokenDTO refreshTokenDTO) {
        publicEndpointRateLimiter.check("refresh-ip", PMSUtils.getIPAddress(request), 60,
                "Too many session refresh requests. Please wait a minute and try again.");
        publicEndpointRateLimiter.check("refresh-token", refreshTokenDTO.refreshToken(), 10,
                "This session is receiving too many requests. Please wait a minute and try again.");
        ResponseDTO login = userAuthenticationService.loginByRefreshToken(refreshTokenDTO.refreshToken());
        return login.isSuccess() ? ResponseEntity.ok(login) : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(login);
    }

    @GetMapping("/logout")
    public ResponseEntity<ResponseDTO> logout() {
        userAuthenticationService.logout();
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(), ResponseCode.GENERAL_SUCCESS.getDescription()));
    }
}
