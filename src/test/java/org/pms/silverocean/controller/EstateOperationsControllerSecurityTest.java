package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class EstateOperationsControllerSecurityTest {
    @Test
    void everyEstateOperationEndpointHasAnExplicitPermissionBoundary() {
        var endpointMethods = Arrays.stream(EstateOperationsController.class.getDeclaredMethods())
                .filter(method -> Arrays.stream(method.getAnnotations()).anyMatch(annotation ->
                        annotation.annotationType().getPackageName().equals("org.springframework.web.bind.annotation")))
                .toList();

        assertThat(endpointMethods).isNotEmpty();
        assertThat(endpointMethods).allSatisfy(method -> assertThat(method.getAnnotation(PreAuthorize.class))
                .as(method.getName()).isNotNull());
    }
}
