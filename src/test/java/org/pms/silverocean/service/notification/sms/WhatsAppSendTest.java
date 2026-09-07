package org.pms.silverocean.service.notification.sms;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.RestTemplateService;
import org.pms.silverocean.service.config.ConfigDTO;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.notification.sms.whatsapp.WhatsAppService;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.*;
import org.pms.silverocean.database.pms.entities.SMS;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class WhatsAppSendTest {
    final RestTemplateService http=mock(RestTemplateService.class);
    final ConfigService config=mock(ConfigService.class);
    final SMSDao messages=mock(SMSDao.class);
    final WhatsAppService service=new WhatsAppService(http,config,messages);
    void setting(PMSConfigs key,String value){when(config.getConfigByName(key)).thenReturn(()->new ConfigDTO(1,key.name(),value,0,true));}
    void settings(String url){setting(PMSConfigs.WHATSAPP_URL,url);setting(PMSConfigs.WHATSAPP_BUSINESS_PHONENUMBER_ID,"1234");setting(PMSConfigs.WHATSAPP_ACCESS_TOKEN,"synthetic-token");}
    @Test void rejectsTokenExfiltrationToAnUntrustedEndpoint() {
        settings("https://attacker.example/%s/messages");
        assertThrows(IllegalStateException.class,()->service.sendUtilityMessage("254700000000","Test","Message",1));verifyNoInteractions(http,messages);
    }
    @Test void noMessageReceiptIsNotReportedAsSuccess() {
        settings("https://graph.facebook.com/v22.0/%s/messages");
        assertThrows(IllegalStateException.class,()->service.sendUtilityMessage("254700000000","Test","Message",1));verifyNoInteractions(messages);
    }
    @Test void acceptedIsNotDeliveredAndInternationalNumberIsRequired() throws Exception {
        settings("https://graph.facebook.com/v22.0/%s/messages");
        when(http.sendPostRequest(anyString(),any(),any(),eq(WhatsAppResponse.class)))
                .thenReturn(new WhatsAppResponse("whatsapp",List.of(),List.of(new WAMessage("message-id","accepted"))));
        service.sendUtilityMessage("+254700000000","Test","Message",1);
        var saved=org.mockito.ArgumentCaptor.forClass(SMS.class);verify(messages).saveSMS(saved.capture());
        assertEquals("accepted",saved.getValue().getStatus());
        assertThrows(IllegalArgumentException.class,()->service.sendUtilityMessage("0700000000","Test","Message",1));
    }
    @Test void utilityTemplateCannotSilentlyReplaceAuthenticationSms() {
        assertThrows(IllegalStateException.class,()->service.executeSend(new NotificationDTO("test OTP","254700000000",NotificationType.OTP_SMS),1));
        verifyNoInteractions(http,messages,config);
    }
}
