package org.pms.silverocean.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.utils.OutputStreamErrorHandler;
import org.pms.silverocean.controller.wrappers.LeaseMessageRequest;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.lease.LeaseService;
import org.pms.silverocean.service.lease.wrappers.InitLeaseDTO;
import org.pms.silverocean.service.lease.wrappers.LeaseDTO;
import org.pms.silverocean.service.lease.wrappers.LeaseMessageDTO;
import org.pms.silverocean.service.lease.wrappers.LeaseTemplateDTO;
import org.pms.silverocean.service.lease.wrappers.PMSLeaseMode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

@RestController
@RequestMapping("/lease")
@Validated
@Slf4j
public class LeaseController extends OutputStreamErrorHandler {

    private final LeaseService leaseService;


    public LeaseController(LeaseService leaseService, I18NService i18NService) {
        super(i18NService);
        this.leaseService = leaseService;
    }

    @GetMapping(value = "/tenancy")
    public ResponseEntity<ResponseDTO> getActiveTenancy() {
        ResponseDTO body = new ResponseDTO(true, ResponseCode.ACTIVE_LEASE_TENANCY.getCode(), i18NService.getLocalizedMessage(ResponseCode.ACTIVE_LEASE_TENANCY), leaseService.listTenancyPerLoggedInUser());
        return ResponseEntity.ok(body);
    }

