package org.pms.silverocean.service.auth.dao;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.database.pms.UserRepo;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class UserReportDao {
    private final UserRepo userRepo;

    public double getInActiveUserPercentage() {
        return userRepo.getActiveUserPercentage();
    }

    public int getUserLoggedInWithinCurrentMonth() {
        YearMonth now = YearMonth.now(PMSUtils.getZoneId());
        ZonedDateTime start = now.atDay(1).atStartOfDay(PMSUtils.getZoneId());
        return userRepo.countUsersLoggedInCurrentMonth(start, ZonedDateTime.now(PMSUtils.getZoneId()));
    }
}
