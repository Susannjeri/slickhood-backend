package org.pms.silverocean.controller;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.soko.SokoRiderKycService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/soko") @RequiredArgsConstructor @PreAuthorize("isAuthenticated()")
public class SokoRiderKycController {
    private final SokoRiderKycService service;
    private ResponseEntity<ResponseDTO> ok(Object data){return ResponseEntity.ok(new ResponseDTO(true,ResponseCode.GENERAL_SUCCESS.getCode(),"Rider evidence updated.",data));}
    @GetMapping("/rider/kyc") public ResponseEntity<ResponseDTO> own(){return ok(service.myChecklist());}
    @PostMapping(value="/rider/kyc",consumes="multipart/form-data") public ResponseEntity<ResponseDTO> upload(@RequestParam String documentType,@RequestPart("file") MultipartFile file)throws Exception{return ok(service.upload(documentType,file));}
    @GetMapping("/admin/riders/{id}/kyc") @PreAuthorize("hasRole('SUPER_ADMIN')") public ResponseEntity<ResponseDTO> admin(@PathVariable long id){return ok(service.adminChecklist(id));}
    @PutMapping("/admin/rider-kyc/{id}/review") @PreAuthorize("hasRole('SUPER_ADMIN')") public ResponseEntity<ResponseDTO> review(@PathVariable long id,@RequestBody SokoRiderKycService.Review review){return ok(service.review(id,review));}
}
