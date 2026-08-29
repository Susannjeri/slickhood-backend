package org.pms.silverocean.service.notification.sms.africastalking;

import com.africastalking.AfricasTalking;
import com.africastalking.SmsService;
import com.africastalking.sms.Recipient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.database.pms.entities.SMS;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.sms.SMSDao;
import org.pms.silverocean.service.notification.sms.SmsProvider;
import org.pms.silverocean.service.notification.sms.africastalking.wrappers.ATStatusCode;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@DependsOn("configInitService")
@Slf4j
public class AfricasTalkingService implements SmsProvider {
    private static final String ATSMS = "Africastalking";
    private final SMSDao smsDao;
    private final ConfigService configService;
    private SmsService smsService;

    public AfricasTalkingService(ConfigService configService, SMSDao smsDao) {
        this.smsDao = smsDao;
        this.configService = configService;
    }


    @PostConstruct
    public void init() {
        String username = configService.getConfigByName(PMSConfigs.AFRICAS_TALKING_SMS_USERNAME).get().stringValue();
        String password = configService.getConfigByName(PMSConfigs.AFRICAS_TALKING_SMS_PASSWORD).get().stringValue();

        AfricasTalking.initialize(username, password);
        smsService = AfricasTalking.getService(AfricasTalking.SERVICE_SMS);
    }

    public int executeSend(NotificationDTO notificationDTO, long notificationId) throws IOException {
        SMS sms = new SMS();
        sms.setNotificationId(notificationId);
        sms.setActive(true);
        sms.setChannel(ATSMS);
        String recipient = notificationDTO.recipient().startsWith("+") ? notificationDTO.recipient() : "+" + notificationDTO.recipient();
        List<Recipient> sendResponse = smsService.send(notificationDTO.formattedMessage(), new String[]{recipient}, true);
        log.debug("SMS Sent to recipients: {}", sendResponse);
        int statusCode = 0;
        for (Recipient r : sendResponse) {
            sms.setStatus(String.valueOf(r.statusCode));
            sms.setDescription(r.status);
            sms.setThirdPartyId(r.messageId);

            statusCode = r.statusCode;
            CurrencyParseResult parsedCost = parseCurrencyAndCost(r.cost);
            if (parsedCost != null) {
                if (parsedCost.isSuccess()) {
                    sms.setCost(parsedCost.cost());
                    sms.setCurrency(parsedCost.currency());
                } else {
                    log.error("Got error ({}) reading cost from response {}", parsedCost.errorMessage(), r.cost);
                }
            } else {
                log.error("Could not read cost from response {}", r.cost);
            }
        }
        smsDao.saveSMS(sms);
        return statusCode;
    }

    @Override
    public boolean isRetryable(int statusCode) {
        return ATStatusCode.fromCode(statusCode).isRetryIfError();
    }

    @Override
    public boolean supports(String providerName) {
        if (StringUtils.isBlank(providerName)) return false;
        String normalizedInput = providerName.replaceAll("[^a-zA-Z0-9]", "");
        return ATSMS.equalsIgnoreCase(normalizedInput);
    }

    private CurrencyParseResult parseCurrencyAndCost(String input) {
        if (input == null || input.isBlank()) {
            return CurrencyParseResult.failure("Input string cannot be null or blank.");
        }

        // 1. Split the string once at the first space delimiter
        String[] parts = input.trim().split(" ", 2);

        if (parts.length == 1) {
            //try deal with "cost":0
            try {
                double cost = Double.parseDouble(parts[0]);
                return CurrencyParseResult.success("KES", cost);
            } catch (NumberFormatException e) {
                return CurrencyParseResult.failure(
                        "Cost WAValue is not a valid decimal number: " + parts[0]
                );
            }
        } else if (parts.length != 2) {
            return CurrencyParseResult.failure(
                    "Input format is incorrect. Expected format: 'CODE VALUE'. Got: " + input
            );
        }

        String currency = parts[0].trim();
        String costString = parts[1].trim();

        // Basic validation for currency code length
        if (currency.length() != 3 || !currency.matches("[A-Z]+")) {
            return CurrencyParseResult.failure("Currency code must be 3 uppercase letters.");
        }

        try {
            // 2. Parse the second part as a double
            // Note: This relies on the system's default locale for decimal separation (usually '.')
            double cost = Double.parseDouble(costString);

            // 3. Return success result
            return CurrencyParseResult.success(currency, cost);

        } catch (NumberFormatException e) {
            // 4. Return error if cost cannot be parsed
            return CurrencyParseResult.failure(
                    "Cost WAValue is not a valid decimal number: " + costString
            );
        }
    }
}
