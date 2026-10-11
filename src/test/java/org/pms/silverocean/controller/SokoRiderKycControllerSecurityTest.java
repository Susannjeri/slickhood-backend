package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.soko.SokoRiderKycService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import static org.assertj.core.api.Assertions.assertThat;

class SokoRiderKycControllerSecurityTest {
    @Test void allRiderEvidenceRoutesRequireAuthentication(){
        assertThat(SokoRiderKycController.class.getAnnotation(PreAuthorize.class).value()).isEqualTo("isAuthenticated()");
    }
    @Test void riderSelfServiceRejectsInternalActiveRoles()throws Exception{
        String boundary="isAuthenticated() and !hasAnyRole('SUPER_ADMIN','SUPPORT','SALES_MARKETING','FINANCE','INSURANCE_ADVISER','INSURANCE_MANAGER')";
        assertThat(SokoRiderKycController.class.getMethod("own").getAnnotation(PreAuthorize.class).value()).isEqualTo(boundary);
        assertThat(SokoRiderKycController.class.getMethod("upload",String.class,MultipartFile.class).getAnnotation(PreAuthorize.class).value()).isEqualTo(boundary);
    }
    @Test void privateRiderEvidenceRequiresSuperadminReviewAuthority()throws Exception{
        assertThat(SokoRiderKycController.class.getMethod("admin",long.class).getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('SUPER_ADMIN')");
        assertThat(SokoRiderKycController.class.getMethod("review",long.class,SokoRiderKycService.Review.class).getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('SUPER_ADMIN')");
    }
}
