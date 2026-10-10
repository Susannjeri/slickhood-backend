package org.pms.silverocean.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.config.ApiErrorHandler;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.soko.SokoRiderKycService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SokoRiderKycControllerContractTest {
    private static final String NO_LINKED_ACCOUNT="This rider does not yet have a phone-confirmed linked SlickHood account.";
    @Mock private SokoRiderKycService service;
    @Mock private I18NService i18n;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp(){
        mockMvc=MockMvcBuilders.standaloneSetup(new SokoRiderKycController(service))
                .setControllerAdvice(new ApiErrorHandler(i18n))
                .build();
    }

    @Test
    void noLinkedAccountIsReturnedAsAReadableApiError()throws Exception{
        when(service.adminChecklist(7L)).thenThrow(new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,NO_LINKED_ACCOUNT));
        when(i18n.getLocalizedMessage(ResponseCode.INVALID_FIELD_DATA)).thenReturn("Invalid field data.");

        mockMvc.perform(get("/soko/admin/riders/7/kyc"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data[0]").value(NO_LINKED_ACCOUNT));

        verify(service).adminChecklist(7L);
    }
}
