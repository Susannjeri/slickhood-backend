package org.pms.silverocean.controller;

import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.controller.wrappers.ConfirmContactRequest;
import org.pms.silverocean.controller.wrappers.UpdateUserDetailsDTO;
import org.pms.silverocean.controller.wrappers.VerifyContactRequest;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.notification.common.NotificationChannel;
import org.pms.silverocean.service.users.SilverOceanUserService;
import org.pms.silverocean.service.users.StaffInviteRequest;
import org.pms.silverocean.service.invites.InviteService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/user")
@Validated
public class UserController {
    private final SilverOceanUserService silverOceanUserService;
    private final I18NService i18NService;
    private final InviteService inviteService;

    public UserController(SilverOceanUserService silverOceanUserService, I18NService i18NService, InviteService inviteService) {
        this.silverOceanUserService = silverOceanUserService;
        this.i18NService = i18NService;
        this.inviteService = inviteService;
    }

    @PostMapping("/staff/invite")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_INTERNAL_STAFF)")
    public ResponseEntity<ResponseDTO> inviteInternalStaff(@Valid @RequestBody StaffInviteRequest request) {
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.INVITE_CREATED_SUCCESSFULLY.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.INVITE_CREATED_SUCCESSFULLY),
                inviteService.createInternalStaffInvite(request)));
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).LIST_USERS)")
    public ResponseEntity<ResponseDTO> getAllUsers(Pageable pageable, @RequestParam Optional<String> search) {
        return ResponseEntity.ok(silverOceanUserService.getUserList(pageable, search));
    }

    @GetMapping("/details")
    public ResponseEntity<ResponseDTO> getUserDetails() {
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.LOGGED_IN_USER_DETAILS.getCode(),
               i18NService.getLocalizedMessage(ResponseCode.LOGGED_IN_USER_DETAILS.getDescription()), silverOceanUserService.getLoggedInUserDetails()));
    }

    @PostMapping("/update/contact")
    public ResponseEntity<ResponseDTO> updateUserPhoneNumber(@Valid @RequestBody ConfirmContactRequest request) {
        silverOceanUserService.verifyOTPAndUpdateContact(request.otp());
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.USER_CONTACTS_UPDATED.getCode(), i18NService.getLocalizedMessage(ResponseCode.USER_CONTACTS_UPDATED)));
    }

    @PostMapping("/verify/contact")
    public ResponseEntity<ResponseDTO> sendOTPToNewNumber(@Valid @RequestBody VerifyContactRequest request) {
        silverOceanUserService.saveChangeContactRequestAndSendOTP(request.contact(), request.channel());
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.OTP_VERIFICATION_SENT_TO_CONTACT.getCode(), i18NService.getLocalizedMessage(ResponseCode.OTP_VERIFICATION_SENT_TO_CONTACT)));
    }

    @PutMapping("/details")
    public ResponseEntity<ResponseDTO> updateUserDetails(@RequestBody UpdateUserDetailsDTO updateUserDetailsDTO) {
        silverOceanUserService.updateUserDetails(updateUserDetailsDTO);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.PROFILE_UPDATED_SUCCESSFULLY.getCode(), i18NService.getLocalizedMessage(ResponseCode.PROFILE_UPDATED_SUCCESSFULLY)));
    }
}
