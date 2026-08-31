package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import static org.assertj.core.api.Assertions.assertThat;

class SokoControllerSecurityTest {
    @Test void controllerRequiresAuthenticationByDefault(){assertThat(SokoController.class.getAnnotation(PreAuthorize.class)).isNotNull().extracting(PreAuthorize::value).isEqualTo("isAuthenticated()");}
    @Test void onlyCatalogueMethodsAreExplicitlyPublic(){var publicMethods=java.util.Arrays.stream(SokoController.class.getDeclaredMethods()).filter(m->{var a=m.getAnnotation(PreAuthorize.class);return a!=null&&"permitAll()".equals(a.value());}).map(java.lang.reflect.Method::getName).toList();assertThat(publicMethods).containsExactlyInAnyOrder("catalog","store");}
}
