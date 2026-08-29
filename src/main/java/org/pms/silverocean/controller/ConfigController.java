package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.config.ConfigDTO;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.EditConfigDTO;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/config")
public class ConfigController {
    private final ConfigService configService;
    private final I18NService i18NService;

    public ConfigController(ConfigService configService, I18NService i18NService) {
        this.configService = configService;
        this.i18NService = i18NService;
    }


    @GetMapping("/names")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_CONFIG)")
    public ResponseEntity<ResponseDTO> getConfigNames() {
        Set<String> allConfigNames = configService.getAllConfigNames();

        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.CONFIG_DETAILS.getCode(), i18NService.getLocalizedMessage(ResponseCode.CONFIG_DETAILS), allConfigNames));
    }

    /**
     * Get a specific configuration by key
     */
    @GetMapping("/value")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_CONFIG)")
    public ResponseEntity<ResponseDTO> getConfig(@RequestParam PMSConfigs name) {
        ConfigDTO config = configService.getConfigByNameForFrontEndView(name);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.CONFIG_DETAILS.getCode(), i18NService.getLocalizedMessage(ResponseCode.CONFIG_DETAILS), config));

    }

    @GetMapping("/value/decrypt")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_CONFIG)")
    public ResponseEntity<ResponseDTO> getConfigDecryptedValue(@RequestParam PMSConfigs name) {
        ConfigDTO param = configService.getConfigByName(name).get();
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.DECRYPTED_PARAM_VALUE.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.DECRYPTED_PARAM_VALUE), String.valueOf(param.stringValue())));
    }

    /**
     * Update a specific configuration
     */
    @PutMapping("/update")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_CONFIG)")
    public ResponseEntity<ResponseDTO> updateConfig(@Valid @RequestBody EditConfigDTO configDTO) {
        configService.updateConfig(configDTO);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.CONFIG_UPDATED_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.CONFIG_UPDATED_SUCCESS)));
    }

    @PatchMapping("/rotate/key")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).ROTATE_KEY)")
    public ResponseEntity<ResponseDTO> rotateKey() {
        configService.rotateKey();
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.NEW_KEYS_CREATED.getCode(), i18NService.getLocalizedMessage(ResponseCode.NEW_KEYS_CREATED)));
    }

}
