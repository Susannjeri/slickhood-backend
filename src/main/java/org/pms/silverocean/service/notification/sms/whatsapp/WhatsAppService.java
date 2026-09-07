package org.pms.silverocean.service.notification.sms.whatsapp;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.database.pms.entities.SMS;
import org.pms.silverocean.service.RestTemplateService;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.sms.SMSDao;
import org.pms.silverocean.service.notification.sms.SmsProvider;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.WAMessage;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.WhatsAppResponse;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.request.Component;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.request.Language;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.request.Parameter;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.request.Template;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.request.WhatsAppRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.List;

@Service @Slf4j
public class WhatsAppService implements SmsProvider {

    private static final String WHATS_APP = "WHATS_APP";
    private final RestTemplateService restTemplateService;
    private final ConfigService configService;
    private final SMSDao smsDao;
    @org.springframework.beans.factory.annotation.Value("${whatsapp.utility-template:general_message}")
    private String utilityTemplate = "general_message";
    @org.springframework.beans.factory.annotation.Value("${whatsapp.template-language:en}")
    private String templateLanguage = "en";

    public WhatsAppService(RestTemplateService restTemplateService, ConfigService configService, SMSDao smsDao) {
        this.restTemplateService = restTemplateService;
        this.configService = configService;
        this.smsDao = smsDao;
    }


    public void sendUtilityMessage(String recipientPhone, String userName, String text, long notificationId) {
        SMS sms = new SMS();
        sms.setNotificationId(notificationId);
        sms.setActive(true);
        sms.setChannel(WHATS_APP);

        String apiUrl = configService.getConfigByName(PMSConfigs.WHATSAPP_URL).get().stringValue();
        String phoneNumberId = configService.getConfigByName(PMSConfigs.WHATSAPP_BUSINESS_PHONENUMBER_ID).get().stringValue();
        String accessToken = configService.getConfigByName(PMSConfigs.WHATSAPP_ACCESS_TOKEN).get().stringValue();

        String url = String.format(apiUrl, phoneNumberId);
        java.net.URI endpoint = java.net.URI.create(url);
        if (!"https".equals(endpoint.getScheme()) || !"graph.facebook.com".equals(endpoint.getHost())
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                || (endpoint.getPort() != -1 && endpoint.getPort() != 443)
                || !phoneNumberId.matches("[0-9]+")
                || !endpoint.getPath().matches("/v[0-9]+\\.[0-9]+/" + phoneNumberId + "/messages")) {
            throw new IllegalStateException("WhatsApp requires the HTTPS Meta messages endpoint and a numeric phone ID");
        }
        if (StringUtils.isBlank(accessToken) || "placeholder".equalsIgnoreCase(accessToken)) {
            throw new IllegalStateException("WhatsApp access token is not configured");
        }
        String recipient = StringUtils.defaultString(recipientPhone).replaceFirst("^\\+", "");
        if (!recipient.matches("[1-9][0-9]{7,14}")) throw new IllegalArgumentException("WhatsApp requires an international phone number");


        var params = List.of(
                new Parameter("text", "name", userName),
                new Parameter("text", "data", text)
        );

        var components = List.of(new Component("body", params));
        var template = new Template(utilityTemplate, new Language(templateLanguage), components);
        var request = new WhatsAppRequest("whatsapp", recipient, "template", template);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        log.info("Sending WhatsApp Message");
        WhatsAppResponse response = restTemplateService.sendPostRequest(url, request, headers, WhatsAppResponse.class);
        if (response == null || response.messages() == null || response.messages().size() != 1
                || response.messages().getFirst() == null || StringUtils.isBlank(response.messages().getFirst().id())) {
            throw new IllegalStateException("WhatsApp did not return a message receipt");
        }
        WAMessage receipt = response.messages().getFirst();
        sms.setStatus("accepted");
        sms.setThirdPartyId(receipt.id());
        sms.setDescription("Accepted by Meta; delivery not yet confirmed");
        smsDao.saveSMS(sms);
    }

    @Override
    public int executeSend(NotificationDTO dto, long notificationId) throws Exception {
        if (dto.notificationType() == org.pms.silverocean.service.notification.common.NotificationType.OTP_SMS) {
            throw new IllegalStateException("OTP delivery requires its own approved authentication template; keep the SMS provider enabled");
        }
        sendUtilityMessage(dto.recipient(), "SlickHood User", dto.formattedMessage(), notificationId);
        return 0;
    }

    @Override
    public boolean isRetryable(int statusCode) {
        return false;
    }

    @Override
    public boolean supports(String providerName) {
        if (StringUtils.isBlank(providerName)) return false;
        return WHATS_APP.equalsIgnoreCase(providerName);
    }
}
