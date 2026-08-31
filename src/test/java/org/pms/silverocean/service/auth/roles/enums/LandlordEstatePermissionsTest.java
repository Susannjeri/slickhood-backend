package org.pms.silverocean.service.auth.roles.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LandlordEstatePermissionsTest {
    @Test
    void landlordCanOperateTheEstateCreatedFromAServiceChargeProperty() {
        assertThat(PMSPermission.LANDLORD_PERMISSIONS.getPermissions()).contains(
                Permission.VIEW_ESTATE,
                Permission.MANAGE_ESTATE,
                Permission.CREATE_SERVICE_CHARGE,
                Permission.VIEW_SERVICE_CHARGE
        );
    }
}
