package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.privacy.PrivacyModels;
import org.pms.silverocean.service.privacy.PrivacyService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/privacy")
@RequiredArgsConstructor
public class PrivacyController {
    private final PrivacyService privacy;
    private final I18NService i18n;

    @PostMapping("/requests")
    public ResponseEntity<ResponseDTO> submit(@RequestBody @Valid PrivacyModels.Submit request) {
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.PRIVACY_REQUEST_SUBMITTED.getCode(),
                i18n.getLocalizedMessage(ResponseCode.PRIVACY_REQUEST_SUBMITTED), privacy.submit(request)));
    }

    @GetMapping("/requests/my")
    public ResponseEntity<ResponseDTO> mine(Pageable pageable) {
        var page = privacy.myRequests(pageable);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.PRIVACY_REQUEST_SUBMITTED.getCode(),
                i18n.getLocalizedMessage(ResponseCode.PRIVACY_REQUEST_SUBMITTED), page.getContent(),
                page.getTotalPages(), page.getTotalElements(), page.getSize()));
    }

    @GetMapping("/export")
    public ResponseEntity<PrivacyModels.Export> export() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"slickhood-personal-data.json\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(privacy.exportMyData());
    }

    @GetMapping("/admin/requests")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO> all(Pageable pageable) {
        var page = privacy.allRequests(pageable);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.PRIVACY_REQUEST_SUBMITTED.getCode(),
                i18n.getLocalizedMessage(ResponseCode.PRIVACY_REQUEST_SUBMITTED), page.getContent(),
                page.getTotalPages(), page.getTotalElements(), page.getSize()));
    }

    @PutMapping("/admin/requests/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO> review(@PathVariable long id, @RequestBody @Valid PrivacyModels.Review request) {
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.PRIVACY_REQUEST_SUBMITTED.getCode(),
                i18n.getLocalizedMessage(ResponseCode.PRIVACY_REQUEST_SUBMITTED), privacy.review(id, request)));
    }
}
