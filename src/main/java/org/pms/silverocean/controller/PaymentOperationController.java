package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.payment.operations.PaymentOperationModels;
import org.pms.silverocean.service.payment.operations.PaymentOperationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/payment/operations") @RequiredArgsConstructor
public class PaymentOperationController {
    private final PaymentOperationService service; private final I18NService i18n;
    @PostMapping public ResponseEntity<ResponseDTO> append(@RequestBody @Valid PaymentOperationModels.Create request){return ok(service.append(request));}
    @GetMapping("/{caseReference}") public ResponseEntity<ResponseDTO> history(@PathVariable String caseReference){return ok(service.caseHistory(caseReference));}
    private ResponseEntity<ResponseDTO> ok(Object value){return ResponseEntity.ok(new ResponseDTO(true,ResponseCode.GENERAL_SUCCESS.getCode(),i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS),value));}
}
