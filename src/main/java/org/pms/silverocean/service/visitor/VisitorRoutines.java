package org.pms.silverocean.service.visitor;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.service.config.ConfigDTO;
import org.pms.silverocean.database.pms.GateRequestNonceRepo;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.threadpooling.PMSThreadPoolExecutorService;
import org.pms.silverocean.service.threadpooling.ThreadPoolBeans;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@Slf4j
@RequiredArgsConstructor
public class VisitorRoutines {

    private final VisitorService visitorService;
    private final ThreadPoolBeans threadPoolBeans;
    private final ConfigService configService;
    private final GateRequestNonceRepo nonceRepo;

    private PMSThreadPoolExecutorService visitorCleanupPool;
    private Supplier<ConfigDTO> visitorExpireBatchSize;
    private Supplier<ConfigDTO> visitorExpireDays;

    @PostConstruct
    public void init() {
        visitorCleanupPool = threadPoolBeans.cpuExecutorService("visitor-cleanup", 1, 10);
        visitorExpireBatchSize = configService.getConfigByName(PMSConfigs.VISITOR_EXPIRY_BATCH_SIZE);
        visitorExpireDays = configService.getConfigByName(PMSConfigs.VISITOR_EXPIRY_DAYS);
        scheduleNextMidnightRun();
    }

    private void runExpiredVisitorBatch() {
        try {
            int deletedNonces = nonceRepo.deleteExpired(ZonedDateTime.now(ZoneId.of("UTC")));
            if (deletedNonces > 0) log.info("Deleted {} expired smart-gate nonces", deletedNonces);
            int batchSize = visitorExpireBatchSize.get().intValue();
            boolean hasMore = visitorService.cleanUpPendingExpiredVisitorRecords(
                    batchSize, visitorExpireDays.get().intValue());
            if (hasMore) {
                log.info("More expired visitors remain, scheduling next batch in 5 minutes");
                visitorCleanupPool.schedule(this::runExpiredVisitorBatch, 5, TimeUnit.MINUTES);
            } else {
                log.info("Visitor expiry cleanup complete");
                scheduleNextMidnightRun();
            }
        } catch (Exception e) {
            log.error("Visitor expiry batch failed, retrying in 5 minutes", e);
            visitorCleanupPool.schedule(this::runExpiredVisitorBatch, 5, TimeUnit.MINUTES);
        }
    }

    private void scheduleNextMidnightRun() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("UTC"));
        long delayMillis = Duration.between(now, nextMidnight).toMillis();
        log.info("Scheduling next visitor expiry run at {} ({} ms from now)", nextMidnight, delayMillis);
        visitorCleanupPool.schedule(this::runExpiredVisitorBatch, delayMillis, TimeUnit.MILLISECONDS);
    }
}
