package org.pms.silverocean.controller;

import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.deployedhash.DeployedHashDTO;
import org.pms.silverocean.service.deployedhash.DeployedHashService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/deployed-hash")
public class DeployedHashController {
    private final DeployedHashService deployedHashService;
    private final I18NService i18NService;

    public DeployedHashController(DeployedHashService deployedHashService, I18NService i18NService) {
        this.deployedHashService = deployedHashService;
        this.i18NService = i18NService;
    }

    @GetMapping
    public ResponseEntity<ResponseDTO> getDeployedHash() {
        DeployedHashDTO dto = deployedHashService.getDeployedHashDetails();
        return ResponseEntity.ok(new ResponseDTO(
                true,
                ResponseCode.DEPLOYED_HASH_DETAILS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.DEPLOYED_HASH_DETAILS),
                dto));
    }
}
