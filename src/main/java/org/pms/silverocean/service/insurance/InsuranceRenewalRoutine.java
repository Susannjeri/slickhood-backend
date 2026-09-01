package org.pms.silverocean.service.insurance;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.notification.*;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component @RequiredArgsConstructor
public class InsuranceRenewalRoutine {
 private static final List<Integer> STAGES=List.of(0,1,3,7,14,30);
 private final InsuranceAgencyRepo agencies;private final InsurancePolicyRepo policies;private final InsuranceCaseRepo cases;private final InsuranceRenewalReminderRepo reminders;private final NotificationService notifications;private final I18NService i18n;
 @Value("${app.public-url:https://slickhood.com}") private String publicUrl;
 @Scheduled(cron="${app.insurance.renewal.cron:0 0 8 * * *}",zone="${app.insurance.renewal.zone:Africa/Nairobi}")
 @Transactional("pmsDBTransactionManager") public void remind(){var agency=agencies.findByCodeAndActiveTrue("SILVERWOOD").orElse(null);if(agency==null)return;ZoneId zone=ZoneId.of("Africa/Nairobi");LocalDate today=LocalDate.now(zone);var due=policies.findStagedReminderQueue(agency.getId(),Set.of("UPCOMING","CONTACTED","RENEWAL_QUOTED","ACCEPTED","RENEWED"),today,today.plusDays(30),PageRequest.of(0,200));for(var p:due){long remaining=ChronoUnit.DAYS.between(today,p.getEndDate());Integer stage=STAGES.stream().filter(x->remaining<=x).findFirst().orElse(null);if(stage==null||reminders.existsByPolicyIdAndPolicyEndDateAndReminderDays(p.getId(),p.getEndDate(),stage))continue;var c=cases.findById(p.getCaseId()).filter(InsuranceCase::isActive).orElse(null);if(c==null)continue;String state=stage==0?"COVER EXPIRES TODAY":"COVER EXPIRES IN "+stage+" DAYS ("+p.getEndDate()+")";queue(c.getEmail(),p,state);InsuranceRenewalReminder reminder=new InsuranceRenewalReminder();reminder.setPolicyId(p.getId());reminder.setPolicyEndDate(p.getEndDate());reminder.setReminderDays(stage);reminder.setQueuedAt(LocalDateTime.now(zone));reminder.setActive(true);reminders.save(reminder);}expire(agency.getId(),today);}
 private void expire(long agencyId,LocalDate today){var expired=policies.findExpiryQueue(agencyId,today.minusDays(1),PageRequest.of(0,200));for(var p:expired){p.setStatus("EXPIRED");boolean paid="PAID".equals(p.getRenewalStatus());if(!paid)p.setRenewalStatus("LAPSED");policies.save(p);String state=paid?"PREVIOUS COVER EXPIRED - RENEWAL PAYMENT VERIFIED; SILVERWOOD IS FINALISING THE NEW POLICY":"COVER EXPIRED - CONTACT SILVERWOOD BEFORE MAKING ANY PAYMENT";cases.findById(p.getCaseId()).filter(InsuranceCase::isActive).ifPresent(c->queue(c.getEmail(),p,state));}}
 private void queue(String email,InsurancePolicy p,String state){String link=StringUtils.removeEnd(publicUrl,"/")+"/dashboard/insurance?tab=policies&policyId="+p.getId();String body=String.format(i18n.getLocalizedMessage(NotificationType.INSURANCE_RENEWAL_EMAIL.getBody()),HtmlUtils.htmlEscape(p.getPolicyNumber()),HtmlUtils.htmlEscape(state),HtmlUtils.htmlEscape(link));notifications.queueNotification(new NotificationDTO(body,email,NotificationType.INSURANCE_RENEWAL_EMAIL));}
 @Scheduled(cron="${app.insurance.payment-reminder.cron:0 30 8 * * *}",zone="${app.insurance.renewal.zone:Africa/Nairobi}")
 @Transactional("pmsDBTransactionManager") public void remindSelectedQuotes(){var agency=agencies.findByCodeAndActiveTrue("SILVERWOOD").orElse(null);if(agency==null)return;var due=cases.findPaymentReminderQueueByAgencyId(agency.getId(),Set.of("CUSTOMER_SELECTED","PAYMENT_PENDING"),LocalDateTime.now().minusDays(2),PageRequest.of(0,200));for(var c:due){String body=String.format(i18n.getLocalizedMessage(NotificationType.INSURANCE_PAYMENT_REMINDER_EMAIL.getBody()),HtmlUtils.htmlEscape(c.getReference()));notifications.queueNotification(new NotificationDTO(body,c.getEmail(),NotificationType.INSURANCE_PAYMENT_REMINDER_EMAIL));c.setPaymentReminderSentAt(LocalDateTime.now());cases.save(c);}}
}
