package org.pms.silverocean.controller;

import io.micrometer.common.util.StringUtils;
import jakarta.validation.constraints.Email;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.controller.wrappers.LoginResponseDTO;
import org.pms.silverocean.controller.wrappers.VerificationOptionsDTO;
import org.pms.silverocean.controller.wrappers.VerifyOtpDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.LoginAttemptService;
import org.pms.silverocean.service.auth.UserAuthenticationService;
import org.pms.silverocean.service.auth.totp.TotpService;
import org.pms.silverocean.service.auth.totp.TotpServiceFactory;
import org.pms.silverocean.service.auth.totp.impl.OtpType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/otp")
@Slf4j
@Validated
public class OtpController {
    private final TotpServiceFactory totpServiceFactory;
    private final I18NService i18NService;

    private final LoginAttemptService loginAttemptService;

    private final UserAuthenticationService userAuthenticationService;

    public OtpController(TotpServiceFactory totpServiceFactory, I18NService i18NService, LoginAttemptService loginAttemptService, UserAuthenticationService userAuthenticationService) {
        this.totpServiceFactory = totpServiceFactory;
        this.i18NService = i18NService;
        this.loginAttemptService = loginAttemptService;
        this.userAuthenticationService = userAuthenticationService;
    }

    @GetMapping("/qrcode")
    public ResponseEntity<ResponseDTO> generateQRCodeImage() {
        TotpService totpService = totpServiceFactory.getService(OtpType.GOOGLE_TOTP).orElseThrow(() -> new PMSCustomException(ResponseCode.UNSUPPORTED_VERIFICATION_OPTION));
        String email = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        String qrCode = totpService.generateOTPCode(email);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.QR_CODE_REGISTRATION.getCode(), i18NService.getLocalizedMessage(ResponseCode.QR_CODE_REGISTRATION), Map.of("qrcode", qrCode)));
    }

//    @GetMapping("/email")
//    public ResponseEntity<ResponseDTO> sendEmailOTP(@RequestParam @Email String email) {
//        TotpService totpService = totpServiceFactory.getService(EmailOtpServiceImpl.NAME).orElseThrow(() -> new PMSCustomException(ResponseCode.UNSUPPORTED_VERIFICATION_OPTION));
//        String message = totpService.generateOTPCode(email);
//        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.EMAIL_OTP_GENERATED.getCode(), i18NService.getLocalizedMessage(ResponseCode.EMAIL_OTP_GENERATED), message));
//
//    }

    @GetMapping("/send")
    public ResponseEntity<ResponseDTO> sendOTP(@RequestParam String email, @RequestParam OtpType channel) {
        if (!OtpType.EMAIL.equals(channel)) {
            throw new PMSCustomException(ResponseCode.CANNOT_REGISTER_QR_CODE_WHEN_RECOVERING_ACCOUNT);
        }
        // Do not send to arbitrary addresses and do not reveal whether the account
        // exists. Eligible accounts receive the message; every caller gets the same
        // generic handoff response.
        if (totpServiceFactory.getUserVerificationOptions(email).isPresent()) {
            TotpService totpService = totpServiceFactory.getService(channel)
                    .orElseThrow(() -> new PMSCustomException(ResponseCode.UNSUPPORTED_VERIFICATION_OPTION));
            totpService.generateOTPCode(email);
        }
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.EMAIL_OTP_GENERATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.EMAIL_OTP_GENERATED)));

    }

    @PostMapping(value = "/verify")
    public ResponseEntity<ResponseDTO> verify(@Validated @RequestBody VerifyOtpDTO verifyOtpDTO) {
        Optional<TotpService> totpServiceOptional = totpServiceFactory.getService(verifyOtpDTO.getChannel());
        if (totpServiceOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ResponseDTO(false, ResponseCode.UNSUPPORTED_VERIFICATION_OPTION.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.UNSUPPORTED_VERIFICATION_OPTION)));
        }
        TotpService totpService = totpServiceOptional.get();
        boolean verified = totpService.validateVerificationToken(verifyOtpDTO.getEmail(), verifyOtpDTO.getCode());
        if (verified) {
            if (StringUtils.isNotBlank(verifyOtpDTO.getPassword())) {
                userAuthenticationService.updatePassword(verifyOtpDTO.getEmail(), verifyOtpDTO.getPassword());
            }
            LoginResponseDTO session = userAuthenticationService.createSessionForVerifiedUser(verifyOtpDTO.getEmail());
            return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.TOTP_VALIDATION_SUCCESS.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.TOTP_VALIDATION_SUCCESS), session));
        } else {
            return ResponseEntity.ok(new ResponseDTO(false, ResponseCode.TOTP_VALIDATION_FAILURE.getCode(), i18NService.getLocalizedMessage(ResponseCode.TOTP_VALIDATION_FAILURE)));
        }
    }

    @GetMapping(value = "/options")
    public ResponseEntity<ResponseDTO> getVerificationOptions(@RequestParam @Email String email) {
        // Public recovery must not disclose whether an email is registered or which
        // additional factors a particular account has configured. Email recovery is
        // presented uniformly; the send endpoint also returns a generic handoff.
        VerificationOptionsDTO publicOptions = new VerificationOptionsDTO(true, false, false, OtpType.EMAIL.name());
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.VERIFICATION_OPTIONS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.VERIFICATION_OPTIONS), publicOptions));
    }
}
