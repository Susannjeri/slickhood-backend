package org.pms.silverocean.controller;

import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.utils.OutputStreamErrorHandler;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.sp.ServiceDirectoryService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sp/directory")
@Slf4j
public class ServiceDirectoryController extends OutputStreamErrorHandler {

    private final ServiceDirectoryService directoryService;

    public ServiceDirectoryController(I18NService i18NService, ServiceDirectoryService directoryService) {
        super(i18NService);
        this.directoryService = directoryService;
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_SP_SERVICE)")
    public ResponseEntity<ResponseDTO> listDirectory(Pageable pageable,
                                                     @RequestParam(required = false) Long categoryId) {
        var page = directoryService.listDirectory(pageable, categoryId);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_DIRECTORY_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_DIRECTORY_LIST),
                page.getContent(), page.getTotalPages(), page.getTotalElements(), page.getSize()));
    }

    @GetMapping("/service/{serviceId}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_SP_SERVICE)")
    public ResponseEntity<ResponseDTO> getServiceDetails(@PathVariable long serviceId) {
        var details = directoryService.getServiceDetails(serviceId);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_SERVICE_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_SERVICE_LIST), details));
    }
}
