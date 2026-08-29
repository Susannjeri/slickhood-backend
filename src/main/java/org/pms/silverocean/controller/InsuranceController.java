package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.insurance.InsuranceModels.PaymentConfigurationRequest;
import org.pms.silverocean.service.insurance.InsuranceModels.CompanyEmailConfigurationRequest;
import org.pms.silverocean.service.insurance.InsuranceService;
import org.pms.silverocean.service.insurance.InsuranceCorrespondenceService;
import org.pms.silverocean.service.insurance.InsuranceModels.InsurerEmailRequest;
import org.pms.silverocean.service.insurance.InsuranceModels.InsurerEmailResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/insurance")
@RequiredArgsConstructor
public class InsuranceController {
    private final InsuranceService service;
    private final InsuranceCorrespondenceService correspondenceService;
    private final I18NService i18n;

    @GetMapping("/companies") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseDTO> companies() { return ok(service.companies()); }

    @GetMapping("/companies/{code}/payment-options") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseDTO> paymentOptions(@PathVariable String code) { return ok(service.customerPaymentOptions(code)); }

    @GetMapping("/admin/companies/{code}/payment-configurations")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_INSURANCE_PAYMENT_CONFIG)")
    public ResponseEntity<ResponseDTO> adminPaymentConfigurations(@PathVariable String code) {
        return ok(service.adminPaymentConfigurations(code));
    }

    @GetMapping("/admin/company-email-configurations")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_INSURANCE_CATALOG)")
    public ResponseEntity<ResponseDTO> companyEmailConfigurations() {
        return ok(service.companyEmailConfigurations());
    }

    @PutMapping("/admin/companies/{code}/email-configuration")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_INSURANCE_CATALOG)")
    public ResponseEntity<ResponseDTO> configureCompanyEmails(@PathVariable String code,
                                                               @Valid @RequestBody CompanyEmailConfigurationRequest request) {
        return ok(service.configureCompanyEmails(code, request));
    }

    @PostMapping("/admin/companies/{code}/payment-configurations")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_INSURANCE_PAYMENT_CONFIG)")
    public ResponseEntity<ResponseDTO> configurePayment(@PathVariable String code,
                                                         @Valid @RequestBody PaymentConfigurationRequest request) {
        return ok(service.configurePayment(code, request));
    }

    @DeleteMapping("/admin/payment-configurations/{id}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_INSURANCE_PAYMENT_CONFIG)")
    public ResponseEntity<ResponseDTO> deactivatePaymentConfiguration(@PathVariable long id) {
        service.deactivatePaymentConfiguration(id); return ok(null);
    }

    @PostMapping("/admin/email-requests")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_INSURANCE_QUOTES)")
    public ResponseEntity<ResponseDTO> queueEmailRequest(@Valid @RequestBody InsurerEmailRequest request) {
        return ok(correspondenceService.queue(request));
    }

    @PostMapping("/admin/email-responses")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_INSURANCE_QUOTES)")
    public ResponseEntity<ResponseDTO> recordEmailResponse(@Valid @RequestBody InsurerEmailResponse response) {
        return ok(correspondenceService.recordResponse(response));
    }

    @GetMapping("/admin/cases/{caseReference}/email-history")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).REVIEW_INSURANCE_APPLICATIONS)")
    public ResponseEntity<ResponseDTO> emailHistory(@PathVariable String caseReference) {
        return ok(correspondenceService.history(caseReference));
    }

    private ResponseEntity<ResponseDTO> ok(Object data) {
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(),
                i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), data));
    }
}
