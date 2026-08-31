package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import java.lang.reflect.Method;
import static org.assertj.core.api.Assertions.assertThat;

class WealthControllerSecurityTest {
    @Test void allWealthEndpointsRequireAnExplicitAuthority(){for(Method method:WealthController.class.getDeclaredMethods()){if(method.isSynthetic()||method.getName().equals("ok"))continue;assertThat(method.getAnnotation(PreAuthorize.class)).as(method.getName()).isNotNull();}}
    @Test void vaultMutationsRequireVaultAuthority() throws Exception {assertThat(WealthController.class.getMethod("personalUpload",Long.class,String.class,java.time.LocalDate.class,java.time.LocalDate.class,String.class,org.springframework.web.multipart.MultipartFile.class).getAnnotation(PreAuthorize.class).value()).contains("MANAGE_WEALTH_VAULT");assertThat(WealthController.class.getMethod("archiveDocument",long.class).getAnnotation(PreAuthorize.class).value()).contains("MANAGE_WEALTH_VAULT");}
}
