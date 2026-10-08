package org.pms.silverocean.config;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.WorkspaceMembershipRepo;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.auth.JwtService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.soko.SokoService;
import org.pms.silverocean.service.subscription.SubscriptionEntitlementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(org.pms.silverocean.controller.SokoController.class)
@Import({SecurityConfig.class, SimpleCorsFilter.class, AccountActivationFilter.class})
class SecurityConfigSokoPublicTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private SokoService sokoService;
    @MockitoBean private I18NService i18NService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDao userDao;
    @MockitoBean private WorkspaceMembershipRepo workspaceMembershipRepo;
    @MockitoBean private SubscriptionEntitlementService subscriptionEntitlementService;

    @Test
    void anonymousCallerCanReadSokoGroceryCategories() throws Exception {
        mvc.perform(get("/soko/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("FRESH_PRODUCE"));
    }

    @Test
    void anonymousCallerStillCannotReadMerchantStoreData() throws Exception {
        mvc.perform(get("/soko/store/my"))
                .andExpect(status().isForbidden());
    }
}
