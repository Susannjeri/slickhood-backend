package org.pms.silverocean.controller;
import org.junit.jupiter.api.*;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.notification.NotificationReportService;
import org.pms.silverocean.service.notification.preferences.NotificationPreferenceService;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationControllerSecurityTest {
    @Configuration @EnableMethodSecurity static class SecurityConfig {
        @Bean NotificationReportService reports(){return mock(NotificationReportService.class);}
        @Bean I18NService i18n(){return mock(I18NService.class);}
        @Bean NotificationPreferenceService preferences(){return mock(NotificationPreferenceService.class);}
        @Bean NotificationController controller(NotificationReportService reports,I18NService i18n,NotificationPreferenceService preferences){return new NotificationController(reports,i18n,preferences);}
    }
    AnnotationConfigApplicationContext context;
    @BeforeEach void open(){context=new AnnotationConfigApplicationContext(SecurityConfig.class);}
    @AfterEach void close(){SecurityContextHolder.clearContext();context.close();}
    void login(String... authorities){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("fixture-user","unused",Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));}
    @Test void customerDeliveryPermissionDoesNotGrantGlobalRecipientAccess(){login("ROLE_ESTATE_MANAGER",Permission.VIEW_NOTIFICATIONS);var controller=context.getBean(NotificationController.class);assertThrows(AccessDeniedException.class,()->controller.getNotifications(PageRequest.of(0,10),Optional.empty()));assertThrows(AccessDeniedException.class,()->controller.viewSMSLogs(PageRequest.of(0,10),Optional.empty()));verifyNoInteractions(context.getBean(NotificationReportService.class));}
    @Test void globalMonitorRequiresBothAdminRoleAndDeliveryAuthority(){login("ROLE_SUPER_ADMIN");assertThrows(AccessDeniedException.class,()->context.getBean(NotificationController.class).getNotifications(PageRequest.of(0,10),Optional.empty()));}
    @Test void approvedSuperadminCanViewGlobalDeliveryRecords(){login("ROLE_SUPER_ADMIN",Permission.VIEW_NOTIFICATIONS);var reports=context.getBean(NotificationReportService.class);when(reports.getNotifications(any(),anyString())).thenReturn(Page.empty());assertEquals(200,context.getBean(NotificationController.class).getNotifications(PageRequest.of(0,10),Optional.empty()).getStatusCode().value());}
    @Test void everyAuthenticatedProfileRetainsItsOwnInbox(){login("ROLE_TENANT");when(context.getBean(NotificationReportService.class).getMyUnreadNotificationCount()).thenReturn(2L);var response=context.getBean(NotificationController.class).getMyUnreadNotificationCount();assertEquals(200,response.getStatusCode().value());assertEquals("no-store",response.getHeaders().getCacheControl());}
}
