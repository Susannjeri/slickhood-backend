package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.payment.currency.CurrencyPreferenceModels;
import org.pms.silverocean.service.payment.currency.CurrencyPreferenceService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/currency/preferences")
@RequiredArgsConstructor
public class CurrencyPreferenceController {
    private final CurrencyPreferenceService service;
    private final I18NService i18NService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseDTO> current() { return response(service.current()); }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseDTO> update(@Valid @RequestBody CurrencyPreferenceModels.Update update) {
        return response(service.update(update));
    }

    private ResponseEntity<ResponseDTO> response(Object value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ResponseDTO(true,
                ResponseCode.CURRENCY_PREFERENCES.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.CURRENCY_PREFERENCES), value));
    }
}
