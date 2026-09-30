package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.deployedhash.DeployedHashService;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeployedHashControllerSecurityTest {
    @Test
    void publicRevisionResponseExposesOnlyTheReleaseHash() {
        DeployedHashService service = mock(DeployedHashService.class);
        I18NService i18n = mock(I18NService.class);
        when(service.getDeployedHash()).thenReturn("abc1234");
        when(i18n.getLocalizedMessage(ResponseCode.DEPLOYED_HASH_DETAILS)).thenReturn("revision");

        ResponseEntity<ResponseDTO> response = new DeployedHashController(service, i18n).getDeployedHash();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().getFirst()).isEqualTo(Map.of("hash", "abc1234"));
        verify(service).getDeployedHash();
    }
}
