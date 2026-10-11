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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
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
    void anonymousCallerCanReachOnlyTheExactPublicCatalogueRoutes() throws Exception {
        when(sokoService.catalog(any(),isNull(),isNull(),isNull(),isNull(),isNull(),isNull(),isNull(),isNull()))
                .thenReturn(Page.empty());
        when(sokoService.sellers(any(),isNull(),isNull())).thenReturn(Page.empty());
        var store=new org.pms.silverocean.service.soko.SokoModels.PublicStore(2L,"Fresh Corner","Groceries",
                "+254712345678","Market Road",-1.28,36.82,java.math.BigDecimal.TEN,
                true,true,new java.math.BigDecimal("150"),"KES");
        when(sokoService.storeDetail(2L)).thenReturn(new org.pms.silverocean.service.soko.SokoModels.StoreDetail(store,List.of()));

        mvc.perform(get("/soko/catalog")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        mvc.perform(get("/soko/catalog/sellers")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        mvc.perform(get("/soko/catalog/2")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].store.name").value("Fresh Corner"));
        mvc.perform(get("/soko/categories")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].code").value("FRESH_PRODUCE"));

        verify(sokoService).catalog(any(),isNull(),isNull(),isNull(),isNull(),isNull(),isNull(),isNull(),isNull());
        verify(sokoService).sellers(any(),isNull(),isNull());
        verify(sokoService).storeDetail(2L);
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

    @Test
    void anonymousCallerIsDeniedAcrossBuyerSellerLinkedRiderFinanceAndAdminBoundaries() throws Exception {
        mvc.perform(get("/soko/order/my")).andExpect(status().isForbidden());
        mvc.perform(get("/soko/store/my")).andExpect(status().isForbidden());
        mvc.perform(get("/soko/rider/assignments")).andExpect(status().isForbidden());
        mvc.perform(get("/soko/finance/refunds")).andExpect(status().isForbidden());
        mvc.perform(get("/soko/admin/summary")).andExpect(status().isForbidden());

        verifyNoInteractions(sokoService);
    }

    @Test
    void anonymousPrivateTokenRiderCanReachEveryAssignmentScopedActionWithoutSessionAuthentication() throws Exception {
        var view=new org.pms.silverocean.service.soko.SokoModels.PublicRiderAssignment("SOKO-2","DISPATCHED",
                "Fresh Corner","Market Road",java.time.ZonedDateTime.now().plusHours(1),1,
                List.of(new org.pms.silverocean.service.soko.SokoModels.PublicRiderItem("Milk","litre",1)),false,null);
        when(sokoService.publicRiderAssignment("private-token")).thenReturn(view);
        when(sokoService.acceptPublicRiderAssignment("private-token")).thenReturn(view);
        when(sokoService.collectPublicRiderAssignment("private-token")).thenReturn(view);
        when(sokoService.declinePublicRiderAssignment(org.mockito.ArgumentMatchers.eq("private-token"),any()))
                .thenReturn(new org.pms.silverocean.service.soko.SokoModels.RiderAssignmentDecision("SOKO-2","DECLINED"));
        when(sokoService.uploadPublicRiderDeliveryProof(org.mockito.ArgumentMatchers.eq("private-token"),any())).thenReturn(view);
        when(sokoService.confirmPublicRiderDelivery(org.mockito.ArgumentMatchers.eq("private-token"),any())).thenReturn(view);
        when(sokoService.failPublicRiderDelivery(org.mockito.ArgumentMatchers.eq("private-token"),any())).thenReturn(view);

        mvc.perform(get("/soko/public/rider-assignment").header("X-Soko-Rider-Token","private-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].orderReference").value("SOKO-2"));
        mvc.perform(put("/soko/public/rider-assignment/accept").header("X-Soko-Rider-Token","private-token"))
                .andExpect(status().isOk());
        mvc.perform(put("/soko/public/rider-assignment/collect").header("X-Soko-Rider-Token","private-token"))
                .andExpect(status().isOk());
        mvc.perform(put("/soko/public/rider-assignment/decline").header("X-Soko-Rider-Token","private-token")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"reason\":\"Unavailable\"}"))
                .andExpect(status().isOk());
        mvc.perform(multipart(HttpMethod.PUT,"/soko/public/rider-assignment/delivery/proof")
                        .file(new MockMultipartFile("proof","handover.png","image/png",new byte[]{1,2,3}))
                        .header("X-Soko-Rider-Token","private-token"))
                .andExpect(status().isOk());
        mvc.perform(put("/soko/public/rider-assignment/delivery/confirm").header("X-Soko-Rider-Token","private-token")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/soko/public/rider-assignment/delivery/fail").header("X-Soko-Rider-Token","private-token")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"reason\":\"Customer unavailable\"}"))
                .andExpect(status().isOk());

        verifyNoInteractions(jwtService);
        verify(sokoService).publicRiderAssignment("private-token");
        verify(sokoService).acceptPublicRiderAssignment("private-token");
        verify(sokoService).collectPublicRiderAssignment("private-token");
        verify(sokoService).declinePublicRiderAssignment(org.mockito.ArgumentMatchers.eq("private-token"),any());
        verify(sokoService).uploadPublicRiderDeliveryProof(org.mockito.ArgumentMatchers.eq("private-token"),any());
        verify(sokoService).confirmPublicRiderDelivery(org.mockito.ArgumentMatchers.eq("private-token"),any());
        verify(sokoService).failPublicRiderDelivery(org.mockito.ArgumentMatchers.eq("private-token"),any());
    }
}
