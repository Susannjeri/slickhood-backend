package org.pms.silverocean.service.insurance;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.notification.*;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import java.time.*;

@Component @RequiredArgsConstructor
public class InsuranceRenewalRoutine {
 private final InsuranceAgencyRepo agencies;private final InsurancePolicyRepo policies;private final InsuranceCaseRepo cases;private final NotificationService notifications;private final I18NService i18n;
 @Scheduled(cron="${app.insurance.renewal.cron:0 0 8 * * *}",zone="${app.insurance.renewal.zone:Africa/Nairobi}")
 @Transactional("pmsDBTransactionManager") public void remind(){var agency=agencies.findByCodeAndActiveTrue("SILVERWOOD").orElse(null);if(agency==null)return;var due=policies.findReminderQueueByAgencyId(agency.getId(),"UPCOMING",LocalDate.now(),LocalDate.now().plusDays(30),PageRequest.of(0,200));for(var p:due){var c=cases.findById(p.getCaseId()).orElse(null);if(c==null)continue;String body=String.format(i18n.getLocalizedMessage(NotificationType.INSURANCE_RENEWAL_EMAIL.getBody()),HtmlUtils.htmlEscape(p.getPolicyNumber()),HtmlUtils.htmlEscape("UPCOMING - due "+p.getEndDate()));notifications.queueNotification(new NotificationDTO(body,c.getEmail(),NotificationType.INSURANCE_RENEWAL_EMAIL));p.setRenewalReminderSentAt(LocalDateTime.now());policies.save(p);}}
 @Scheduled(cron="${app.insurance.payment-reminder.cron:0 30 8 * * *}",zone="${app.insurance.renewal.zone:Africa/Nairobi}")
 @Transactional("pmsDBTransactionManager") public void remindSelectedQuotes(){var agency=agencies.findByCodeAndActiveTrue("SILVERWOOD").orElse(null);if(agency==null)return;var due=cases.findPaymentReminderQueueByAgencyId(agency.getId(),java.util.Set.of("CUSTOMER_SELECTED","PAYMENT_PENDING"),LocalDateTime.now().minusDays(2),PageRequest.of(0,200));for(var c:due){String body=String.format(i18n.getLocalizedMessage(NotificationType.INSURANCE_PAYMENT_REMINDER_EMAIL.getBody()),HtmlUtils.htmlEscape(c.getReference()));notifications.queueNotification(new NotificationDTO(body,c.getEmail(),NotificationType.INSURANCE_PAYMENT_REMINDER_EMAIL));c.setPaymentReminderSentAt(LocalDateTime.now());cases.save(c);}}
}
