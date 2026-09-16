package org.pms.silverocean.service.notification.preferences;

import org.junit.jupiter.api.Test;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NotificationPreferenceServiceTest {
    private final NotificationPreferenceRepo preferences=mock(NotificationPreferenceRepo.class);
    private final NotificationChannelConsentRepo consents=mock(NotificationChannelConsentRepo.class);
    private final UserDao users=mock(UserDao.class);
    private final AuditLogService audit=mock(AuditLogService.class);
    private final WhatsAppTemplateRegistry templates=mock(WhatsAppTemplateRegistry.class);
    private final NotificationPreferenceService service=new NotificationPreferenceService(preferences,consents,users,audit,templates);

    @Test void defaultsKeepImportantEmailOnMarketingOffAndExternalChannelsOff(){
        Users user=user(false); when(users.getUserObject()).thenReturn(user); when(preferences.findAllByUserIdOrderByCategory(7L)).thenReturn(List.of());
        var view=service.current();
        assertThat(view.inAppRequired()).isTrue(); assertThat(view.phoneVerified()).isFalse();
        assertThat(view.categories()).hasSize(5);
        assertThat(view.categories()).filteredOn(c->c.category()==NotificationCategory.MARKETING).allMatch(c->!c.emailEnabled());
        assertThat(view.categories()).filteredOn(c->c.category()!=NotificationCategory.MARKETING).allMatch(NotificationPreferenceModels.CategoryView::emailEnabled);
        assertThat(view.categories()).allMatch(c->!c.smsEnabled()&&!c.whatsappEnabled());
    }

    @Test void unverifiedPhoneCannotEnableSmsOrWhatsapp(){
        when(users.getUserObject()).thenReturn(user(false));
        var request=update(false,NotificationCategory.BILLING,"sms");
        assertThatThrownBy(()->service.update(request)).isInstanceOfSatisfying(PMSCustomException.class,
                e->assertThat(e.getResponseCode()).isEqualTo(ResponseCode.NOTIFICATION_PHONE_VERIFICATION_REQUIRED));
        verifyNoInteractions(consents,audit);
    }

    @Test void whatsappRequiresExplicitConsentAndApprovedCategoryTemplate(){
        when(users.getUserObject()).thenReturn(user(true));
        assertThatThrownBy(()->service.update(update(false,NotificationCategory.PROPERTY,"whatsapp")))
                .isInstanceOfSatisfying(PMSCustomException.class,e->assertThat(e.getResponseCode()).isEqualTo(ResponseCode.WHATSAPP_CONSENT_REQUIRED));
        assertThatThrownBy(()->service.update(update(true,NotificationCategory.PROPERTY,"whatsapp")))
                .isInstanceOfSatisfying(PMSCustomException.class,e->assertThat(e.getResponseCode()).isEqualTo(ResponseCode.WHATSAPP_TEMPLATE_UNAVAILABLE));
    }

    @Test void routingRequiresStoredOptInVerifiedPhoneConsentAndApprovedTemplate(){
        Users user=user(true); NotificationPreference p=new NotificationPreference(); p.setUserId(7L); p.setCategory(NotificationCategory.BILLING);
        p.setEmailEnabled(false);p.setSmsEnabled(true);p.setWhatsappEnabled(true);
        NotificationChannelConsent consent=new NotificationChannelConsent();consent.setConsented(true);
        when(preferences.findByUserIdAndCategory(7L,NotificationCategory.BILLING)).thenReturn(Optional.of(p));
        when(consents.findByUserIdAndChannel(7L,NotificationChannel.WHATSAPP)).thenReturn(Optional.of(consent));
        when(templates.approved(NotificationCategory.BILLING)).thenReturn(Optional.of(
                new WhatsAppTemplateRegistry.ApprovedTemplate("billing_update","en","name","data")));
        assertThat(service.plan(user,NotificationCategory.BILLING))
                .isEqualTo(new NotificationPreferenceModels.DeliveryPlan(false,true,true));
    }

    private Users user(boolean verified){Users user=new Users();user.setId(7L);user.setActive(true);user.setEmail("user@example.test");
        user.setPhoneNumber("+254722788650");user.setPhoneVerified(verified);return user;}
    private static NotificationPreferenceModels.Update update(boolean consent,NotificationCategory enabled,String channel){
        return new NotificationPreferenceModels.Update(consent,Arrays.stream(NotificationCategory.values()).map(category->
                new NotificationPreferenceModels.CategoryUpdate(category,true,
                        category==enabled&&"sms".equals(channel),category==enabled&&"whatsapp".equals(channel),0)).toList());
    }
}
