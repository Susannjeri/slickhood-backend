package org.pms.silverocean.service.sp;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.database.pms.entities.RiskScore;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.config.ConfigDTO;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.sp.dao.RiskScoreDao;
import org.pms.silverocean.service.sp.dao.ServiceRatingDao;
import org.pms.silverocean.service.sp.enums.RiskLabel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Supplier;

@Service
@Slf4j
@RequiredArgsConstructor
public class RiskScoreService {
    private final RiskScoreDao riskScoreDao;
    private final ServiceRatingDao serviceRatingDao;
    private final UserDao userDao;
    private final NotificationService notificationService;
    private final I18NService i18NService;
    private final ConfigService configService;

    private Supplier<ConfigDTO> trustedThresholdSupplier;
    private Supplier<ConfigDTO> trustedMinStarsSupplier;
    private Supplier<ConfigDTO> lowRatingThresholdSupplier;

    @PostConstruct
    public void init() {
        trustedThresholdSupplier = configService.getConfigByName(PMSConfigs.SP_TRUSTED_THRESHOLD_COUNT);
        trustedMinStarsSupplier = configService.getConfigByName(PMSConfigs.SP_TRUSTED_MIN_STARS_COUNT);
        lowRatingThresholdSupplier = configService.getConfigByName(PMSConfigs.SP_LOW_RATING_THRESHOLD_VALUE);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void initRiskScore(long serviceId) {
        RiskScore riskScore = new RiskScore();
        riskScore.setServiceId(serviceId);
        riskScore.setLabel(RiskLabel.UNDER_REVIEW.name());
        riskScore.setHighlyRatedCompletedCount(0);
        riskScore.setComputedAt(ZonedDateTime.now(ZoneId.of("UTC")));
        riskScore.setActive(true);
        riskScore.setCreatedBy(userDao.getUserId());
        riskScoreDao.save(riskScore, Permission.ADD_SP_SERVICE);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void setVerified(long serviceId) {
        riskScoreDao.findLatestByServiceId(serviceId).ifPresent(riskScore -> {
            riskScore.setLabel(RiskLabel.VERIFIED.name());
            riskScore.setComputedAt(ZonedDateTime.now(ZoneId.of("UTC")));
            riskScoreDao.save(riskScore, Permission.APPROVE_SP_SERVICE);
        });
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void recomputeRiskLabel(long serviceId) {
        int trustedThreshold = trustedThresholdSupplier.get().intValue();
        int minStars = trustedMinStarsSupplier.get().intValue();
        double lowRatingThreshold = lowRatingThresholdSupplier.get().intValue();

        int highlyRatedCount = serviceRatingDao.countHighlyRated(serviceId, minStars);
        double avgStars = serviceRatingDao.avgStars(serviceId);

        riskScoreDao.findLatestByServiceId(serviceId).ifPresent(riskScore -> {
            riskScore.setHighlyRatedCompletedCount(highlyRatedCount);
            riskScore.setComputedAt(ZonedDateTime.now(ZoneId.of("UTC")));

            boolean wasTrustedAlready = RiskLabel.TRUSTED.name().equals(riskScore.getLabel());
            if (!wasTrustedAlready && RiskLabel.VERIFIED.name().equals(riskScore.getLabel()) && highlyRatedCount >= trustedThreshold) {
                riskScore.setLabel(RiskLabel.TRUSTED.name());
                riskScoreDao.save(riskScore, Permission.RATE_SP_SERVICE);
                sendTrustedNotification(serviceId);
            } else {
                riskScoreDao.save(riskScore, Permission.RATE_SP_SERVICE);
            }

            if (avgStars < lowRatingThreshold) {
                log.warn("Service {} has low average rating: {}", serviceId, avgStars);
            }
        });
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void resetToUnderReview(long serviceId) {
        riskScoreDao.findLatestByServiceId(serviceId).ifPresent(riskScore -> {
            riskScore.setLabel(RiskLabel.UNDER_REVIEW.name());
            riskScore.setComputedAt(ZonedDateTime.now(ZoneId.of("UTC")));
            riskScoreDao.save(riskScore, Permission.RESOLVE_SP_COMPLAINT);
        });
    }

    private void sendTrustedNotification(long serviceId) {
        try {
            Users user = userDao.getUserObject();
            if (user != null) {
                String message = String.format(
                        i18NService.getLocalizedMessage(NotificationType.SP_TRUSTED_LABEL_EMAIL.getBody()),
                        serviceId
                );
                notificationService.sendNotification(new NotificationDTO(message, user.getEmail(), NotificationType.SP_TRUSTED_LABEL_EMAIL));
            }
        } catch (Exception e) {
            log.warn("Failed to send trusted label notification for service {}", serviceId, e);
        }
    }
}
