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

    @Test
    void estateManagerCanCreateAndMaintainTheEstateBeforeInvitingHomeowners() {
        assertThat(PMSPermission.ESTATE_MANAGER.getPermissions()).contains(
                Permission.CREATE_PROPERTY,
                Permission.EDIT_PROPERTY,
                Permission.VIEW_PROPERTY,
                Permission.CREATE_UNIT,
                Permission.EDIT_UNIT,
                Permission.VIEW_UNIT,
                Permission.MANAGE_ESTATE,
                Permission.CREATE_SERVICE_CHARGE
        );
    }

    @Test
    void participantRolesCannotCreateOrManageTheirHostBusinessArea() {
        assertThat(PMSPermission.TENANT.getPermissions()).doesNotContain(
                Permission.CREATE_PROPERTY, Permission.CREATE_UNIT, Permission.MANAGE_ESTATE,
                Permission.MANAGE_SALE_PIPELINE
        );
        assertThat(PMSPermission.HOMEOWNER.getPermissions()).doesNotContain(
                Permission.CREATE_PROPERTY, Permission.CREATE_UNIT, Permission.MANAGE_ESTATE,
                Permission.CREATE_SERVICE_CHARGE
        );
        assertThat(PMSPermission.BUYER.getPermissions()).doesNotContain(
                Permission.CREATE_PROPERTY, Permission.CREATE_UNIT, Permission.MANAGE_SALE_PIPELINE
        );
    }
}
