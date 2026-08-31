package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.sales.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

@RestController @RequestMapping("/sales")
public class SalesController {
    private final SalesService service; private final I18NService i18n;
    public SalesController(SalesService service,I18NService i18n){this.service=service;this.i18n=i18n;}
    @PostMapping @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_SALE_PIPELINE)")
    public ResponseEntity<ResponseDTO> create(@Valid @RequestBody CreateSaleRequest request){return ok(ResponseCode.SALE_CREATED,service.create(request));}
    @GetMapping @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_SALE_PIPELINE)")
    public ResponseEntity<ResponseDTO> list(@PageableDefault(size=25,sort="createdOn") Pageable pageable){return page(service.list(pageable));}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_SALE_PIPELINE)")
    public ResponseEntity<ResponseDTO> update(@PathVariable long id,@Valid @RequestBody UpdateSaleRequest request){return ok(ResponseCode.SALE_UPDATED,service.update(id,request));}
    @PostMapping("/{id}/accept-offer") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).ACCEPT_SALE_OFFER)")
    public ResponseEntity<ResponseDTO> accept(@PathVariable long id){return ok(ResponseCode.SALE_ACCEPTED,service.acceptOffer(id));}
    @PostMapping("/{id}/milestones") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_SALE_PIPELINE)") public ResponseEntity<ResponseDTO> milestone(@PathVariable long id,@Valid @RequestBody SaleMilestoneModels.Create request){return ok(ResponseCode.SALE_UPDATED,service.addMilestone(id,request));}
    @GetMapping("/{id}/milestones") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_SALE_PIPELINE)") public ResponseEntity<ResponseDTO> milestones(@PathVariable long id,@PageableDefault(size=50,sort="occurredAt") Pageable pageable){return page(service.milestones(id,pageable));}
    private ResponseEntity<ResponseDTO> ok(ResponseCode code,Object data){return ResponseEntity.ok(new ResponseDTO(true,code.getCode(),i18n.getLocalizedMessage(code),data));}
    private ResponseEntity<ResponseDTO> page(Page<?> values){ResponseDTO body=new ResponseDTO(true,ResponseCode.GENERAL_SUCCESS.getCode(),i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS),values.getContent());body.setSize(values.getSize());body.setTotalPages(values.getTotalPages());body.setTotalElements(values.getTotalElements());return ResponseEntity.ok(body);}
}
