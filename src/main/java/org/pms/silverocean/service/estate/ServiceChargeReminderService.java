package org.pms.silverocean.service.estate;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.database.pms.EstateServiceChargeRepo;
import org.pms.silverocean.service.architecture.events.DomainEventOutboxPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ServiceChargeReminderService {
    private final EstateServiceChargeRepo charges;
    private final DomainEventOutboxPublisher events;
    @Value("${pms.service-charge.reminder.overdue-repeat-days:7}") private int overdueRepeatDays = 7;
    @Value("${pms.service-charge.reminder.max-overdue-reminders:12}") private int maxOverdueReminders = 12;

    @Transactional
    public BatchResult queueBatch(LocalDate today, int batchSize) {
        var page = PageRequest.of(0, batchSize);
        var preDue = charges.lockPreDueReminderCandidates(today, today.plusDays(3), page);
        LocalDateTime queuedAt = LocalDateTime.now();
        int repeatDays = Math.max(1, overdueRepeatDays);
        int reminderLimit = Math.max(1, maxOverdueReminders);
        var overdue = charges.lockOverdueReminderCandidates(today, queuedAt.minusDays(repeatDays), reminderLimit, page);
        preDue.forEach(charge -> {
            charge.setPreDueReminderQueuedAt(queuedAt);
            events.publish(ServiceChargeReminderEvent.TYPE, "ESTATE_SERVICE_CHARGE", charge.getId().toString(),
                    "SERVICE_CHARGE_PRE_DUE:" + charge.getId(),
                    new ServiceChargeReminderEvent(charge.getId(), ServiceChargeReminderEvent.Phase.PRE_DUE));
        });
        overdue.forEach(charge -> {
            int occurrence = charge.getOverdueReminderCount() + 1;
            if (charge.getOverdueNoticeQueuedAt() == null) charge.setOverdueNoticeQueuedAt(queuedAt);
            charge.setLastOverdueReminderQueuedAt(queuedAt);
            charge.setOverdueReminderCount(occurrence);
            events.publish(ServiceChargeReminderEvent.TYPE, "ESTATE_SERVICE_CHARGE", charge.getId().toString(),
                    "SERVICE_CHARGE_OVERDUE:" + charge.getId() + ":" + occurrence,
                    new ServiceChargeReminderEvent(charge.getId(), ServiceChargeReminderEvent.Phase.OVERDUE));
        });
        charges.saveAll(preDue);
        charges.saveAll(overdue);
        return new BatchResult(preDue.size(), overdue.size());
    }

    public record BatchResult(int preDue, int overdue) {
        public int total() { return preDue + overdue; }
        public boolean hasFullCategory(int batchSize) { return preDue == batchSize || overdue == batchSize; }
    }
}
