package org.pms.silverocean.controller;

import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.leasedocument.DocumentBrandingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/lease/documents/branding")
@PreAuthorize("isAuthenticated()")
public class DocumentBrandingController {
    private final DocumentBrandingService service;
    private final I18NService i18n;
    public DocumentBrandingController(DocumentBrandingService service, I18NService i18n) { this.service = service; this.i18n = i18n; }
    @GetMapping public ResponseEntity<ResponseDTO> current() { return ok(service.current()); }
    @PostMapping(consumes = "multipart/form-data") public ResponseEntity<ResponseDTO> upload(@RequestPart("logo") MultipartFile logo) { return ok(service.upload(logo)); }
    private ResponseEntity<ResponseDTO> ok(Object value) { return ResponseEntity.ok(new ResponseDTO(true,
            ResponseCode.GENERAL_SUCCESS.getCode(), i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), value)); }
}
