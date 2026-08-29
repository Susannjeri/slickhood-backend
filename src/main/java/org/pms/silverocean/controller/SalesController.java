package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.sales.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/sales")
public class SalesController {
    private final SalesService service; private final I18NService i18n;
    public SalesController(SalesService service,I18NService i18n){this.service=service;this.i18n=i18n;}
    @PostMapping @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_SALE_PIPELINE)")
    public ResponseEntity<ResponseDTO> create(@Valid @RequestBody CreateSaleRequest request){return ok(ResponseCode.SALE_CREATED,service.create(request));}
    @GetMapping @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_SALE_PIPELINE)")
    public ResponseEntity<ResponseDTO> list(){return ok(ResponseCode.GENERAL_SUCCESS,service.list());}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_SALE_PIPELINE)")
    public ResponseEntity<ResponseDTO> update(@PathVariable long id,@Valid @RequestBody UpdateSaleRequest request){return ok(ResponseCode.SALE_UPDATED,service.update(id,request));}
    @PostMapping("/{id}/accept-offer") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).ACCEPT_SALE_OFFER)")
    public ResponseEntity<ResponseDTO> accept(@PathVariable long id){return ok(ResponseCode.SALE_ACCEPTED,service.acceptOffer(id));}
    @PostMapping("/{id}/milestones") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_SALE_PIPELINE)") public ResponseEntity<ResponseDTO> milestone(@PathVariable long id,@Valid @RequestBody SaleMilestoneModels.Create request){return ok(ResponseCode.SALE_UPDATED,service.addMilestone(id,request));}
    @GetMapping("/{id}/milestones") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_SALE_PIPELINE)") public ResponseEntity<ResponseDTO> milestones(@PathVariable long id){return ok(ResponseCode.GENERAL_SUCCESS,service.milestones(id));}
    private ResponseEntity<ResponseDTO> ok(ResponseCode code,Object data){return ResponseEntity.ok(new ResponseDTO(true,code.getCode(),i18n.getLocalizedMessage(code),data));}
}
