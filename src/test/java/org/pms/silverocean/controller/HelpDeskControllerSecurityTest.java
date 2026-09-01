package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.helpdesk.HelpDeskModels;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class HelpDeskControllerSecurityTest {
    @Test
    void publicRegistrationHelpIsExplicitlyPublic() throws Exception {
        assertThat(HelpDeskController.class.getMethod("startGuest", HelpDeskModels.GuestStart.class, jakarta.servlet.http.HttpServletRequest.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("permitAll()");
        assertThat(HelpDeskController.class.getMethod("guestArticles")
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("permitAll()");
    }

    @Test
    void supportOperationsUseDedicatedLeastPrivilegePermissions() throws Exception {
        assertThat(HelpDeskController.class.getMethod("queue", org.springframework.data.domain.Pageable.class)
                .getAnnotation(PreAuthorize.class).value()).contains("view_helpdesk_queue").doesNotContain("list_users");
        assertThat(HelpDeskController.class.getMethod("reply", long.class, HelpDeskModels.AgentReply.class)
                .getAnnotation(PreAuthorize.class).value()).contains("manage_helpdesk_cases").doesNotContain("list_users");
        assertThat(HelpDeskController.class.getMethod("createArticle", HelpDeskModels.ArticleUpsert.class)
                .getAnnotation(PreAuthorize.class).value()).contains("manage_helpdesk_articles").doesNotContain("list_users");
    }
}
