package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.estate.EstateService;
import org.pms.silverocean.service.estate.OwnershipRequest;
import org.pms.silverocean.service.estate.ServiceChargeRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController @RequestMapping("/estate")
public class EstateController {
    private final EstateService service; private final I18NService i18n;
    public EstateController(EstateService service,I18NService i18n){this.service=service;this.i18n=i18n;}
    @PostMapping("/ownership") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_ESTATE)")
    public ResponseEntity<ResponseDTO> create(@Valid @RequestBody OwnershipRequest request){return ok(ResponseCode.OWNERSHIP_CREATED,service.create(request));}
    @GetMapping("/ownership") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_ESTATE)")
    public ResponseEntity<ResponseDTO> list(){return ok(ResponseCode.GENERAL_SUCCESS,service.list());}
    @PostMapping("/ownership/{id}/end") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_ESTATE)")
    public ResponseEntity<ResponseDTO> end(@PathVariable long id,@RequestParam LocalDate endDate){return ok(ResponseCode.OWNERSHIP_ENDED,service.end(id,endDate));}
    @PostMapping("/service-charges") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).CREATE_SERVICE_CHARGE)")
    public ResponseEntity<ResponseDTO> charge(@Valid @RequestBody ServiceChargeRequest request){return ok(ResponseCode.SERVICE_CHARGE_CREATED,service.createServiceCharge(request));}
    @GetMapping("/service-charges") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_SERVICE_CHARGE)")
    public ResponseEntity<ResponseDTO> charges(){return ok(ResponseCode.GENERAL_SUCCESS,service.listServiceCharges());}
    private ResponseEntity<ResponseDTO> ok(ResponseCode code,Object data){return ResponseEntity.ok(new ResponseDTO(true,code.getCode(),i18n.getLocalizedMessage(code),data));}
}
