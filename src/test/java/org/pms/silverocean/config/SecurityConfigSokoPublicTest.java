package org.pms.silverocean.config;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.WorkspaceMembershipRepo;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.auth.JwtService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.soko.SokoService;
import org.pms.silverocean.service.subscription.SubscriptionEntitlementService;
import org.springframework.data.domain.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

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
    void anonymousCallerCanBrowseSellerDirectory() throws Exception {
        when(sokoService.sellers(any(),isNull(),isNull())).thenReturn(Page.empty());
        mvc.perform(get("/soko/catalog/sellers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void anonymousCallerCannotReadPrivateDeliveryDestinations() throws Exception {
        mvc.perform(get("/soko/delivery-destinations"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCallerStillCannotReadMerchantStoreData() throws Exception {
        mvc.perform(get("/soko/store/my"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousAndStaleSessionBrowsersCanUseAssignmentScopedRiderBearer() throws Exception {
        var view=new org.pms.silverocean.service.soko.SokoModels.PublicRiderAssignment("SOKO-1","DELIVERY_ASSIGNED",
                "Fresh Corner","Market Road",java.time.ZonedDateTime.now().plusHours(1),0,java.util.List.of(),false,null);
        when(sokoService.publicRiderAssignment("assignment-secret")).thenReturn(view);
        when(sokoService.acceptPublicRiderAssignment("assignment-secret")).thenReturn(view);

        mvc.perform(get("/soko/public/rider-assignment")
                        .header("Authorization","Bearer stale-session")
                        .header("X-Soko-Rider-Token","assignment-secret"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].orderReference").value("SOKO-1"));
        mvc.perform(put("/soko/public/rider-assignment/accept")
                        .header("Authorization","Bearer stale-session")
                        .header("X-Soko-Rider-Token","assignment-secret"))
                .andExpect(status().isOk());

        verifyNoInteractions(jwtService);
    }

    @Test
    void anonymousCallerCannotRotateMerchantAssignmentLink() throws Exception {
        mvc.perform(put("/soko/order/9/rider/assignment-link/resend"))
                .andExpect(status().isForbidden());
    }
}
