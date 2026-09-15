package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.soko.SokoRiderKycService;
import org.springframework.security.access.prepost.PreAuthorize;
import static org.assertj.core.api.Assertions.assertThat;

class SokoRiderKycControllerSecurityTest {
    @Test void allRiderEvidenceRoutesRequireAuthentication(){
        assertThat(SokoRiderKycController.class.getAnnotation(PreAuthorize.class).value()).isEqualTo("isAuthenticated()");
    }
    @Test void privateRiderEvidenceRequiresSuperadminReviewAuthority()throws Exception{
        assertThat(SokoRiderKycController.class.getMethod("admin",long.class).getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('SUPER_ADMIN')");
        assertThat(SokoRiderKycController.class.getMethod("review",long.class,SokoRiderKycService.Review.class).getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('SUPER_ADMIN')");
    }
}
