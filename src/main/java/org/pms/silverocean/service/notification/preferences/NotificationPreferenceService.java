package org.pms.silverocean.service.notification.preferences;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.NotificationChannelConsentRepo;
import org.pms.silverocean.database.pms.NotificationPreferenceRepo;
import org.pms.silverocean.database.pms.entities.NotificationChannelConsent;
import org.pms.silverocean.database.pms.entities.NotificationPreference;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.notification.common.NotificationChannel;
import org.pms.silverocean.service.notification.whatsapp.WhatsAppTemplateRegistry;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static org.pms.silverocean.service.notification.preferences.NotificationPreferenceModels.*;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {
    public static final String WHATSAPP_CONSENT_VERSION = "whatsapp-notifications-v1-2026-09";
    private final NotificationPreferenceRepo preferenceRepo;
    private final NotificationChannelConsentRepo consentRepo;
    private final UserDao userDao;
    private final AuditLogService auditLogService;
    private final WhatsAppTemplateRegistry templateRegistry;

    @Transactional("pmsDBTransactionManager")
    public View current() { return view(requireUser()); }

    @Transactional("pmsDBTransactionManager")
    public View update(Update update) {
        Users user = requireUser();
        EnumSet<NotificationCategory> categories = EnumSet.noneOf(NotificationCategory.class);
        for (CategoryUpdate item : update.categories()) {
            if (!categories.add(item.category())) throw new IllegalArgumentException("Each notification category must appear once");
            if ((item.smsEnabled() || item.whatsappEnabled()) && !user.isPhoneVerified())
                throw new PMSCustomException(ResponseCode.NOTIFICATION_PHONE_VERIFICATION_REQUIRED);
            if (item.whatsappEnabled() && !update.whatsappConsent())
                throw new PMSCustomException(ResponseCode.WHATSAPP_CONSENT_REQUIRED);
            if (item.whatsappEnabled() && templateRegistry.approved(item.category()).isEmpty())
                throw new PMSCustomException(ResponseCode.WHATSAPP_TEMPLATE_UNAVAILABLE, item.category());
        }
        if (categories.size() != NotificationCategory.values().length)
            throw new IllegalArgumentException("Preferences are required for every notification category");

        updateConsent(user, update.whatsappConsent());
        for (CategoryUpdate item : update.categories()) {
            NotificationPreference preference = preferenceRepo.findByUserIdAndCategory(user.getId(), item.category())
                    .orElseGet(() -> defaults(user.getId(), item.category()));
            if (preference.getId() != null && preference.getVersion() != item.version())
                throw new OptimisticLockingFailureException("Notification preferences changed in another session");
            preference.setEmailEnabled(item.emailEnabled());
            preference.setSmsEnabled(item.smsEnabled());
            preference.setWhatsappEnabled(item.whatsappEnabled());
            preference.setCreatedBy(user.getId()); preference.setActive(true);
            preferenceRepo.save(preference);
            auditLogService.createAuditLog(preference, "UPDATE_NOTIFICATION_PREFERENCE");
        }
        return view(user);
    }

    @Transactional(value = "pmsDBTransactionManager", readOnly = true)
    public DeliveryPlan plan(Users user, NotificationCategory category) {
        NotificationPreference preference = preferenceRepo.findByUserIdAndCategory(user.getId(), category)
                .orElseGet(() -> defaults(user.getId(), category));
        boolean phoneReady = user.isPhoneVerified() && user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank();
        boolean consented = consentRepo.findByUserIdAndChannel(user.getId(), NotificationChannel.WHATSAPP)
                .filter(NotificationChannelConsent::isConsented).isPresent();
        return new DeliveryPlan(preference.isEmailEnabled(), phoneReady && preference.isSmsEnabled(),
                phoneReady && consented && preference.isWhatsappEnabled() && templateRegistry.approved(category).isPresent());
    }

    private void updateConsent(Users user, boolean consented) {
        NotificationChannelConsent consent = consentRepo.findByUserIdAndChannel(user.getId(), NotificationChannel.WHATSAPP)
                .orElseGet(NotificationChannelConsent::new);
        if (consent.getId() != null && consent.isConsented() == consented) return;
        LocalDateTime now = LocalDateTime.now();
        consent.setUserId(user.getId()); consent.setChannel(NotificationChannel.WHATSAPP); consent.setConsented(consented);
        consent.setConsentVersion(WHATSAPP_CONSENT_VERSION); consent.setCreatedBy(user.getId()); consent.setActive(true);
        consent.setConsentedAt(consented ? now : consent.getConsentedAt()); consent.setRevokedAt(consented ? null : now);
        consentRepo.save(consent);
        auditLogService.createAuditLog(consent, consented ? "GRANT_WHATSAPP_CONSENT" : "REVOKE_WHATSAPP_CONSENT");
    }

    private View view(Users user) {
        Map<NotificationCategory, NotificationPreference> stored = new EnumMap<>(NotificationCategory.class);
        preferenceRepo.findAllByUserIdOrderByCategory(user.getId()).forEach(p -> stored.put(p.getCategory(), p));
        boolean consented = consentRepo.findByUserIdAndChannel(user.getId(), NotificationChannel.WHATSAPP)
                .filter(NotificationChannelConsent::isConsented).isPresent();
        boolean phoneReady = user.isPhoneVerified() && user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank();
        List<CategoryView> categories = Arrays.stream(NotificationCategory.values()).map(category -> {
            NotificationPreference p = stored.getOrDefault(category, defaults(user.getId(), category));
            return new CategoryView(category, p.isEmailEnabled(), p.isSmsEnabled(),
                    consented && p.isWhatsappEnabled(), phoneReady,
                    phoneReady && templateRegistry.approved(category).isPresent(), p.getVersion());
        }).toList();
        return new View(true, phoneReady, mask(user.getPhoneNumber()), consented,
                WHATSAPP_CONSENT_VERSION, categories);
    }

    private Users requireUser() {
        Users user = userDao.getUserObject();
        if (user == null || !user.isActive()) throw new IllegalStateException("Authenticated user was not found");
        return user;
    }

    private NotificationPreference defaults(Long userId, NotificationCategory category) {
        NotificationPreference p = new NotificationPreference(); p.setUserId(userId); p.setCategory(category);
        p.setEmailEnabled(category != NotificationCategory.MARKETING); p.setActive(true); return p;
    }

    private static String mask(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String clean = phone.trim(); return clean.length() <= 4 ? "****" : "*".repeat(clean.length() - 4) + clean.substring(clean.length() - 4);
    }
}
