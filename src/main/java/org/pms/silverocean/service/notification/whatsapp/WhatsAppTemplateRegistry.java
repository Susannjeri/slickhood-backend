package org.pms.silverocean.service.notification.whatsapp;

import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.service.notification.preferences.NotificationCategory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class WhatsAppTemplateRegistry {
    private final Environment environment;

    public WhatsAppTemplateRegistry(Environment environment) { this.environment = environment; }

    public Optional<ApprovedTemplate> approved(NotificationCategory category) {
        String prefix = "whatsapp.templates." + category.name().toLowerCase(Locale.ROOT) + ".";
        boolean approved = Boolean.parseBoolean(environment.getProperty(prefix + "approved", "false"));
        String name = environment.getProperty(prefix + "name");
        String language = environment.getProperty(prefix + "language", "en");
        if (!approved || StringUtils.isBlank(name) || !name.matches("[a-z0-9_]+")
                || !language.matches("[A-Za-z_-]{2,12}")) return Optional.empty();
        return Optional.of(new ApprovedTemplate(name, language,
                environment.getProperty(prefix + "name-parameter", "name"),
                environment.getProperty(prefix + "message-parameter", "data")));
    }

    public record ApprovedTemplate(String name, String language, String nameParameter, String messageParameter) {}
}
