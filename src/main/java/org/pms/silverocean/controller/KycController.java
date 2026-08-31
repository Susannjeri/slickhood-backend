package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.kyc.KycDocumentType;
import org.pms.silverocean.service.kyc.KycDocumentContent;
import org.pms.silverocean.service.kyc.KycDocumentMaintenanceRequest;
import org.pms.silverocean.service.kyc.KycReviewRequest;
import org.pms.silverocean.service.kyc.KycService;
import org.pms.silverocean.service.kyc.StartKycRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    @PostMapping("/reprocess")
    public ResponseEntity<ResponseDTO> reprocess() throws Exception {
        return ok(ResponseCode.KYC_DETAILS, service.reprocessOwnDocuments());
    }

    @GetMapping("/documents/{documentId}/content")
    public ResponseEntity<byte[]> documentContent(@PathVariable long documentId) {
        KycDocumentContent document = service.documentContent(documentId);
        MediaType mediaType;
        try { mediaType = MediaType.parseMediaType(document.contentType()); }
        catch (Exception ignored) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(document.fileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .contentLength(document.contentLength())
                .contentType(mediaType)
                .body(document.bytes());
    }

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

    @PostMapping("/admin/{caseId}/reprocess")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).LIST_USERS)")
    public ResponseEntity<ResponseDTO> reprocessCase(@PathVariable long caseId) throws Exception {
        return ok(ResponseCode.KYC_DETAILS, service.reprocessCase(caseId));
    }

    @PatchMapping("/admin/documents/{documentId}/maintenance")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).LIST_USERS)")
    public ResponseEntity<ResponseDTO> maintainDocument(@PathVariable long documentId,
                                                         @Valid @RequestBody KycDocumentMaintenanceRequest request) {
        return ok(ResponseCode.KYC_DETAILS, service.maintainDocument(documentId, request));
    }

    private ResponseEntity<ResponseDTO> ok(ResponseCode code, Object data) { return ResponseEntity.ok(response(code, data)); }
    private ResponseDTO response(ResponseCode code, Object data) {
        return new ResponseDTO(true, code.getCode(), i18NService.getLocalizedMessage(code), data);
    }
}
