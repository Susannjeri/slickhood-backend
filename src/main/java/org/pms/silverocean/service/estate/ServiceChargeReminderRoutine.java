package org.pms.silverocean.service.estate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceChargeReminderRoutine {
    private static final int BATCH_SIZE = 100;
    private static final int MAX_BATCHES = 100;
    private final ServiceChargeReminderService reminders;
    @Value("${pms.service-charge.reminder.zone:Africa/Nairobi}") private String reminderZone;

    @Scheduled(cron = "${pms.service-charge.reminder.cron:0 0 8 * * *}", zone = "${pms.service-charge.reminder.zone:Africa/Nairobi}")
    public void queueReminders() {
        LocalDate today = LocalDate.now(ZoneId.of(reminderZone));
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            ServiceChargeReminderService.BatchResult queued = reminders.queueBatch(today, BATCH_SIZE);
            total += queued.total();
            if (!queued.hasFullCategory(BATCH_SIZE)) break;
        }
        if (total > 0) log.info("Queued {} service-charge reminder events", total);
    }
}
