package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.kyc.KycDocumentType;
import org.pms.silverocean.service.kyc.KycReviewRequest;
import org.pms.silverocean.service.kyc.KycService;
import org.pms.silverocean.service.kyc.StartKycRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/kyc")
@RequiredArgsConstructor
public class KycController {
    private final KycService service;
    private final I18NService i18NService;

    @GetMapping("/current")
    public ResponseEntity<ResponseDTO> current() { return ok(ResponseCode.KYC_DETAILS, service.current()); }

    @PostMapping("/start")
    public ResponseEntity<ResponseDTO> start(@Valid @RequestBody StartKycRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(response(ResponseCode.KYC_STARTED, service.start(request)));
    }

    @PostMapping("/documents")
    public ResponseEntity<ResponseDTO> upload(@RequestParam KycDocumentType documentType,
                                              @RequestParam MultipartFile file) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED).body(response(ResponseCode.KYC_DOCUMENT_UPLOADED,
                service.upload(documentType, file)));
    }

    @PostMapping("/submit")
    public ResponseEntity<ResponseDTO> submit() { return ok(ResponseCode.KYC_SUBMITTED, service.submit()); }

    @PostMapping("/admin/{caseId}/review")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).LIST_USERS)")
    public ResponseEntity<ResponseDTO> review(@PathVariable long caseId, @Valid @RequestBody KycReviewRequest request) {
        return ok(ResponseCode.KYC_REVIEWED, service.review(caseId, request));
    }

    @GetMapping("/admin/queue")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).LIST_USERS)")
    public ResponseEntity<ResponseDTO> reviewQueue() {
        return ok(ResponseCode.KYC_DETAILS, service.reviewQueue());
    }

    private ResponseEntity<ResponseDTO> ok(ResponseCode code, Object data) { return ResponseEntity.ok(response(code, data)); }
    private ResponseDTO response(ResponseCode code, Object data) {
        return new ResponseDTO(true, code.getCode(), i18NService.getLocalizedMessage(code), data);
    }
}
