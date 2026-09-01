package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.property.listing.PropertyListingModels;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyListingControllerSecurityTest {
    @Test
    void publicationRequiresAdvertisePermission() throws Exception {
        var annotation = PropertyListingController.class
                .getMethod("publish", long.class, PropertyListingModels.PublishRequest.class)
                .getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains("ADVERTISE_UNIT");
    }

    @Test
    void moderationUsesDedicatedSuperAdminPermission() throws Exception {
        var annotation = PropertyListingController.class
                .getMethod("moderate", long.class, PropertyListingModels.ModerateRequest.class)
                .getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains("MANAGE_PROPERTY_LISTINGS").doesNotContain("ADVERTISE_UNIT");
    }
}
