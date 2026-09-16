package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import java.lang.reflect.Method;
import static org.assertj.core.api.Assertions.assertThat;

class AffiliateControllerSecurityTest {
    @Test void newAdminAndOwnerEndpointsKeepTheirAccessBoundaries(){for(Method method:AffiliateAdminController.class.getDeclaredMethods()){if(method.getAnnotationsByType(org.springframework.web.bind.annotation.GetMapping.class).length==0&&method.getAnnotationsByType(org.springframework.web.bind.annotation.PutMapping.class).length==0)continue;var boundary=method.getAnnotation(PreAuthorize.class);assertThat(boundary).as(method.getName()).isNotNull();assertThat(boundary.value()).isEqualTo(method.getName().startsWith("own")?"hasAnyRole('AFFILIATE','SUPER_ADMIN')":"hasRole('SUPER_ADMIN')");}}
    @Test void everyAffiliateEndpointDeclaresItsAccessBoundary(){for(Method method:AffiliateController.class.getDeclaredMethods()){if(method.isSynthetic()||method.getName().equals("ok"))continue;assertThat(method.getAnnotation(PreAuthorize.class)).as(method.getName()).isNotNull();}}
    @Test void onlyReferralResolutionIsPublic() throws Exception {assertThat(AffiliateController.class.getMethod("resolve",String.class).getAnnotation(PreAuthorize.class).value()).isEqualTo("permitAll()");assertThat(AffiliateController.class.getMethod("payouts").getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('SUPER_ADMIN')");assertThat(AffiliateController.class.getMethod("decide",long.class,org.pms.silverocean.service.affiliate.AffiliateModels.PayoutDecision.class).getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('SUPER_ADMIN')");}
}
