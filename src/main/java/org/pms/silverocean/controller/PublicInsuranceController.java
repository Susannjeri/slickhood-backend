package org.pms.silverocean.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.insurance.InsuranceGuestAccessService;
import org.pms.silverocean.service.insurance.InsuranceModels;
import org.pms.silverocean.service.insurance.InsuranceOperationsService;
import org.pms.silverocean.service.insurance.InsuranceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/public/insurance")
@RequiredArgsConstructor
public class PublicInsuranceController {
    private final InsuranceGuestAccessService guests;
    private final InsuranceOperationsService operations;
    private final InsuranceService insurance;
    private final I18NService i18n;

    @GetMapping("/agency") public ResponseEntity<ResponseDTO> agency() { return ok(operations.agency()); }
    @GetMapping("/products") public ResponseEntity<ResponseDTO> products() { return ok(operations.products()); }
    @GetMapping("/companies") public ResponseEntity<ResponseDTO> companies() { return ok(insurance.companies()); }
    @GetMapping("/access/channels") public ResponseEntity<ResponseDTO> channels() { return ok(guests.deliveryOptions()); }

    @PostMapping("/access/request")
    public ResponseEntity<ResponseDTO> request(@Valid @RequestBody InsuranceModels.GuestAccessRequest request,
                                                HttpServletRequest servletRequest) {
        return ok(guests.requestAccess(request, PMSUtils.getIPAddress(servletRequest)));
    }

    @PostMapping("/access/verify")
    public ResponseEntity<ResponseDTO> verify(@Valid @RequestBody InsuranceModels.GuestAccessVerifyRequest request) {
        return ok(guests.verify(request));
    }

    @PostMapping("/access/resend")
    public ResponseEntity<ResponseDTO> resend(@Valid @RequestBody InsuranceModels.GuestAccessResendRequest request,
                                               HttpServletRequest servletRequest) {
        return ok(guests.resend(request, PMSUtils.getIPAddress(servletRequest)));
    }

    @PostMapping("/access/status")
    public ResponseEntity<ResponseDTO> deliveryStatus(@Valid @RequestBody InsuranceModels.GuestAccessStatusRequest request,
                                                       HttpServletRequest servletRequest) {
        return ok(guests.deliveryStatus(request, PMSUtils.getIPAddress(servletRequest)));
    }

    @GetMapping("/case")
    public ResponseEntity<ResponseDTO> caseStatus(@RequestHeader("X-Insurance-Access") String token) {
        return ok(guests.caseStatus(token));
    }

    @PostMapping("/cases")
    public ResponseEntity<ResponseDTO> createCase(@RequestHeader("X-Insurance-Access") String token,
                                                   @Valid @RequestBody InsuranceModels.CaseRequest request) {
        return ok(guests.createCase(token, request));
    }

    @PostMapping(value = "/proposal-ocr/marine-idf", consumes = "multipart/form-data")
    public ResponseEntity<ResponseDTO> extractMarineIdf(@RequestHeader("X-Insurance-Access") String token,
                                                         @RequestParam MultipartFile file) throws IOException {
        return ok(guests.extractMarineIdf(token, file));
    }

    @PostMapping(value = "/cases/{caseId}/proposal", consumes = "multipart/form-data")
    public ResponseEntity<ResponseDTO> uploadProposal(@RequestHeader("X-Insurance-Access") String token,
                                                       @PathVariable long caseId,
                                                       @RequestParam MultipartFile file) throws IOException {
        return ok(guests.uploadProposal(token, caseId, file));
    }

    @PostMapping("/cases/{caseId}/select-quote")
    public ResponseEntity<ResponseDTO> selectQuote(@RequestHeader("X-Insurance-Access") String token,
                                                    @PathVariable long caseId,
                                                    @Valid @RequestBody InsuranceModels.SelectQuoteRequest request) {
        return ok(guests.selectQuote(token, caseId, request));
    }

    private ResponseEntity<ResponseDTO> ok(Object data) {
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(),
                i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), data));
    }
}
