package org.pms.silverocean.service.auth.roles.enums;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InsurancePermissionBoundaryTest {
 @Test void adviserMayPrepareButCannotApproveOrMoveMoney(){
  assertThat(PMSPermission.INSURANCE_ADVISER.getPermissions())
   .contains(Permission.REVIEW_INSURANCE_APPLICATIONS,Permission.MANAGE_INSURANCE_QUOTES,Permission.MANAGE_INSURANCE_CLAIMS)
   .doesNotContain(Permission.APPROVE_INSURANCE_QUOTES,Permission.VERIFY_INSURANCE_PAYMENTS,Permission.ISSUE_INSURANCE_POLICIES,Permission.MANAGE_INSURANCE_CATALOG);
 }
 @Test void managerOwnsApprovalPaymentIssuanceAndConfiguration(){
  assertThat(PMSPermission.INSURANCE_MANAGER.getPermissions()).contains(Permission.APPROVE_INSURANCE_QUOTES,
   Permission.VERIFY_INSURANCE_PAYMENTS,Permission.ISSUE_INSURANCE_POLICIES,Permission.MANAGE_INSURANCE_CATALOG,
   Permission.MANAGE_INSURANCE_PAYMENT_CONFIG,Permission.VIEW_INSURANCE_REPORTS);
 }
 @Test void insuranceRolesCannotBeSelfAssigned(){
  assertThat(PMSRole.INSURANCE_ADVISER.isSelfAssignable()).isFalse();
  assertThat(PMSRole.INSURANCE_MANAGER.isSelfAssignable()).isFalse();
 }
}
