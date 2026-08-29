package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.wealth.WealthRequests.*;
import org.pms.silverocean.service.wealth.WealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

@RestController @RequestMapping("/wealth") @RequiredArgsConstructor
public class WealthController {
    private final WealthService service; private final I18NService i18n;
    @GetMapping("/dashboard") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_WEALTH)")
    public ResponseEntity<ResponseDTO> dashboard(@RequestParam(defaultValue="5") int years,@RequestParam(defaultValue="5") BigDecimal valueGrowth,@RequestParam(defaultValue="3") BigDecimal incomeGrowth,@RequestParam(defaultValue="3") BigDecimal expenseGrowth){return ok(service.dashboard(years,valueGrowth,incomeGrowth,expenseGrowth));}
    @GetMapping("/assets") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_WEALTH)") public ResponseEntity<ResponseDTO> assets(){return ok(service.assets());}
    @PostMapping("/assets") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_WEALTH_ASSETS)") public ResponseEntity<ResponseDTO> create(@Valid @RequestBody AssetRequest r){return ok(service.createAsset(r));}
    @PutMapping("/assets/{id}") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_WEALTH_ASSETS)") public ResponseEntity<ResponseDTO> update(@PathVariable long id,@Valid @RequestBody AssetRequest r){return ok(service.updateAsset(id,r));}
    @DeleteMapping("/assets/{id}") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_WEALTH_ASSETS)") public ResponseEntity<ResponseDTO> archive(@PathVariable long id){service.archiveAsset(id);return ok(null);}
    @PostMapping("/assets/{id}/valuations") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_WEALTH_FINANCE)") public ResponseEntity<ResponseDTO> valuation(@PathVariable long id,@Valid @RequestBody ValuationRequest r){return ok(service.addValuation(id,r));}
    @GetMapping("/assets/{id}/valuations") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_WEALTH)") public ResponseEntity<ResponseDTO> valuations(@PathVariable long id){return ok(service.valuations(id));}
    @PostMapping("/assets/{id}/cash-flows") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_WEALTH_FINANCE)") public ResponseEntity<ResponseDTO> cashFlow(@PathVariable long id,@Valid @RequestBody CashFlowRequest r){return ok(service.addCashFlow(id,r));}
    @PostMapping("/assets/{id}/liabilities") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_WEALTH_FINANCE)") public ResponseEntity<ResponseDTO> liability(@PathVariable long id,@Valid @RequestBody LiabilityRequest r){return ok(service.addLiability(id,r));}
    @PostMapping("/assets/{id}/obligations") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_WEALTH_COMPLIANCE)") public ResponseEntity<ResponseDTO> obligation(@PathVariable long id,@Valid @RequestBody ObligationRequest r){return ok(service.addObligation(id,r));}
    @PostMapping("/obligations/{id}/complete") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_WEALTH_COMPLIANCE)") public ResponseEntity<ResponseDTO> complete(@PathVariable long id){return ok(service.completeObligation(id));}
    @PostMapping("/goals") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_WEALTH_GOALS)") public ResponseEntity<ResponseDTO> goal(@Valid @RequestBody GoalRequest r){return ok(service.addGoal(r));}
    @PostMapping("/assets/{id}/vault") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_WEALTH_VAULT)") public ResponseEntity<ResponseDTO> upload(@PathVariable long id,@RequestParam String category,@RequestParam(required=false) LocalDate documentDate,@RequestParam(required=false) LocalDate expiryDate,@RequestParam(required=false) String notes,@RequestParam MultipartFile file) throws IOException{return ok(service.upload(id,category,documentDate,expiryDate,notes,file));}
    @GetMapping("/assets/{id}/vault") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_WEALTH)") public ResponseEntity<ResponseDTO> documents(@PathVariable long id){return ok(service.documents(id));}
    @GetMapping("/vault/{id}") @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_WEALTH)") public ResponseEntity<ResponseDTO> document(@PathVariable long id){return ok(service.document(id));}
    private ResponseEntity<ResponseDTO> ok(Object data){return ResponseEntity.ok(new ResponseDTO(true,ResponseCode.GENERAL_SUCCESS.getCode(),i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS),data));}
}
