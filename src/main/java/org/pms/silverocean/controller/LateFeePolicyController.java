package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.payment.latefee.LateFeePolicyModels;
import org.pms.silverocean.service.payment.latefee.LateFeePolicyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/billing/late-fee-policy")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class LateFeePolicyController {
    private final LateFeePolicyService service;
    private final I18NService i18n;

    @GetMapping("/{billingType}")
    public ResponseEntity<ResponseDTO> view(@PathVariable String billingType) {
        return ok(service.view(billingType));
    }

    @PutMapping("/{billingType}")
    public ResponseEntity<ResponseDTO> save(@PathVariable String billingType,
                                            @RequestBody @Valid LateFeePolicyModels.Update request) {
        return ok(service.save(billingType, request));
    }

    private ResponseEntity<ResponseDTO> ok(Object value) {
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(),
                i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), value));
    }
}
