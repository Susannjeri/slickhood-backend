package org.pms.silverocean.service.notification.sms;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.SMS;
import org.pms.silverocean.service.RestTemplateService;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.ConfigDTO;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.sms.textsms.TextSmsReceiptReconciler;
import org.pms.silverocean.service.notification.sms.textsms.wrappers.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TextSmsReceiptReconcilerTest {
    SMSDao sms=mock(SMSDao.class);NotificationDao notifications=mock(NotificationDao.class);
    RestTemplateService transport=mock(RestTemplateService.class);ConfigService config=mock(ConfigService.class);
    TextSmsReceiptReconciler worker=new TextSmsReceiptReconciler(sms,notifications,transport,config);
    void configured(){for(var name:List.of(PMSConfigs.TEXT_DLR_URL,PMSConfigs.TEXT_SMS_PARTNER_ID,PMSConfigs.TEXT_SMS_API_KEY))when(config.getConfigByName(name)).thenReturn(()->new ConfigDTO(1,name.name(),name==PMSConfigs.TEXT_DLR_URL?"https://fixture.example/dlr":"test-only",0,true));}
    SMS receipt(){SMS r=new SMS();r.setId(4L);r.setThirdPartyId("provider-id");return r;}
    void due(SMS r){configured();when(sms.dueReceiptChecks(any())).thenReturn(List.of(r));}
    TextSMSDlrResponse delivered(String id){return new TextSMSDlrResponse(200,id,"Success",200,"DeliveredToTerminal","",1,"");}
    @Test void claimedReceiptIsPolledWithoutSendingAnotherSms(){
        due(receipt());when(sms.claimReceiptCheck(eq(4L),any(),eq(12))).thenReturn(true);
        when(transport.sendPostRequest(eq("https://fixture.example/dlr"),any(TextSMSDlrRequest.class),isNull(),eq(TextSMSDlrResponse.class))).thenReturn(delivered("provider-id"));
        when(sms.recordProviderReceipt(eq("TEXTSMS"),eq("provider-id"),anyString(),eq("DeliveredToTerminal"),anyString(),isNull())).thenReturn(Optional.of(7L));
        worker.reconcile();verify(notifications).confirmDelivered(7L);
        verify(transport).sendPostRequest(anyString(),argThat((TextSMSDlrRequest r)->"provider-id".equals(r.messageId())),isNull(),eq(TextSMSDlrResponse.class));
    }
    @Test void anotherWorkersClaimPreventsADuplicateCheck(){due(receipt());worker.reconcile();verifyNoInteractions(transport,notifications);}
    @Test void mismatchedProviderReceiptCannotMarkNotificationDelivered(){due(receipt());when(sms.claimReceiptCheck(eq(4L),any(),eq(12))).thenReturn(true);when(transport.sendPostRequest(anyString(),any(TextSMSDlrRequest.class),isNull(),eq(TextSMSDlrResponse.class))).thenReturn(delivered("wrong-id"));worker.reconcile();verify(sms,never()).recordProviderReceipt(anyString(),anyString(),anyString(),anyString(),anyString(),any());verifyNoInteractions(notifications);}
    @Test void exhaustedChecksRequireReconciliationInsteadOfResending(){SMS r=receipt();r.setReceiptCheckAttempts(12);due(r);worker.reconcile();verify(sms).exhaustReceiptChecks(4L,12);verifyNoInteractions(transport,notifications);}
    @Test void transportFailureLeavesTheDurableLeaseForLaterRetry(){due(receipt());when(sms.claimReceiptCheck(eq(4L),any(),eq(12))).thenReturn(true);when(transport.sendPostRequest(anyString(),any(TextSMSDlrRequest.class),isNull(),eq(TextSMSDlrResponse.class))).thenThrow(new IllegalStateException("fixture network failure"));assertDoesNotThrow(worker::reconcile);verifyNoInteractions(notifications);}
}
