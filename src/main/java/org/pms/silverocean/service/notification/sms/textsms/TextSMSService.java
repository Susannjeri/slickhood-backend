package org.pms.silverocean.service.notification.sms.textsms;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.database.pms.entities.SMS;
import org.pms.silverocean.service.RestTemplateService;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.sms.SMSDao;
import org.pms.silverocean.service.notification.sms.SmsProvider;
import org.pms.silverocean.service.notification.sms.textsms.wrappers.SmsResponseItem;
import org.pms.silverocean.service.notification.sms.textsms.wrappers.TestSMSStatusCode;
import org.pms.silverocean.service.notification.sms.textsms.wrappers.TextSMSDlrRequest;
import org.pms.silverocean.service.notification.sms.textsms.wrappers.TextSMSDlrResponse;
import org.pms.silverocean.service.notification.sms.textsms.wrappers.TextSMSRequest;
import org.pms.silverocean.service.notification.sms.textsms.wrappers.TextSMSResponse;
import org.pms.silverocean.service.threadpooling.PMSThreadPoolExecutorService;
import org.pms.silverocean.service.threadpooling.ThreadPoolBeans;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@DependsOn("configInitService")
@Slf4j
public class TextSMSService implements SmsProvider {
    private static final String TEXTSMS = "TEXTSMS";
    private static final String SENT_TO_NETWORK_STATUS = "SentToNetwork";
    private static final String DELIVERED_TO_TERMINAL_STATUS = "DeliveredToTerminal";
    private final SMSDao smsDao;
    private final NotificationDao notificationDao;
    private final RestTemplateService restTemplateService;
    private final ConfigService configService;

    private final PMSThreadPoolExecutorService DLR_EXECUTOR;

    public TextSMSService(ConfigService configService, SMSDao smsDao, NotificationDao notificationDao, RestTemplateService restTemplateService, ThreadPoolBeans threadPoolBeans) {
        this.smsDao = smsDao;
        this.notificationDao = notificationDao;
        this.restTemplateService = restTemplateService;
        this.configService = configService;
        DLR_EXECUTOR = threadPoolBeans.ioExecutorService(TEXTSMS + "-DLR");
    }

    public int executeSend(NotificationDTO notificationDTO, long notificationId) {
        SMS sms = new SMS();
        sms.setNotificationId(notificationId);
        sms.setActive(true);
        sms.setChannel(TEXTSMS);

        TextSMSRequest request = new TextSMSRequest(getPartnerId(), getApiKey(), notificationDTO.recipient(), notificationDTO.formattedMessage(), getSenderId());

        TextSMSResponse textSMSResponse = restTemplateService.sendPostRequest( configService.getConfigByName(PMSConfigs.TEXT_SMS_URL).get().stringValue(),
                request, null, TextSMSResponse.class);
        int statusCode = 0;
        for (SmsResponseItem responseItem : textSMSResponse.responses()) {
            log.debug("SMS Sent to recipients: {}", responseItem);
            sms.setStatus(String.valueOf(responseItem.responseCode()));
            sms.setDescription(responseItem.responseDescription());
            sms.setThirdPartyId(responseItem.thirdPartyMessageId());

            statusCode = responseItem.responseCode();
            int smsCostCents = configService.getConfigByName(PMSConfigs.TEXT_SMS_COST).get().intValue();
            double cost = ((double) smsCostCents / 100.0) * countSMSUnits(notificationDTO.formattedMessage());
            sms.setCost(cost);
            sms.setCurrency("KES");

            scheduleDeliveryReportCallback(new TextSMSDlrRequest(getPartnerId(), getApiKey(), responseItem.thirdPartyMessageId(), 1));
        }
        smsDao.saveSMS(sms);
        return statusCode;
    }

    private double countSMSUnits(String sms) {
        int l = sms.length();
        if (l <= 160) return 1;
        return Math.ceil(l / 153.0);
    }

    @Override
    public boolean isRetryable(int statusCode) {
        return TestSMSStatusCode.fromCode(statusCode).isRetryIfError();
    }

    @Override
    public boolean supports(String providerName) {
        if (StringUtils.isBlank(providerName)) return false;
        String normalizedInput = providerName.replaceAll("[^a-zA-Z0-9]", "");
        return TEXTSMS.equalsIgnoreCase(normalizedInput);
    }

    private void scheduleDeliveryReportCallback(TextSMSDlrRequest request) {
        if (request.retryCount() > 3) {
            log.error("Max Retry Count fetching DLR for ID {}: Count {}",  request.messageId(), request.messageId());
            updateFinalDLRStatus(request.messageId(), "0", "Max Retries fetching DLR", "0");
            return;
        }

        DLR_EXECUTOR.schedule(() -> sendDLRRequest(request), 5L * request.retryCount(), TimeUnit.MINUTES)
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        Throwable actualError = throwable.getCause() != null ? throwable.getCause() : throwable;
                        log.error("Error fetching DLR for ID {}: {}",  request.messageId(), actualError.getMessage());
                        scheduleDeliveryReportCallback(new TextSMSDlrRequest(getPartnerId(), getApiKey(), request.messageId(), request.retryCount() + 1));
                        return;
                    }

                    if (response != null) {
                        log.info("DLR Response for {}: Status {}",  request.messageId(), response.deliveryStatus());
                        // deliveryDescription: SentToNetwork, SenderName Blacklisted, DeliveredToTerminal
                        if (SENT_TO_NETWORK_STATUS.equals(response.deliveryDescription())) {
                            log.info("Status still pending for {}. Rescheduling...", request.messageId());
                            scheduleDeliveryReportCallback(new TextSMSDlrRequest(getPartnerId(), getApiKey(), request.messageId(), request.retryCount() + 1));
                        } else {
                            log.info("Final status reached for {}. Stopping checks.", request.messageId());
                            updateFinalDLRStatus(request.messageId(), Integer.toString(response.deliveryStatus()), response.deliveryDescription(), Integer.toString(response.deliveryNetworkId()));
                        }
                    }
                });
    }


    private TextSMSDlrResponse sendDLRRequest(TextSMSDlrRequest request) {
        return restTemplateService.sendPostRequest(configService.getConfigByName(PMSConfigs.TEXT_DLR_URL).get().stringValue(), request, null, TextSMSDlrResponse.class);
    }

    public void updateFinalDLRStatus(String id, String status, String deliveryDescription, String network) {
        smsDao.findByThirdPartyId(id)
                .ifPresent(smsEntity -> {
                    smsEntity.setStatus(status);
                    smsEntity.setDescription(deliveryDescription);
                    smsEntity.setNetwork(network);
                    smsEntity.setUpdatedOn(LocalDateTime.now());
                    smsDao.saveSMS(smsEntity);
                    updateNotification(smsEntity.getNotificationId(), deliveryDescription);
                });
    }

    private void updateNotification(long notificationId, String deliveryDescription) {
        notificationDao.findById(notificationId)
                .ifPresent(notification -> {
                    notification.setUpdatedOn(LocalDateTime.now());
                    notification.setDelivered(DELIVERED_TO_TERMINAL_STATUS.equals(deliveryDescription));
                    notificationDao.save(notification);
                });
    }

    private String getPartnerId() {
        return configService.getConfigByName(PMSConfigs.TEXT_SMS_PARTNER_ID)
                .get().stringValue();
    }

    private String getApiKey() {
        return configService.getConfigByName(PMSConfigs.TEXT_SMS_API_KEY)
                .get().stringValue();
    }

    private String getSenderId() {
        return configService.getConfigByName(PMSConfigs.SMS_SENDERNAME)
                .get().stringValue();
    }
}