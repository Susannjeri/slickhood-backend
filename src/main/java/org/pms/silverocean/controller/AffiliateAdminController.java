package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.affiliate.AffiliateAdminService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/affiliate") @RequiredArgsConstructor
public class AffiliateAdminController {
    private final AffiliateAdminService service;
    private final UserDao users;
    private final I18NService i18n;
    private ResponseEntity<ResponseDTO> ok(Object value) {return ResponseEntity.ok(new ResponseDTO(true,ResponseCode.GENERAL_SUCCESS.getCode(),i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS),value));}
    private ResponseEntity<ResponseDTO> page(Page<?> page) {return ResponseEntity.ok(new ResponseDTO(true,ResponseCode.GENERAL_SUCCESS.getCode(),i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS),page.getContent(),page.getTotalPages(),page.getTotalElements(),page.getSize()));}
    @GetMapping("/admin/profiles") @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO> directory(@RequestParam(required=false) String query,@RequestParam(required=false) String status,Pageable pageable) {return page(service.directory(query,status,pageable));}
    @GetMapping("/admin/profiles/{userId}") @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO> detail(@PathVariable long userId) {return ok(service.detail(userId));}
    @PutMapping("/admin/profiles/{userId}") @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO> edit(@PathVariable long userId,@Valid @RequestBody AffiliateAdminService.Edit request) {return ok(service.edit(userId,request));}
    @GetMapping("/admin/profiles/{userId}/history/{ledger}") @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO> history(@PathVariable long userId,@PathVariable String ledger,Pageable pageable) {return page(service.history(userId,ledger,pageable,true));}
    @GetMapping("/history/{ledger}") @PreAuthorize("hasAnyRole('AFFILIATE','SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO> ownHistory(@PathVariable String ledger,Pageable pageable) {return page(service.history(users.getUserId(),ledger,pageable,false));}
    @GetMapping("/balances") @PreAuthorize("hasAnyRole('AFFILIATE','SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO> ownBalances() {return ok(service.ownBalances());}
    @GetMapping("/admin/payout-queue") @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO> payouts(@RequestParam(required=false) String query,@RequestParam(required=false) String status,Pageable pageable) {return page(service.payoutQueue(query,status,pageable));}
}
