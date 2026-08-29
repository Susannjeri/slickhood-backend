package org.pms.silverocean.service.visitor;

import lombok.AllArgsConstructor;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.database.pms.VisitorRepo;
import org.pms.silverocean.service.visitor.enums.VisitorCategory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Service
@AllArgsConstructor
public class VisitorReportDao {
    private final VisitorRepo visitorRepo;

    public int countVisitorsInsidePropertyByGuardUserId(long userId) {
        return visitorRepo.countVisitorInsidePropertyByGuard(userId);
    }

    public int countDeliveryExpectedTodayInPropertyByGuardUserId(long userId) {
        ZonedDateTime startOfDay = LocalDate.now(PMSUtils.getZoneId()).atStartOfDay(PMSUtils.getZoneId());

        return visitorRepo.countVisitorExpectedWithinDateRangeByGuardAndVisitorCategory(startOfDay, startOfDay.plusDays(1), userId, VisitorCategory.DELIVERY.name());
    }

    public int countContractorsExpectedTodayInPropertyByGuardUserId(long userId) {
        ZonedDateTime startOfDay = LocalDate.now(PMSUtils.getZoneId()).atStartOfDay(PMSUtils.getZoneId());

        return visitorRepo.countVisitorExpectedWithinDateRangeByGuardAndVisitorCategory(startOfDay, startOfDay.plusDays(1), userId, VisitorCategory.CONTRACTOR.name());
    }

    public int countGuestsExpectedTodayInPropertyByGuardUserId(long userId) {
        ZonedDateTime startOfDay = LocalDate.now(PMSUtils.getZoneId()).atStartOfDay(PMSUtils.getZoneId());

        return visitorRepo.countVisitorExpectedWithinDateRangeByGuardAndVisitorCategory(startOfDay, startOfDay.plusDays(1), userId, VisitorCategory.GUEST.name());
    }
}
