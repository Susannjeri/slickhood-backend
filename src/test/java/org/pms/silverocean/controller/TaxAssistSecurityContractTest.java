package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class TaxAssistSecurityContractTest {
    @Test
    void allTaxEndpointsRequireAuthenticationAndAdministrationRequiresSuperAdmin() throws Exception {
        PreAuthorize root = TaxAssistController.class.getAnnotation(PreAuthorize.class);
        assertThat(root).isNotNull();
        assertThat(root.value()).isEqualTo("isAuthenticated()");
        for (String method : new String[]{"rules", "createRule", "closeRule", "adminConnections", "review"}) {
            PreAuthorize guard = java.util.Arrays.stream(TaxAssistController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(method)).findFirst().orElseThrow().getAnnotation(PreAuthorize.class);
            assertThat(guard).as(method).isNotNull();
            assertThat(guard.value()).as(method).isEqualTo("hasRole('SUPER_ADMIN')");
        }
    }
}
