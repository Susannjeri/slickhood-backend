package org.pms.silverocean.service.notification.preferences;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class NotificationPreferenceModels {
    private NotificationPreferenceModels() {}

    public record View(boolean inAppRequired, boolean phoneVerified, String maskedPhone,
                       boolean whatsappConsented, String consentVersion,
                       List<CategoryView> categories) {}
    public record CategoryView(NotificationCategory category, boolean emailEnabled, boolean smsEnabled,
                               boolean whatsappEnabled, boolean smsAvailable, boolean whatsappAvailable,
                               long version) {}
    public record Update(boolean whatsappConsent,
                         @NotNull @Size(min = 5, max = 5) List<@Valid CategoryUpdate> categories) {}
    public record CategoryUpdate(@NotNull NotificationCategory category, boolean emailEnabled,
                                 boolean smsEnabled, boolean whatsappEnabled, long version) {}
    public record DeliveryPlan(boolean email, boolean sms, boolean whatsapp) {}
}
