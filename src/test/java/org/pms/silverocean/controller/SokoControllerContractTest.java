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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SokoControllerContractTest {
    @Mock private SokoService service;
    @Mock private I18NService i18n;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SokoController(service, i18n)).build();
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
}
