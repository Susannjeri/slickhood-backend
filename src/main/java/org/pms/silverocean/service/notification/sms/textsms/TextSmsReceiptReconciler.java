package org.pms.silverocean.service.notification.sms.textsms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.service.RestTemplateService;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.sms.SMSDao;
import org.pms.silverocean.service.notification.sms.textsms.wrappers.TextSMSDlrRequest;
import org.pms.silverocean.service.notification.sms.textsms.wrappers.TextSMSDlrResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

/** Durable receipt polling only: never resubmits a message or contacts a new recipient. */
@Service @RequiredArgsConstructor @Slf4j
public class TextSmsReceiptReconciler {
    private final SMSDao sms;
    private final NotificationDao notifications;
    private final RestTemplateService transport;
    private final ConfigService config;
    @Value("${notifications.textsms-receipt-checks-enabled:true}") private boolean enabled=true;
    @Value("${notifications.textsms-receipt-max-attempts:12}") private int maxAttempts=12;
    @Scheduled(fixedDelayString="${notifications.textsms-receipt-scan-ms:60000}")
    public void reconcile(){
        if(!enabled)return;
        org.pms.silverocean.service.config.ConfigDTO url,partner,key;
        try{
            url=config.getConfigByName(PMSConfigs.TEXT_DLR_URL).get();
            partner=config.getConfigByName(PMSConfigs.TEXT_SMS_PARTNER_ID).get();
            key=config.getConfigByName(PMSConfigs.TEXT_SMS_API_KEY).get();
        }catch(org.pms.silverocean.service.PMSCustomException missingConfig){return;}
        int limit=Math.max(1,Math.min(maxAttempts,24));
        LocalDateTime now=LocalDateTime.now();
        for(var receipt:sms.dueReceiptChecks(now)){
            if(receipt.getReceiptCheckAttempts()>=limit){sms.exhaustReceiptChecks(receipt.getId(),limit);continue;}
            if(!sms.claimReceiptCheck(receipt.getId(),now,limit))continue;
            try{
                var response=transport.sendPostRequest(url.stringValue(),new TextSMSDlrRequest(partner.stringValue(),key.stringValue(),receipt.getThirdPartyId(),receipt.getReceiptCheckAttempts()+1),null,TextSMSDlrResponse.class);
                if(response==null||response.responseCode()!=200||!receipt.getThirdPartyId().equals(response.messageId())||response.deliveryDescription()==null)continue;
                sms.recordProviderReceipt("TEXTSMS",receipt.getThirdPartyId(),Integer.toString(response.deliveryStatus()),response.deliveryDescription(),Integer.toString(response.deliveryNetworkId()),null)
                        .ifPresent(notifications::confirmDelivered);
                if (!"DeliveredToTerminal".equals(response.deliveryDescription())
                        && !"SentToNetwork".equals(response.deliveryDescription())) {
                    notifications.markDeliveryFailed(receipt.getNotificationId());
                }
            }catch(RuntimeException failure){
                log.warn("Receipt reconciliation deferred for SMS {} ({})",receipt.getId(),failure.getClass().getSimpleName());
            }
        }
    }
}
