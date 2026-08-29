package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.account.AccountService;
import org.pms.silverocean.service.account.dto.AccountDTO;
import org.pms.silverocean.service.account.dto.AccountSummaryDTO;
import org.pms.silverocean.service.account.dto.CreateAccountRequestDTO;
import org.pms.silverocean.service.account.dto.UpdateAccountPropertyRequestDTO;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/account")
@Validated
public class AccountController {

    private final AccountService accountService;
    private final I18NService i18NService;

    public AccountController(AccountService accountService, I18NService i18NService) {
        this.accountService = accountService;
        this.i18NService = i18NService;
    }

    @PostMapping("/create")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).CREATE_ACCOUNT)")
    public ResponseEntity<ResponseDTO> createAccount(@Valid @RequestBody CreateAccountRequestDTO dto) {
        AccountDTO account = accountService.createAccount(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ResponseDTO(
                true,
                ResponseCode.ACCOUNT_CREATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.ACCOUNT_CREATED),
                account));
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_ACCOUNT)")
    public ResponseEntity<ResponseDTO> listAccounts(Pageable pageable,
                                                    @RequestParam Optional<Long> propertyId,
                                                    @RequestParam Optional<Boolean> byLandlord,
                                                    @RequestParam Optional<Boolean> isSlickHood,
                                                    @RequestParam Optional<String> landlordEmail,
                                                    @RequestParam Optional<PaymentChannel> channel,
                                                    @RequestParam(defaultValue = "true") boolean active,
                                                    @RequestParam(defaultValue = "true") boolean verified) {
        Page<AccountSummaryDTO> page = accountService.listAccounts(pageable, propertyId, byLandlord, isSlickHood, landlordEmail, channel, active, verified);
        return ResponseEntity.ok(new ResponseDTO(
                true,
                ResponseCode.ACCOUNT_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.ACCOUNT_LIST),
                page.getContent(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getSize()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_ACCOUNT)")
    public ResponseEntity<ResponseDTO> getAccount(@PathVariable Long id) {
        AccountDTO account = accountService.getAccount(id);
        return ResponseEntity.ok(new ResponseDTO(
                true,
                ResponseCode.ACCOUNT_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.ACCOUNT_LIST),
                account));
    }

    @PutMapping("/{id}/verify")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VERIFY_ACCOUNT)")
    public ResponseEntity<ResponseDTO> verifyAccount(@PathVariable Long id, @RequestParam Boolean verify, @RequestParam Optional<String> comments) {
       accountService.verifyAccount(id, verify, comments.orElse(""));
        return ResponseEntity.ok(new ResponseDTO(
                true,
                ResponseCode.GENERAL_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS)));
    }

    @GetMapping("/{id}/verify")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_ACCOUNT)")
    public ResponseEntity<ResponseDTO> requestAccountVerification(@PathVariable Long id) {
        accountService.requestVerification(id);
        return ResponseEntity.ok(new ResponseDTO(
                true,
                ResponseCode.GENERAL_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS)));
    }

    @PutMapping("/{id}/property")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_ACCOUNT)")
    public ResponseEntity<ResponseDTO> updateAccountProperty(@PathVariable Long id,
                                                             @Valid @RequestBody UpdateAccountPropertyRequestDTO dto) {
        accountService.updateAccountProperty(id, dto);
        return ResponseEntity.ok(new ResponseDTO(
                true,
                ResponseCode.ACCOUNT_PROPERTY_UPDATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.ACCOUNT_PROPERTY_UPDATED)));
    }

    @GetMapping("/{id}/property/decrypt")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).DECRYPT_ACCOUNT_PROPERTY)")
    public ResponseEntity<ResponseDTO> decryptAccountProperty(@PathVariable Long id,
                                                              @RequestParam String key) {
        String value = accountService.decryptAccountProperty(id, key);
        return ResponseEntity.ok(new ResponseDTO(
                true,
                ResponseCode.ACCOUNT_PROPERTY_DECRYPTED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.ACCOUNT_PROPERTY_DECRYPTED),
                value));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).DELETE_ACCOUNT)")
    public ResponseEntity<ResponseDTO> deleteAccount(@PathVariable Long id) {
        accountService.deleteAccount(id);
        return ResponseEntity.ok(new ResponseDTO(
                true,
                ResponseCode.ACCOUNT_DELETED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.ACCOUNT_DELETED)));
    }

    @GetMapping("/channels/{channel}/properties")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_ACCOUNT)")
    public ResponseEntity<ResponseDTO> getChannelProperties(@PathVariable PaymentChannel channel) {
        return ResponseEntity.ok(new ResponseDTO(
                true,
                ResponseCode.ACCOUNT_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.ACCOUNT_LIST),
                accountService.getChannelProperties(channel)));
    }
}
