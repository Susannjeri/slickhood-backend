package org.pms.silverocean.controller;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.property.CustomPropertyTypeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/property/type/custom") @RequiredArgsConstructor @PreAuthorize("hasRole('SUPER_ADMIN')")
public class CustomPropertyTypeController {
 private final CustomPropertyTypeService service;
 @GetMapping public ResponseDTO list(){return ok(service.all());}
 @PostMapping public ResponseDTO create(@Valid @RequestBody CustomPropertyTypeService.Create r){return ok(service.create(r));}
 @PutMapping("/{code}") public ResponseDTO edit(@PathVariable String code,@Valid @RequestBody CustomPropertyTypeService.Edit r){return ok(service.edit(code,r));}
 private ResponseDTO ok(Object value){return new ResponseDTO(true,"S0000","Property type catalogue updated",value);}
}
