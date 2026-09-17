package org.pms.silverocean.service.notification;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.pms.silverocean.database.pms.entities.Notification;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.notification.preferences.NotificationPreferenceModels;
import org.pms.silverocean.service.notification.preferences.NotificationPreferenceService;
import org.pms.silverocean.service.security.EncryptionService;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

class NotificationServiceTest {
    @Test void optedInUserReceivesEmailSmsWhatsappAndMandatoryInAppWithoutDuplicates(){
        EncryptionService encryption=mock(EncryptionService.class); NotificationDao dao=mock(NotificationDao.class);
        UserDao users=mock(UserDao.class); ApplicationEventPublisher events=mock(ApplicationEventPublisher.class);
        NotificationPreferenceService preferences=mock(NotificationPreferenceService.class);
        Users user=new Users(); user.setId(8L); user.setActive(true); user.setPhoneNumber("+254722788650"); user.setPhoneVerified(true);
        when(users.findByEmail("buyer@example.test")).thenReturn(java.util.Optional.of(user));
        when(preferences.plan(user,org.pms.silverocean.service.notification.preferences.NotificationCategory.BILLING))
                .thenReturn(new NotificationPreferenceModels.DeliveryPlan(true,true,true));
        when(dao.hasDeliveryKey(any())).thenReturn(false);
        when(dao.save(any())).thenAnswer(i->{Notification n=i.getArgument(0);n.setId((long)(n.getChannel().hashCode()&0xffff));return n.getId();});
        when(encryption.encrypt(any())).thenReturn(new byte[]{1});
        var service=new NotificationService(encryption,dao,users,Map.of(),events,preferences);

        service.queueEmailAndInAppOnce("invoice:7:overdue","buyer@example.test",
                NotificationType.RENT_OVERDUE_EMAIL,"<p>Balance due</p>","RENT_OVERDUE","Balance due","/dashboard/billing");

        var stored=ArgumentCaptor.forClass(Notification.class); verify(dao,times(4)).save(stored.capture());
        assertThat(stored.getAllValues()).extracting(Notification::getChannel)
                .containsExactly("EMAIL","SMS","WHATSAPP","IN_APP");
        assertThat(stored.getAllValues().subList(1,3)).extracting(Notification::getRecipient)
                .containsOnly("+254722788650");
        verify(events,times(3)).publishEvent(any(NotificationService.NotificationQueued.class));
    }
    @Test void replayCreatesOnlyOneRecordPerChannelAndOneEmailDispatch(){
        EncryptionService encryption=mock(EncryptionService.class);NotificationDao dao=mock(NotificationDao.class);
        UserDao users=mock(UserDao.class);ApplicationEventPublisher events=mock(ApplicationEventPublisher.class);
        Users user=new Users();user.setActive(true);when(users.findByEmail("buyer@example.test")).thenReturn(java.util.Optional.of(user));
        Set<String> keys=new java.util.HashSet<>();
        when(dao.hasDeliveryKey(any())).thenAnswer(i->keys.contains(i.getArgument(0)));
        when(dao.save(any())).thenAnswer(i->{Notification n=i.getArgument(0);assertThat(keys.add(n.getDeliveryKey())).isTrue();n.setId((long)keys.size());return n.getId();});
        when(encryption.encrypt(any())).thenReturn(new byte[]{1});
        NotificationPreferenceService preferences=mock(NotificationPreferenceService.class);
        when(preferences.plan(any(),any())).thenReturn(new NotificationPreferenceModels.DeliveryPlan(true,false,false));
        var service=new NotificationService(encryption,dao,users,Map.of(),events,preferences);
        for(int i=0;i<2;i++)service.queueEmailAndInAppOnce("order:7:paid"," Buyer@Example.test ",NotificationType.BUSINESS_ALERT_EMAIL,"Order paid","SOKO_ORDER_STATUS","Order paid","/dashboard/soko");
        var stored=ArgumentCaptor.forClass(Notification.class);verify(dao,times(2)).save(stored.capture());verify(events,times(1)).publishEvent(any(NotificationService.NotificationQueued.class));
        assertThat(stored.getAllValues()).extracting(Notification::getChannel).containsExactly("EMAIL","IN_APP");
        assertThat(stored.getAllValues().get(0).getBusinessEventKey()).isEqualTo(stored.getAllValues().get(1).getBusinessEventKey());
        assertThat(stored.getAllValues().get(0).getDeliveryKey()).isNotEqualTo(stored.getAllValues().get(1).getDeliveryKey());
    }
    @Test
    void inAppNotificationIsStoredForExistingActiveUserWithoutExternalDelivery() {
        EncryptionService encryption = mock(EncryptionService.class);
        NotificationDao dao = mock(NotificationDao.class);
        UserDao users = mock(UserDao.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        Users recipient = Users.builder().email("member@example.com").build();
        recipient.setActive(true);
        when(users.findByEmail("member@example.com")).thenReturn(java.util.Optional.of(recipient));
        when(encryption.encrypt("Review invitation: https://app.slickhood.com/lease/onboard?token=safe"))
                .thenReturn(new byte[]{9, 1});
        NotificationService service = new NotificationService(encryption, dao, users, Map.of(), events, mock(NotificationPreferenceService.class));

        assertThat(service.queueInAppNotificationForExistingUser(
                " Member@Example.com ", "INVITE_RECEIVED",
                "Review invitation: https://app.slickhood.com/lease/onboard?token=safe")).isTrue();

        ArgumentCaptor<Notification> stored = ArgumentCaptor.forClass(Notification.class);
        verify(dao).save(stored.capture());
        assertThat(stored.getValue().getRecipient()).isEqualTo("member@example.com");
        assertThat(stored.getValue().getChannel()).isEqualTo("IN_APP");
        assertThat(stored.getValue().getType()).isEqualTo("INVITE_RECEIVED");
        assertThat(stored.getValue().isDelivered()).isTrue();
        assertThat(stored.getValue().isRetry()).isFalse();
        verifyNoInteractions(events);
    }

    @Test
    void inAppNotificationIsNotStoredForUnknownOrInactiveAccount() {
        EncryptionService encryption = mock(EncryptionService.class);
        NotificationDao dao = mock(NotificationDao.class);
        UserDao users = mock(UserDao.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        when(users.findByEmail("missing@example.com")).thenReturn(java.util.Optional.empty());
        NotificationService service = new NotificationService(encryption, dao, users, Map.of(), events, mock(NotificationPreferenceService.class));

        assertThat(service.queueInAppNotificationForExistingUser(
                "missing@example.com", "INVITE_RECEIVED", "Review invitation")).isFalse();

        verifyNoInteractions(dao, encryption, events);
    }

    @Test
    void queuePersistsBeforePublishingAndDeliveryStartsOnlyFromAfterCommitListener() {
        EncryptionService encryption = mock(EncryptionService.class);
        NotificationDao dao = mock(NotificationDao.class);
        UserDao users = mock(UserDao.class);
        NotificationSender email = mock(NotificationSender.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        when(encryption.encrypt("Balance due")).thenReturn(new byte[]{4, 2});
        when(dao.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification stored = invocation.getArgument(0);
            stored.setId(91L);
            return 91L;
        });
        NotificationService service = new NotificationService(encryption, dao, users, Map.of("EMAIL", email), events, mock(NotificationPreferenceService.class));
        NotificationDTO request = new NotificationDTO("Balance due", "owner@example.com",
                NotificationType.SERVICE_CHARGE_OVERDUE_EMAIL);

        assertThat(service.queueNotification(request)).isEqualTo(91L);

        verify(email, never()).send(any(), anyLong());
        ArgumentCaptor<NotificationService.NotificationQueued> queued =
                ArgumentCaptor.forClass(NotificationService.NotificationQueued.class);
        verify(events).publishEvent(queued.capture());
        assertThat(queued.getValue().notificationId()).isEqualTo(91L);

        service.deliverAfterCommit(queued.getValue());
        verify(email).send(request, 91L);
    }

    @Test
    void deliveryRoutingFailureIsRecordedInsteadOfRemainingQueued() {
        NotificationDao dao = mock(NotificationDao.class);
        NotificationService service = new NotificationService(mock(EncryptionService.class), dao,
                mock(UserDao.class), Map.of(), mock(ApplicationEventPublisher.class),
                mock(NotificationPreferenceService.class));
        NotificationDTO request = new NotificationDTO("Code", "owner@example.com",
                NotificationType.INSURANCE_GUEST_OTP_EMAIL);

        service.deliverAfterCommit(new NotificationService.NotificationQueued(92L, request));

        verify(dao).markDeliveryFailed(92L);
    }

    @Test
    void superAdminEscalationUsesCurrentActiveRecipientsWithoutRestart() {
        EncryptionService encryption = mock(EncryptionService.class);
        NotificationDao dao = mock(NotificationDao.class);
        UserDao users = mock(UserDao.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        Users first = Users.builder().email("first-admin@example.com").build();
        Users second = Users.builder().email("second-admin@example.com").build();
        when(users.findActiveSuperAdminAccounts()).thenReturn(Set.of(first), Set.of(second));
        when(encryption.encrypt(any())).thenReturn(new byte[]{1});
        when(dao.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification stored = invocation.getArgument(0);
            stored.setId(100L);
            return 100L;
        });
        NotificationService service = new NotificationService(encryption, dao, users, Map.of(), events, mock(NotificationPreferenceService.class));

        service.sendEmailToSuperAdmin(NotificationType.SERVICE_CHARGE_OVERDUE_EMAIL, "Escalation");
        service.sendEmailToSuperAdmin(NotificationType.SERVICE_CHARGE_OVERDUE_EMAIL, "Escalation");

        ArgumentCaptor<Notification> stored = ArgumentCaptor.forClass(Notification.class);
        verify(dao, times(2)).save(stored.capture());
        assertThat(stored.getAllValues()).extracting(Notification::getRecipient)
                .containsExactly("first-admin@example.com", "second-admin@example.com");
    }
}
