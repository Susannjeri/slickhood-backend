package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.estate.EstateSetupService;
import org.pms.silverocean.service.estate.EstateSetupStatus;
import org.pms.silverocean.service.property.PMSPropertyManagementMode;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;

class EstateSetupControllerSecurityTest {
    @Test
    void setupStatusRequiresPropertyVisibilityPermission() throws NoSuchMethodException {
        PreAuthorize boundary = EstateSetupController.class.getDeclaredMethod("getStatus", long.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(boundary).isNotNull();
        assertThat(boundary.value()).contains("VIEW_PROPERTY");
    }

    @Test
    void setupStatusCannotBeReusedAfterPaymentAccountChanges() {
        EstateSetupService service = mock(EstateSetupService.class);
        I18NService i18n = mock(I18NService.class);
        when(i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS)).thenReturn("ok");
        when(service.getStatus(83L)).thenReturn(new EstateSetupStatus(
                83L, "Jabali Towers", PMSPropertyManagementMode.SERVICE_CHARGE,
                1L, 0L, 2L, 0L, 0L,
                true, true, false, false, EstateSetupStatus.NextAction.ASSIGN_HOMEOWNERS));

        var response = new EstateSetupController(service, i18n).getStatus(83L);

        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }
}