    @GetMapping(value = "/view")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_ACTIVE_LEASE)")
    public void viewLease(@RequestParam long leaseId, HttpServletResponse response) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            leaseService.viewLease(leaseId, buffer);
            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.builder("inline") // "attachment" for download
                            .filename("lease_" + leaseId + ".pdf")
                            .build().toString());
            buffer.writeTo(response.getOutputStream());
            response.getOutputStream().flush();
        } catch (PMSCustomException ex) {
            writeErrorResponse(response, ex.getResponseCode());
        } catch (IOException ex) {
            writeErrorResponse(response, ResponseCode.GENERAL_FAILURE);
        }
    }

    @GetMapping("/sign")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).SIGN_LEASE)")
    public ResponseEntity<ResponseDTO> acceptLease(@RequestParam long leaseId) {
        leaseService.signLease(leaseId);
        return ResponseEntity.ok(new ResponseDTO(true,  ResponseCode.GENERAL_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS)));
    }

    @PostMapping("/message")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).SEND_LEASE_MESSAGE)")
    public ResponseEntity<ResponseDTO> sendMessage(@RequestBody LeaseMessageRequest leaseMessageRequest) {
        leaseService.sendLeaseMessage(leaseMessageRequest);
        return ResponseEntity.ok(new ResponseDTO(true,  ResponseCode.GENERAL_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS)));
    }

    @GetMapping("/message")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_LEASE_MESSAGE)")
    public ResponseEntity<ResponseDTO> sendMessage(Pageable pageable, @RequestParam long leaseId) {
        Page<LeaseMessageDTO> leaseMessageByLeaseId = leaseService.getLeaseMessageByLeaseId(pageable, leaseId);
        ResponseDTO body = new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), leaseMessageByLeaseId.getContent());
        body.setSize(leaseMessageByLeaseId.getSize());
        body.setTotalPages(leaseMessageByLeaseId.getTotalPages());
        body.setTotalElements(leaseMessageByLeaseId.getTotalElements());
        return ResponseEntity.ok(body);
    }

    @GetMapping(value = "/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_ACTIVE_LEASE)")
    public ResponseEntity<ResponseDTO> getLeaseList(Pageable pageable) {
        Page<LeaseDTO> leaseDTOPage = leaseService.getLeaseList(pageable);
        ResponseDTO body = new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), leaseDTOPage.getContent());
        body.setSize(leaseDTOPage.getSize());
        body.setTotalPages(leaseDTOPage.getTotalPages());
        body.setTotalElements(leaseDTOPage.getTotalElements());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/tenant/create")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).CREATE_NEW_LEASE)")
    public ResponseEntity<ResponseDTO> initializeLeaseDraft(@RequestBody InitLeaseDTO initLeaseDTO) {
        leaseService.initializeLeaseDraft(initLeaseDTO.token(), initLeaseDTO.moveInDate(), initLeaseDTO.moveOutDate());
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.LEASE_INITIALIZED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.LEASE_INITIALIZED)));
    }

    @PutMapping("/tenant/edit/{leaseId}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_LEASE)")
    public ResponseEntity<ResponseDTO> tenantUpdateLeaseDraft(@PathVariable long leaseId, @RequestBody InitLeaseDTO initLeaseDTO) {
        leaseService.tenantEditLease(leaseId, initLeaseDTO.moveInDate(), initLeaseDTO.moveOutDate());
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS)));
    }

    @PutMapping("/owner/edit/{leaseId}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_LEASE)")
    public ResponseEntity<ResponseDTO> ownerUpdateLeaseDraft(@PathVariable long leaseId, @RequestBody LeaseTemplateDTO leaseDTO) {
        leaseService.ownerEditLease(leaseId, leaseDTO);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS)));
    }

    @DeleteMapping("/delete/{leaseId}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).DELETE_LEASE)")
    public ResponseEntity<ResponseDTO> deleteLeaseDraft(@PathVariable long leaseId) {
        leaseService.deleteLease(leaseId);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS)));
    }

    @PostMapping("/template")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).CREATE_LEASE_TEMPLATE)")
    public ResponseEntity<ResponseDTO> createLeaseTemplate(@RequestBody LeaseTemplateDTO leaseTemplateDTO) {
        leaseService.createLeaseTemplate(leaseTemplateDTO);
        ResponseDTO body = new ResponseDTO(true, ResponseCode.LEASE_TEMPLATE_CREATED_SUCCESSFULLY.getCode(), i18NService.getLocalizedMessage(ResponseCode.LEASE_TEMPLATE_CREATED_SUCCESSFULLY));
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/template/{templateId}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).DELETE_LEASE_TEMPLATE)")
    public ResponseEntity<ResponseDTO> deleteLeaseTemplate(@PathVariable long templateId) {
        leaseService.deleteLeaseTemplate(templateId);
        ResponseDTO body = new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS));
        return ResponseEntity.ok(body);
    }

    @PutMapping("/template/{templateId}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_LEASE_TEMPLATE)")
    public ResponseEntity<ResponseDTO> editLeaseTemplate(@PathVariable long templateId, @RequestBody LeaseTemplateDTO leaseTemplateDTO) {
        leaseService.editLeaseTemplate(templateId, leaseTemplateDTO);
        ResponseDTO body = new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS));
        return ResponseEntity.ok(body);
    }

    @GetMapping(value = "/view/template")
    @PreAuthorize("#token.isPresent() || hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_LEASE_TEMPLATE)")
    public void viewLeaseTemplate(@RequestParam Optional<Long> templateId, @RequestParam Optional<Long> unitId, @RequestParam Optional<String> token, HttpServletResponse response) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            if (templateId.isPresent()) {
                leaseService.renderLeaseTemplateToPdfByTemplate(templateId.get(), buffer);
            } else if (unitId.isPresent()) {
                leaseService.renderLeaseTemplateToPdfByUnit(unitId.get(), buffer);
            } else if (token.isPresent()) {
                leaseService.renderLeaseTemplateToPdfByInviteToken(token.get(), buffer);
            }
            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.builder("inline") // "attachment" for download
                            .filename("lease_template_" + templateId + ".pdf")
                            .build().toString());
            buffer.writeTo(response.getOutputStream());
            response.getOutputStream().flush();
        } catch (PMSCustomException ex) {
            writeErrorResponse(response, ex.getResponseCode());
        } catch (IOException ex) {
            writeErrorResponse(response, ResponseCode.GENERAL_FAILURE);
        }
    }

    @GetMapping(value = "/template")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).LIST_LEASE_TEMPLATE)")
    public ResponseEntity<ResponseDTO> getLeaseTemplates(Pageable pageable, Optional<PMSLeaseMode> leaseMode) {
        Page<LeaseTemplateDTO> templateList = leaseService.getTemplateList(pageable, leaseMode.orElse(null));
        ResponseDTO body = new ResponseDTO(true, ResponseCode.TEMPLATE_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.TEMPLATE_LIST), templateList.getContent());
        body.setSize(templateList.getSize());
        body.setTotalPages(templateList.getTotalPages());
        body.setTotalElements(templateList.getTotalElements());
        return ResponseEntity.ok(body);
    }


}
