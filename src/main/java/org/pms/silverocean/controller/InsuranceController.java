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
import org.pms.silverocean.service.insurance.InsuranceOperationsService;
import org.pms.silverocean.service.insurance.InsuranceStaffDirectoryService;
import org.pms.silverocean.service.insurance.InsuranceModels.InsurerEmailRequest;
import org.pms.silverocean.service.insurance.InsuranceModels.InsurerEmailResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/insurance")
@RequiredArgsConstructor
public class InsuranceController {
    private final InsuranceService service;
    private final InsuranceCorrespondenceService correspondenceService;
    private final InsuranceOperationsService operations;
    private final InsuranceStaffDirectoryService staffDirectory;
    private final I18NService i18n;

    @GetMapping("/companies") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseDTO> companies() { return ok(service.companies()); }

    @GetMapping("/agency") @PreAuthorize("isAuthenticated()") public ResponseEntity<ResponseDTO> agency(){return ok(operations.agency());}
    @GetMapping("/products") @PreAuthorize("isAuthenticated()") public ResponseEntity<ResponseDTO> products(){return ok(operations.products());}
    @PostMapping("/cases") @PreAuthorize("isAuthenticated()") public ResponseEntity<ResponseDTO> createCase(@Valid @RequestBody org.pms.silverocean.service.insurance.InsuranceModels.CaseRequest r){return ok(operations.create(r));}
    @GetMapping("/cases") @PreAuthorize("isAuthenticated()") public ResponseEntity<ResponseDTO> myCases(){return ok(operations.mine());}
    @GetMapping("/cases/{id}") @PreAuthorize("isAuthenticated()") public ResponseEntity<ResponseDTO> myCase(@PathVariable long id){return ok(operations.myCase(id));}
    @PostMapping("/cases/{id}/withdraw") @PreAuthorize("isAuthenticated()") public ResponseEntity<ResponseDTO> withdrawCase(@PathVariable long id){return ok(operations.withdraw(id));}
    @PostMapping("/cases/{id}/select-quote") @PreAuthorize("isAuthenticated()") public ResponseEntity<ResponseDTO> selectQuote(@PathVariable long id,@Valid @RequestBody org.pms.silverocean.service.insurance.InsuranceModels.SelectQuoteRequest r){return ok(operations.selectQuote(id,r));}
    @PostMapping("/cases/{id}/payments") @PreAuthorize("isAuthenticated()") public ResponseEntity<ResponseDTO> recordPayment(@PathVariable long id,@Valid @RequestBody org.pms.silverocean.service.insurance.InsuranceModels.PaymentRequest r){return ok(operations.recordPayment(id,r));}
    @PostMapping(value="/payments/{id}/proof",consumes="multipart/form-data") @PreAuthorize("isAuthenticated()") public ResponseEntity<ResponseDTO> paymentProof(@PathVariable long id,@RequestParam MultipartFile file)throws IOException{return ok(operations.uploadPaymentProof(id,file));}
    @GetMapping("/payments/{id}/proof") @PreAuthorize("isAuthenticated()") public ResponseEntity<ResponseDTO> paymentProof(@PathVariable long id){return ok(operations.paymentProof(id));}
    @GetMapping("/policies") @PreAuthorize("isAuthenticated()") public ResponseEntity<ResponseDTO> policies(){return ok(operations.policies());}
    @PostMapping("/claims") @PreAuthorize("isAuthenticated()") public ResponseEntity<ResponseDTO> claim(@Valid @RequestBody org.pms.silverocean.service.insurance.InsuranceModels.ClaimRequest r){return ok(operations.claim(r));}
    @GetMapping("/claims") @PreAuthorize("isAuthenticated()") public ResponseEntity<ResponseDTO> claims(){return ok(operations.claims());}
    @PostMapping(value="/documents",consumes="multipart/form-data") @PreAuthorize("isAuthenticated()") public ResponseEntity<ResponseDTO> uploadDocument(@RequestParam(required=false) Long caseId,@RequestParam(required=false) Long policyId,@RequestParam(required=false) Long claimId,@RequestParam String category,@RequestParam MultipartFile file)throws IOException{return ok(operations.upload(caseId,policyId,claimId,category,file));}
    @GetMapping("/documents") @PreAuthorize("isAuthenticated()") public ResponseEntity<ResponseDTO> documents(){return ok(operations.documents());}
    @GetMapping("/documents/{id}") @PreAuthorize("isAuthenticated()") public ResponseEntity<ResponseDTO> document(@PathVariable long id){return ok(operations.document(id));}

