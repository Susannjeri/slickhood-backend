package org.pms.silverocean.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.soko.SokoModels;
import org.pms.silverocean.service.soko.SokoRequests;
import org.pms.silverocean.service.soko.SokoService;
import org.pms.silverocean.config.ApiErrorHandler;
import org.pms.silverocean.service.soko.SokoDeliveryCodeLockedException;
import org.pms.silverocean.service.soko.SokoRiderAssignmentAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@ExtendWith(MockitoExtension.class)
class SokoControllerContractTest {
    @Mock private SokoService service;
    @Mock private I18NService i18n;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SokoController(service, i18n))
                .setControllerAdvice(new ApiErrorHandler(i18n)).build();
    }

    @Test
    void nonDispatchTransitionAcceptsAnEmptyJsonObject() throws Exception {
        when(service.transition(eq(19L), eq("CONFIRMED"), any(SokoRequests.Dispatch.class)))
                .thenReturn(mock(SokoModels.OrderDetail.class));

        mockMvc.perform(put("/soko/order/19/status")
                        .param("status", "CONFIRMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        ArgumentCaptor<SokoRequests.Dispatch> dispatch = ArgumentCaptor.forClass(SokoRequests.Dispatch.class);
        verify(service).transition(eq(19L), eq("CONFIRMED"), dispatch.capture());
        assertNull(dispatch.getValue().riderId());
        assertNull(dispatch.getValue().expectedArrivalTime());
    }

    @Test
    void cachedFormClientCanConfirmWithoutReceivingUnsupportedMediaType() throws Exception {
        when(service.transition(19L,"CONFIRMED",null)).thenReturn(mock(SokoModels.OrderDetail.class));

        mockMvc.perform(put("/soko/order/19/status")
                        .param("status","CONFIRMED")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(""))
                .andExpect(status().isOk());

        verify(service).transition(19L,"CONFIRMED",null);
    }

    @Test
    void checkoutRejectsMoreThanTwentyFiveLinesAndQuantityAboveOneHundred() throws Exception {
        String lines=java.util.stream.LongStream.rangeClosed(1,26)
                .mapToObj(id->"{\"productId\":"+id+",\"quantity\":1}")
                .collect(java.util.stream.Collectors.joining(","));
        String oversizedCart="{\"storeId\":2,\"items\":["+lines+"],\"deliveryMethod\":\"PICKUP\",\"customerPhone\":\"0712345678\"}";
        String oversizedQuantity="{\"storeId\":2,\"items\":[{\"productId\":5,\"quantity\":101}],\"deliveryMethod\":\"PICKUP\",\"customerPhone\":\"0712345678\"}";

        mockMvc.perform(post("/soko/order/checkout").header("Idempotency-Key","cart-lines").contentType(MediaType.APPLICATION_JSON).content(oversizedCart))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/soko/order/checkout").header("Idempotency-Key","cart-quantity").contentType(MediaType.APPLICATION_JSON).content(oversizedQuantity))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void publicRiderBoundaryReturnsPrivacyHeadersAndRoutesAllPersonalActions() throws Exception {
        var view=new SokoModels.PublicRiderAssignment("SOKO-1","DELIVERY_ASSIGNED","Fresh Corner","Market Road",
                java.time.ZonedDateTime.now().plusHours(1),1,List.of(new SokoModels.PublicRiderItem("Milk","litre",1)),false,null);
        when(service.publicRiderAssignment("secret")).thenReturn(view);
        when(service.acceptPublicRiderAssignment("secret")).thenReturn(view);
        when(service.collectPublicRiderAssignment("secret")).thenReturn(view);
        when(service.declinePublicRiderAssignment(eq("secret"),any())).thenReturn(new SokoModels.RiderAssignmentDecision("SOKO-1","DECLINED"));
        when(service.confirmPublicRiderDelivery(eq("secret"),any())).thenReturn(view);

        mockMvc.perform(get("/soko/public/rider-assignment").header("X-Soko-Rider-Token","secret"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store, max-age=0"))
                .andExpect(header().string("Referrer-Policy","no-referrer")).andExpect(jsonPath("$.data[0].orderReference").value("SOKO-1"));
        mockMvc.perform(put("/soko/public/rider-assignment/accept").header("X-Soko-Rider-Token","secret")).andExpect(status().isOk());
        mockMvc.perform(put("/soko/public/rider-assignment/collect").header("X-Soko-Rider-Token","secret")).andExpect(status().isOk());
        mockMvc.perform(put("/soko/public/rider-assignment/decline").header("X-Soko-Rider-Token","secret").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Unavailable\"}")) .andExpect(status().isOk());
        mockMvc.perform(put("/soko/public/rider-assignment/delivery/confirm").header("X-Soko-Rider-Token","secret").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}")) .andExpect(status().isOk());
        verify(service).acceptPublicRiderAssignment("secret");verify(service).collectPublicRiderAssignment("secret");verify(service).confirmPublicRiderDelivery(eq("secret"),any());
    }

    @Test
    void invalidExpiredRevokedAndMissingPublicBearersAreUniformGoneAndNeverCached() throws Exception {
        when(service.publicRiderAssignment(nullable(String.class))).thenThrow(new SokoRiderAssignmentAccessException());
        for(String token:new String[]{"invalid",null}){
            var request=get("/soko/public/rider-assignment");if(token!=null)request.header("X-Soko-Rider-Token",token);
            mockMvc.perform(request).andExpect(status().isGone())
                    .andExpect(header().string("Cache-Control","no-store, max-age=0"))
                    .andExpect(header().string("Referrer-Policy","no-referrer"));
        }
    }

    @Test
    void lockedDeliveryCodeHasStableLockedHttpStatus() throws Exception {
        when(service.confirmPublicRiderDelivery(eq("secret"),any())).thenThrow(new SokoDeliveryCodeLockedException());
        mockMvc.perform(put("/soko/public/rider-assignment/delivery/confirm").header("X-Soko-Rider-Token","secret")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isLocked());
    }
}
