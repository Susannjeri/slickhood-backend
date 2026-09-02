package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.tax.TaxAssistService;
import org.pms.silverocean.service.tax.TaxModels.*;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tax-assist")
@PreAuthorize("isAuthenticated()")
public class TaxAssistController {
    private final TaxAssistService service;
    private final I18NService i18n;

    public TaxAssistController(TaxAssistService service, I18NService i18n) { this.service = service; this.i18n = i18n; }

    @PostMapping("/estimate/mri")
    public ResponseEntity<ResponseDTO> mri(@Valid @RequestBody MriEstimateRequest request) { return ok(service.estimateMri(request)); }

    @PostMapping("/estimate/cgt")
    public ResponseEntity<ResponseDTO> cgt(@Valid @RequestBody CgtEstimateRequest request) { return ok(service.estimateCgt(request)); }

    @GetMapping("/calculations")
    public ResponseEntity<ResponseDTO> history(@PageableDefault(size = 20) Pageable pageable) { return page(service.history(pageable)); }

    @PostMapping("/connections")
    public ResponseEntity<ResponseDTO> requestConnection(@Valid @RequestBody ConnectionRequest request) { return ok(service.requestConnection(request)); }

    @GetMapping("/connections")
    public ResponseEntity<ResponseDTO> connections() { return ok(service.myConnections()); }

    @GetMapping("/configuration")
    public ResponseEntity<ResponseDTO> configuration() { return ok(service.configuration()); }

    @DeleteMapping("/connections/{id}")
    public ResponseEntity<ResponseDTO> disconnect(@PathVariable long id) { service.disconnect(id); return ok(null); }

    @GetMapping("/admin/rules")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO> rules() { return ok(service.rules()); }

    @PutMapping("/admin/configuration")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO> updateConfiguration(@Valid @RequestBody ConfigurationRequest request) { return ok(service.updateConfiguration(request)); }

    @PostMapping("/admin/rules")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO> createRule(@Valid @RequestBody RuleRequest request) { return ok(service.createRule(request)); }

    @PutMapping("/admin/rules/{id}/close")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO> closeRule(@PathVariable long id, @Valid @RequestBody RuleCloseRequest request) { return ok(service.closeRule(id, request)); }

    @GetMapping("/admin/connections")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO> adminConnections(@PageableDefault(size = 50) Pageable pageable) { return page(service.adminConnections(pageable)); }

    @PutMapping("/admin/connections/{id}/review")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO> review(@PathVariable long id, @Valid @RequestBody ConnectionReview request) { return ok(service.reviewConnection(id, request)); }

    private ResponseEntity<ResponseDTO> ok(Object data) {
        ResponseCode code = ResponseCode.GENERAL_SUCCESS;
        return ResponseEntity.ok(new ResponseDTO(true, code.getCode(), i18n.getLocalizedMessage(code), data));
    }
    private ResponseEntity<ResponseDTO> page(Page<?> values) {
        ResponseDTO body = new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(), i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), values.getContent());
        body.setSize(values.getSize()); body.setTotalPages(values.getTotalPages()); body.setTotalElements(values.getTotalElements());
        return ResponseEntity.ok(body);
    }
}
