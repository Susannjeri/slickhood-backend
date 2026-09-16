package org.pms.silverocean.controller;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.affiliate.AffiliatePolicyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/affiliate/admin/policy") @RequiredArgsConstructor @PreAuthorize("hasRole('SUPER_ADMIN')")
public class AffiliatePolicyController {
 private final AffiliatePolicyService service;
 @GetMapping public ResponseDTO get(){return ok(service.adminView());}
 @PutMapping public ResponseDTO edit(@Valid @RequestBody AffiliatePolicyService.Edit r){return ok(service.edit(r));}
 private ResponseDTO ok(Object value){return new ResponseDTO(true,"S0000","Affiliate policy loaded",value);}
}
