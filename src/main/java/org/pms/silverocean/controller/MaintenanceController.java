package org.pms.silverocean.controller;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.maintenance.MaintenanceModels;
import org.pms.silverocean.service.maintenance.MaintenanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/maintenance") @RequiredArgsConstructor
public class MaintenanceController {
 private final MaintenanceService service;private final I18NService i18n;
 @PostMapping public ResponseEntity<ResponseDTO> create(@Valid @RequestBody MaintenanceModels.Create request){return ok(service.create(request));}
 @GetMapping("/unit/{unitId}") public ResponseEntity<ResponseDTO> list(@PathVariable long unitId){return ok(service.list(unitId));}
 @PutMapping("/{id}") public ResponseEntity<ResponseDTO> update(@PathVariable long id,@Valid @RequestBody MaintenanceModels.Update request){return ok(service.update(id,request));}
 private ResponseEntity<ResponseDTO> ok(Object data){return ResponseEntity.ok(new ResponseDTO(true,ResponseCode.GENERAL_SUCCESS.getCode(),i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS),data));}
}
