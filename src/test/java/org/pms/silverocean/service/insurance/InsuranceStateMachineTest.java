package org.pms.silverocean.service.insurance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InsuranceStateMachineTest {
    @Test
    void paymentAndPolicyStatesCannotRegressToQuotation() {
        assertThat(InsuranceStateMachine.canReceiveQuote("PAYMENT_PENDING")).isFalse();
        assertThat(InsuranceStateMachine.canReceiveQuote("PAYMENT_VERIFIED")).isFalse();
        assertThat(InsuranceStateMachine.canReceiveQuote("POLICY_ISSUED")).isFalse();
    }

    @Test
    void customerWithdrawalStopsWhenFinancialCommitmentStarts() {
        assertThat(InsuranceStateMachine.canCustomerWithdraw("QUOTED")).isTrue();
        assertThat(InsuranceStateMachine.canCustomerWithdraw("CUSTOMER_SELECTED")).isFalse();
        assertThat(InsuranceStateMachine.canCustomerWithdraw("PAYMENT_PENDING")).isFalse();
    }

    @Test
    void claimsAndLapsedRenewalsStayClosedButRenewedPoliciesCanStartTheirNextTerm() {
        assertThat(InsuranceStateMachine.canMoveClaim("CLOSED", "ACKNOWLEDGED")).isFalse();
        assertThat(InsuranceStateMachine.canMoveRenewal("RENEWED", "CONTACTED")).isTrue();
        assertThat(InsuranceStateMachine.canMoveRenewal("LAPSED", "CONTACTED")).isFalse();
    }
}
