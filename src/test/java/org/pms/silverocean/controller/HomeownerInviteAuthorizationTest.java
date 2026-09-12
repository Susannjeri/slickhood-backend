package org.pms.silverocean.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.EmailOccupantInviteDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.invites.InviteService;
import org.pms.silverocean.service.invites.InviteTokenInspection;
import org.pms.silverocean.service.invites.InviteType;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/** Exercise the actual method-security proxy; UI permissions alone are not authorization. */
class HomeownerInviteAuthorizationTest {
    AnnotationConfigApplicationContext context;
    InviteController controller;
    InviteService invitations;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean InviteService invitations() { return mock(InviteService.class); }
        @Bean InviteController controller(InviteService service) {
            return new InviteController(service, mock(I18NService.class));
        }
    }

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(InviteController.class);
        invitations = context.getBean(InviteService.class);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }
    void authenticate(String... permissions) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "estate-staff", "unused", AuthorityUtils.createAuthorityList(permissions)));
    }
    EmailOccupantInviteDTO request(InviteType type) {
        return new EmailOccupantInviteDTO(type, 77L, "homeowner@example.test", null, null);
    }

    @Test void estateManagerCanEmailHomeownerWithoutGenericStaffInvitePowers() {
        authenticate(Permission.MANAGE_ESTATE);
        controller.createAndEmailOccupantInvite(request(InviteType.HOMEOWNER));
        verify(invitations).createAndSendEmailInvite(InviteType.HOMEOWNER, 77L, "homeowner@example.test", null, null);
    }
    @Test void estatePermissionDoesNotPermitTenantInvites() {
        authenticate(Permission.MANAGE_ESTATE);
        assertThrows(AccessDeniedException.class, () -> controller.createAndEmailOccupantInvite(request(InviteType.TENANT)));
        verifyNoInteractions(invitations);
    }
    @Test void estatePermissionDoesNotPermitStaffInvites() {
        authenticate(Permission.MANAGE_ESTATE);
        assertThrows(AccessDeniedException.class, () -> controller.createAndEmailOccupantInvite(request(InviteType.PROPERTY_MANAGER)));
        verifyNoInteractions(invitations);
    }
    @Test void estateViewerCannotInviteHomeowners() {
        authenticate(Permission.VIEW_ESTATE);
        assertThrows(AccessDeniedException.class, () -> controller.createAndEmailOccupantInvite(request(InviteType.HOMEOWNER)));
        verifyNoInteractions(invitations);
    }
    @Test void rentalInviteStillRequiresBothExistingPermissions() {
        authenticate(Permission.CREATE_INVITE);
        assertThrows(AccessDeniedException.class, () -> controller.createAndEmailOccupantInvite(request(InviteType.TENANT)));
        authenticate(Permission.CREATE_INVITE, Permission.SHARE_INVITE);
        controller.createAndEmailOccupantInvite(request(InviteType.TENANT));
        verify(invitations).createAndSendEmailInvite(InviteType.TENANT, 77L, "homeowner@example.test", null, null);
    }

    @Test void publicInspectionUsesTheReadOnlyServicePath() {
        var expiresAt = java.time.LocalDateTime.now().plusDays(1);
        when(invitations.inspectToken("still-valid")).thenReturn(new InviteTokenInspection("TENANT", expiresAt, 86_400));

        var response = controller.inspectInviteToken("still-valid");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(ResponseCode.VALID_INVITE_LINK.getCode(), response.getBody().getCode());
        verify(invitations).inspectToken("still-valid");
        verify(invitations, never()).validateToken(anyString());
    }
}
