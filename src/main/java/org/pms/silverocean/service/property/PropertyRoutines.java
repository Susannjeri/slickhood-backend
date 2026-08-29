package org.pms.silverocean.service.property;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.property.wrappers.DuplicateUnitJobDTO;
import org.pms.silverocean.service.threadpooling.PMSThreadPoolExecutorService;
import org.pms.silverocean.service.threadpooling.ThreadPoolBeans;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PropertyRoutines {
    private final PropertyDao propertyDao;
    private final UserDao userDao;
    private final NotificationService notificationService;
    private final I18NService i18NService;
    private final ThreadPoolBeans threadPoolBeans;

    private static final int BATCH_SIZE = 100;

    @Value("${property.max.days.without.units:14}")
    private int maxDaysWithoutUnits;

    @Value("${property.reminder.days.without.units:12}")
    private int sendAddUnitsReminder;

    private final ConcurrentHashMap<Long, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    private PMSThreadPoolExecutorService createUnitDuplicatePool;


    public PropertyRoutines(PropertyDao propertyDao, UserDao userDao, NotificationService notificationService, I18NService i18NService, ThreadPoolBeans threadPoolBeans) {
        this.propertyDao = propertyDao;
        this.userDao = userDao;
        this.notificationService = notificationService;
        this.i18NService = i18NService;
        this.threadPoolBeans = threadPoolBeans;
    }

    @PostConstruct
    public void init() {
        createUnitDuplicatePool = threadPoolBeans.cpuExecutorService("duplicate-", 6, 600);
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void deactivatePropertyWithoutUnits() { //TODO move this to global routines class where all routines will be triggered from
        log.info("Started routine stale property clean up");
        ZonedDateTime startTime = ZonedDateTime.now();
        int page = 0;
        Page<Property> inactiveProperty;
        do {
            inactiveProperty = propertyDao.findPropertyWithoutUnitsToDeactivate(maxDaysWithoutUnits, PageRequest.of(page, BATCH_SIZE));
            Set<Property> deactivatedProperty = inactiveProperty.stream()
                    .peek(property -> {
                        property.setActive(false);
                        property.setLastModifiedDate(LocalDateTime.now());
                    }).collect(Collectors.toSet());
            if (!inactiveProperty.isEmpty()) {
                log.info("Deactivating {} property without units", deactivatedProperty.size());
                propertyDao.updateInactiveProperty(deactivatedProperty);
            }
            page++;
        } while (inactiveProperty.hasNext());
        log.info("Completed routine stale property clean up, took {} seconds", Duration.between(startTime, ZonedDateTime.now()).toSeconds());
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void sendReminderToAddUnitsOrRiskDeletion() { //TODO move this to global routines class where all routines will be triggered from
        log.info("Started routine to send reminder to add units before deletion");
        ZonedDateTime startTime = ZonedDateTime.now();

        int page = 0;
        Page<Property> propertyToSendNotifications;
        do {
            propertyToSendNotifications = propertyDao.findPropertyWithoutUnitsToDeactivate(sendAddUnitsReminder, PageRequest.of(page, BATCH_SIZE));

            propertyToSendNotifications.forEach(property -> userDao.findById(property.getCreatedBy())
                    .ifPresent(user ->
                            notificationService.sendNotification(new NotificationDTO(String.format(i18NService.getLocalizedMessage("add.units.reminder.email.body", new Locale(user.getLocale())), user.getFullName(), property.getName()),
                                    user.getEmail(), NotificationType.ADD_UNITS_REMINDER_EMAIL))));
            page++;
        } while (propertyToSendNotifications.hasNext());
        log.info("Completed sending email reminders to add units, took {} seconds", Duration.between(startTime, ZonedDateTime.now()).toSeconds());
    }

    public void scheduleDuplicateUnitJob(long bulkJobId, Supplier<DuplicateUnitJobDTO> bulkJob) {
        if (!scheduledFutures.containsKey(bulkJobId)) {
            Runnable createUnitJob = () -> {
                try {
                    DuplicateUnitJobDTO duplicateUnitJobDTO = bulkJob.get();
                    String localizedMessage = String.format(i18NService.getLocalizedMessage(NotificationType.CREATE_SIMILAR_UNITS_JOB_COMPLETION_EMAIL.getSubject()), bulkJobId, duplicateUnitJobDTO.success() ? "successfully" : "with failures");
                    NotificationDTO notification =
                            new NotificationDTO(localizedMessage, duplicateUnitJobDTO.email(), NotificationType.EMAIL_OTP);
                    notificationService.sendNotification(notification);
                } catch (Exception e) {
                    log.error("Exception running creat unit bulk job", e);
                } finally {
                    scheduledFutures.remove(bulkJobId);
                }
            };


            ScheduledFuture<?> schedule = createUnitDuplicatePool.schedule(createUnitJob, 5, TimeUnit.SECONDS);

            scheduledFutures.put(bulkJobId, schedule);
        }
    }


}
