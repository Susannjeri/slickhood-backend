package org.pms.silverocean.service.auth.roles.enums;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.controller.ServiceProviderController;
import org.pms.silverocean.service.sp.wrappers.MarketplaceFinanceRequest;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.pms.silverocean.service.auth.roles.enums.Permission.*;

class ServicesRolePermissionsTest {

    private static final Set<String> BUYER_FUNCTIONS = Set.of(
            VIEW_SP_CATEGORY_LIST, VIEW_SP_SERVICE, VIEW_SP_RATINGS,
            CREATE_SP_BOOKING, CANCEL_SP_BOOKING, VIEW_SP_BOOKING,
            RATE_SP_SERVICE, FILE_SP_COMPLAINT);

    private static final Set<String> SELLER_FUNCTIONS = Set.of(
            SETUP_SP_PROFILE, VIEW_SP_PROFILE, EDIT_SP_PROFILE,
            ADD_SP_SERVICE, EDIT_SP_SERVICE, REMOVE_SP_SERVICE,
            UPLOAD_SP_DOCUMENT, ADD_SP_REFEREE, EDIT_SP_REFEREE, REMOVE_SP_REFEREE,
            CONFIRM_SP_BOOKING, COMPLETE_SP_BOOKING);

    private static final Set<String> ADMIN_MUTATIONS = Set.of(
            APPROVE_SP_SERVICE, SUSPEND_SP_SERVICE, ASSIGN_SP_TIER,
            VERIFY_SP_DOCUMENT, VERIFY_SP_REFEREE,
            REVIEW_SP_COMPLAINT, RESOLVE_SP_COMPLAINT,
            BLACKLIST_SP, MANAGE_SP_CATEGORIES);

    @Test
    void buyerCapableRolesCanUseTheCompleteBuyerJourneyButNotSellerOrAdminFunctions() {
        for (PMSPermission role : Set.of(PMSPermission.MARKETPLACE_BUYER,PMSPermission.TENANT, PMSPermission.HOMEOWNER,
                PMSPermission.BUYER, PMSPermission.LANDLORD_PERMISSIONS, PMSPermission.PROPERTY_MANAGER,
                PMSPermission.ESTATE_MANAGER)) {
            assertThat(role.getPermissions())
                    .containsAll(BUYER_FUNCTIONS)
                    .doesNotContainAnyElementsOf(SELLER_FUNCTIONS)
                    .doesNotContainAnyElementsOf(ADMIN_MUTATIONS);
        }
    }

    @Test
    void marketplaceBuyerHasNoSellerPropertyOrAdministrativeCapability() {
        assertThat(PMSRole.MARKETPLACE_BUYER.isSelfAssignable()).isTrue();
        assertThat(PMSPermission.MARKETPLACE_BUYER.getPermissions())
                .containsAll(BUYER_FUNCTIONS)
                .doesNotContainAnyElementsOf(SELLER_FUNCTIONS)
                .doesNotContainAnyElementsOf(ADMIN_MUTATIONS)
                .doesNotContain(CREATE_PROPERTY,CREATE_UNIT,CREATE_ACCOUNT,RECORD_MANUAL_PAYMENT);
    }

    @Test
    void serviceProviderCanOperateBothSidesWithoutReceivingAdminFunctions() {
        assertThat(PMSPermission.SERVICE_PROVIDER.getPermissions())
                .containsAll(BUYER_FUNCTIONS)
                .containsAll(SELLER_FUNCTIONS)
                .doesNotContainAnyElementsOf(ADMIN_MUTATIONS);
    }

    @Test
    void marketplaceAdministrationAndFinanceRemainRoleSeparated() throws Exception {
        for (PMSPermission role : PMSPermission.values()) {
            if (role == PMSPermission.SUPER_ADMIN) {
                assertThat(role.getPermissions()).containsAll(ADMIN_MUTATIONS);
            } else {
                assertThat(role.getPermissions()).doesNotContainAnyElementsOf(ADMIN_MUTATIONS);
            }
        }

        assertThat(PMSPermission.FINANCE.getPermissions())
                .doesNotContainAnyElementsOf(BUYER_FUNCTIONS)
                .doesNotContainAnyElementsOf(SELLER_FUNCTIONS)
                .doesNotContainAnyElementsOf(ADMIN_MUTATIONS);

        PreAuthorize financeBoundary = ServiceProviderController.class
                .getMethod("updateBookingFinance", long.class, MarketplaceFinanceRequest.class)
                .getAnnotation(PreAuthorize.class);
        assertThat(financeBoundary).isNotNull();
        assertThat(financeBoundary.value()).isEqualTo("hasRole('FINANCE')");
    }
}
