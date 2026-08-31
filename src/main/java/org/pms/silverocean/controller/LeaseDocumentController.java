package org.pms.silverocean.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.utils.OutputStreamErrorHandler;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.leasedocument.GenerateLeaseDocumentRequest;
import org.pms.silverocean.service.leasedocument.LeaseDocumentService;
import org.pms.silverocean.service.leasedocument.TemplateVersionRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PageableDefault;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@RestController
@RequestMapping("/lease/documents")
public class LeaseDocumentController extends OutputStreamErrorHandler {
    private static final String PERMISSION = "T(org.pms.silverocean.service.auth.roles.enums.Permission)";
    private final LeaseDocumentService service;

    public LeaseDocumentController(LeaseDocumentService service, I18NService i18NService) {
        super(i18NService);
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority(" + PERMISSION + ".CREATE_LEASE_DOCUMENT)")
    public ResponseEntity<ResponseDTO> generate(@Valid @RequestBody GenerateLeaseDocumentRequest request) {
        return ok(ResponseCode.LEASE_DOCUMENT_CREATED, service.generate(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority(" + PERMISSION + ".VIEW_LEASE_DOCUMENT)")
    public ResponseEntity<ResponseDTO> list(@PageableDefault(size = 25, sort = "createdOn", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return page(service.list(pageable));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority(" + PERMISSION + ".VIEW_LEASE_DOCUMENT)")
    public void pdf(@PathVariable long id, HttpServletResponse response) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            service.renderPdf(id, output);
            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.inline().filename("slickhood-document-" + id + ".pdf").build().toString());
            output.writeTo(response.getOutputStream());
        } catch (PMSCustomException ex) {
            writeErrorResponse(response, ex.getResponseCode());
        } catch (IOException ex) {
            writeErrorResponse(response, ResponseCode.GENERAL_FAILURE);
        }
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAuthority(" + PERMISSION + ".ISSUE_LEASE_DOCUMENT)")
    public ResponseEntity<ResponseDTO> issue(@PathVariable long id) {
        return ok(ResponseCode.LEASE_DOCUMENT_ISSUED, service.issue(id));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority(" + PERMISSION + ".ACKNOWLEDGE_LEASE_DOCUMENT)")
    public ResponseEntity<ResponseDTO> acknowledge(@PathVariable long id) {
        return ok(ResponseCode.LEASE_DOCUMENT_ACKNOWLEDGED, service.acknowledge(id));
    }

    @PostMapping("/{id}/sign")
    @PreAuthorize("hasAuthority(" + PERMISSION + ".SIGN_LEASE_DOCUMENT)")
    public ResponseEntity<ResponseDTO> sign(@PathVariable long id) {
        return ok(ResponseCode.LEASE_DOCUMENT_SIGNED, service.sign(id));
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAuthority(" + PERMISSION + ".VIEW_LEASE_DOCUMENT)")
    public ResponseEntity<ResponseDTO> templates() {
        return ok(ResponseCode.GENERAL_SUCCESS, service.templates());
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAuthority(" + PERMISSION + ".MANAGE_LEASE_DOCUMENT_TEMPLATE)")
    public ResponseEntity<ResponseDTO> createTemplateVersion(@Valid @RequestBody TemplateVersionRequest request) {
        return ok(ResponseCode.LEASE_DOCUMENT_TEMPLATE_CREATED, service.createTemplateVersion(request));
    }

    private ResponseEntity<ResponseDTO> ok(ResponseCode code, Object data) {
        return ResponseEntity.ok(new ResponseDTO(true, code.getCode(), i18NService.getLocalizedMessage(code), data));
    }

    private ResponseEntity<ResponseDTO> page(Page<?> values) {
        ResponseDTO body = new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), values.getContent());
        body.setSize(values.getSize());
        body.setTotalPages(values.getTotalPages());
        body.setTotalElements(values.getTotalElements());
        return ResponseEntity.ok(body);
    }
}