    @GetMapping("/admin/operations/summary") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_INSURANCE_REPORTS)") public ResponseEntity<ResponseDTO> operationsSummary(){return ok(operations.summary());}
    @GetMapping("/admin/staff") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).REVIEW_INSURANCE_APPLICATIONS)") public ResponseEntity<ResponseDTO> staff(){return ok(staffDirectory.activeStaff());}
    @GetMapping("/admin/cases") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).REVIEW_INSURANCE_APPLICATIONS)") public ResponseEntity<ResponseDTO> queue(@RequestParam(required=false) String status,Pageable pageable){return ok(operations.queue(status,pageable));}
    @PostMapping("/admin/cases/{id}/assign") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).REVIEW_INSURANCE_APPLICATIONS)") public ResponseEntity<ResponseDTO> assign(@PathVariable long id,@Valid @RequestBody org.pms.silverocean.service.insurance.InsuranceModels.AssignmentRequest r){return ok(operations.assign(id,r));}
    @PostMapping("/admin/cases/{id}/status") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).REVIEW_INSURANCE_APPLICATIONS)") public ResponseEntity<ResponseDTO> caseStatus(@PathVariable long id,@Valid @RequestBody org.pms.silverocean.service.insurance.InsuranceModels.CaseStatusRequest r){return ok(operations.updateCaseStatus(id,r));}
    @PostMapping("/admin/cases/{id}/quotes") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_INSURANCE_QUOTES)") public ResponseEntity<ResponseDTO> addQuote(@PathVariable long id,@Valid @RequestBody org.pms.silverocean.service.insurance.InsuranceModels.QuoteRequest r){return ok(operations.addQuote(id,r));}
    @PostMapping("/admin/cases/{caseId}/quotes/{quoteId}/publish") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).APPROVE_INSURANCE_QUOTES)") public ResponseEntity<ResponseDTO> publishQuote(@PathVariable long caseId,@PathVariable long quoteId){return ok(operations.publishQuote(caseId,quoteId));}
    @PostMapping("/admin/payments/{id}/decision") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VERIFY_INSURANCE_PAYMENTS)") public ResponseEntity<ResponseDTO> paymentDecision(@PathVariable long id,@Valid @RequestBody org.pms.silverocean.service.insurance.InsuranceModels.PaymentDecisionRequest r){return ok(operations.decidePayment(id,r));}
    @PostMapping("/admin/payments/{id}/remit") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VERIFY_INSURANCE_PAYMENTS)") public ResponseEntity<ResponseDTO> remit(@PathVariable long id,@Valid @RequestBody org.pms.silverocean.service.insurance.InsuranceModels.RemittanceRequest r){return ok(operations.remit(id,r));}
    @PostMapping("/admin/cases/{id}/policy") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).ISSUE_INSURANCE_POLICIES)") public ResponseEntity<ResponseDTO> issuePolicy(@PathVariable long id,@Valid @RequestBody org.pms.silverocean.service.insurance.InsuranceModels.PolicyRequest r){return ok(operations.issue(id,r));}
    @GetMapping("/admin/claims") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_INSURANCE_CLAIMS)") public ResponseEntity<ResponseDTO> claimQueue(@RequestParam(required=false) String status,Pageable pageable){return ok(operations.claimQueue(status,pageable));}
    @PostMapping("/admin/claims/{id}/status") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_INSURANCE_CLAIMS)") public ResponseEntity<ResponseDTO> claimStatus(@PathVariable long id,@Valid @RequestBody org.pms.silverocean.service.insurance.InsuranceModels.ClaimStatusRequest r){return ok(operations.updateClaim(id,r));}
    @PostMapping("/admin/policies/{id}/renewal") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_INSURANCE_RENEWALS)") public ResponseEntity<ResponseDTO> renewal(@PathVariable long id,@Valid @RequestBody org.pms.silverocean.service.insurance.InsuranceModels.RenewalRequest r){return ok(operations.renewal(id,r));}
    @GetMapping("/admin/renewals") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_INSURANCE_RENEWALS)") public ResponseEntity<ResponseDTO> renewals(Pageable pageable){return ok(operations.renewalQueue(pageable));}

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

    @GetMapping("/admin/companies")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_INSURANCE_CATALOG)")
    public ResponseEntity<ResponseDTO> adminCompanies() { return ok(service.adminCompanies()); }

    @PostMapping("/admin/companies")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_INSURANCE_CATALOG)")
    public ResponseEntity<ResponseDTO> createCompany(@Valid @RequestBody org.pms.silverocean.service.insurance.InsuranceModels.CompanyCreateRequest request) {
        return ok(service.createCompany(request));
    }

    @PutMapping("/admin/companies/{code}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_INSURANCE_CATALOG)")
    public ResponseEntity<ResponseDTO> updateCompany(@PathVariable String code,
            @Valid @RequestBody org.pms.silverocean.service.insurance.InsuranceModels.CompanyUpdateRequest request) {
        return ok(service.updateCompany(code, request));
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
