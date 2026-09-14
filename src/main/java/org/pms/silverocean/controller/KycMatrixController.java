package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.kyc.KycMatrixRequests;
import org.pms.silverocean.service.kyc.KycMatrixService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/kyc/admin/matrix")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class KycMatrixController {
    private final KycMatrixService service;
    private final I18NService i18n;
    @GetMapping("/draft") public ResponseEntity<ResponseDTO> draft(){return ok(service.draft());}
    @GetMapping("/published") public ResponseEntity<ResponseDTO> published(){return ok(service.published());}
    @GetMapping("/preview") public ResponseEntity<ResponseDTO> preview(){return ok(service.preview());}
    @GetMapping("/history") public ResponseEntity<ResponseDTO> history(){return ok(service.history());}
    @PostMapping("/requirements") public ResponseEntity<ResponseDTO> add(@RequestBody @Valid KycMatrixRequests.RequirementUpsert request){return ok(service.add(request));}
    @PutMapping("/requirements/{id}") public ResponseEntity<ResponseDTO> update(@PathVariable long id,@RequestBody @Valid KycMatrixRequests.RequirementUpsert request){return ok(service.update(id,request));}
    @DeleteMapping("/requirements/{id}") public ResponseEntity<ResponseDTO> deactivate(@PathVariable long id){return ok(service.deactivate(id));}
    @PostMapping("/publish") public ResponseEntity<ResponseDTO> publish(@RequestBody @Valid KycMatrixRequests.Publish request){return ok(service.publish(request));}
    private ResponseEntity<ResponseDTO> ok(Object data){return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(),i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS),data));}
}
