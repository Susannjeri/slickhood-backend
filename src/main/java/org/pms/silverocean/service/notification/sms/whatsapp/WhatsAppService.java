package org.pms.silverocean.service.notification.sms.whatsapp;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.database.pms.entities.SMS;
import org.pms.silverocean.service.RestTemplateService;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.sms.SMSDao;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.WAMessage;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.WhatsAppResponse;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.request.Component;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.request.Language;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.request.Parameter;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.request.Template;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.request.WhatsAppRequest;
import org.pms.silverocean.service.notification.whatsapp.WhatsAppTemplateRegistry.ApprovedTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.List;

@Service @Slf4j
public class WhatsAppService {

    private static final String WHATS_APP = "WHATS_APP";
    private final RestTemplateService restTemplateService;
    private final ConfigService configService;
    private final SMSDao smsDao;
    @org.springframework.beans.factory.annotation.Value("${whatsapp.utility-template:general_message}")
    private String utilityTemplate = "general_message";
    @org.springframework.beans.factory.annotation.Value("${whatsapp.template-language:en}")
    private String templateLanguage = "en";
    @org.springframework.beans.factory.annotation.Value("${whatsapp.api-url:}")
    private String configuredApiUrl;
    @org.springframework.beans.factory.annotation.Value("${whatsapp.phone-number-id:}")
    private String configuredPhoneNumberId;
    @org.springframework.beans.factory.annotation.Value("${whatsapp.access-token:}")
    private String configuredAccessToken;

    public WhatsAppService(RestTemplateService restTemplateService, ConfigService configService, SMSDao smsDao) {
        this.restTemplateService = restTemplateService;
        this.configService = configService;
        this.smsDao = smsDao;
    }


    public void sendUtilityMessage(String recipientPhone, String userName, String text, long notificationId) {
        sendTemplateMessage(recipientPhone, userName, text, notificationId,
                new ApprovedTemplate(utilityTemplate, templateLanguage, "name", "data"));
    }

    public void sendTemplateMessage(String recipientPhone, String userName, String text, long notificationId,
                                    ApprovedTemplate approvedTemplate) {
        SMS sms = new SMS();
        sms.setNotificationId(notificationId);
        sms.setActive(true);
        sms.setChannel(WHATS_APP);

        // Production secrets belong in the protected service environment. Keep the
        // governed database settings as a compatibility fallback for existing
        // installations, but never require an operator to copy a token into the UI.
        String apiUrl = configuredOrFallback(configuredApiUrl, PMSConfigs.WHATSAPP_URL);
        String phoneNumberId = configuredOrFallback(configuredPhoneNumberId, PMSConfigs.WHATSAPP_BUSINESS_PHONENUMBER_ID);
        String accessToken = configuredOrFallback(configuredAccessToken, PMSConfigs.WHATSAPP_ACCESS_TOKEN);

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
                new Parameter("text", approvedTemplate.nameParameter(), userName),
                new Parameter("text", approvedTemplate.messageParameter(), text)
        );

        var components = List.of(new Component("body", params));
        var template = new Template(approvedTemplate.name(), new Language(approvedTemplate.language()), components);
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

    public int executeSend(NotificationDTO dto, long notificationId) throws Exception {
        if (dto.notificationType() == org.pms.silverocean.service.notification.common.NotificationType.OTP_SMS) {
            throw new IllegalStateException("OTP delivery requires its own approved authentication template; keep the SMS provider enabled");
        }
        sendUtilityMessage(dto.recipient(), "SlickHood User", dto.formattedMessage(), notificationId);
        return 0;
    }

    public boolean isRetryable(int statusCode) {
        return false;
    }

    public boolean supports(String providerName) {
        if (StringUtils.isBlank(providerName)) return false;
        return WHATS_APP.equalsIgnoreCase(providerName);
    }

    private String configuredOrFallback(String configured, PMSConfigs fallback) {
        return StringUtils.isNotBlank(configured)
                ? configured.trim()
                : configService.getConfigByName(fallback).get().stringValue();
    }
}
