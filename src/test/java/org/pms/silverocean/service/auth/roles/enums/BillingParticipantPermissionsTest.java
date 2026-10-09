package org.pms.silverocean.service.auth.roles.enums;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class BillingParticipantPermissionsTest {

    @ParameterizedTest
    @EnumSource(value = PMSPermission.class, names = {
            "SERVICE_PROVIDER", "SALES_AGENT", "ASSET_PORTFOLIO_MANAGER", "BUYER"
    })
    void billingParticipantsCanReadTheirParticipantScopedRecords(PMSPermission rolePermissions) {
        assertThat(rolePermissions.getPermissions()).contains(
                Permission.VIEW_INVOICE_LIST,
                Permission.VIEW_INVOICE_PDF,
                Permission.VIEW_PAYMENT_LIST
        );
    }

    @ParameterizedTest
    @EnumSource(value = PMSPermission.class, names = {
            "SERVICE_PROVIDER", "SALES_AGENT", "ASSET_PORTFOLIO_MANAGER", "BUYER"
    })
    void billingParticipantsCannotReconcilePaymentsManually(PMSPermission rolePermissions) {
        assertThat(rolePermissions.getPermissions())
                .doesNotContain(Permission.RECORD_MANUAL_PAYMENT);
    }
}
