package org.pms.silverocean.service.property;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.database.pms.PMSInvoiceRepo;
import org.pms.silverocean.database.pms.PMSPaymentRepo;
import org.pms.silverocean.database.pms.PropertyRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.UnitTenantRepo;
import org.pms.silverocean.service.lease.wrappers.TenancyProjection;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UnitReportDao {
    private final UnitRepo unitRepo;
    private final UnitTenantRepo unitTenantRepo;
    private final PropertyRepo propertyRepo;
    private final PMSPaymentRepo pmsPaymentRepo;
    private final PMSInvoiceRepo pmsInvoiceRepo;


    public int countPropertiesByOwner(long userId) {
        return propertyRepo.countPropertyByLandlord(userId);
    }

    public int countUnitsByOwner(long userId) {
        return unitRepo.countUnitsByLandlord(userId);
    }

    public int countActiveTenantsByOwner(long userId) {
        return unitTenantRepo.countTenantsByLandlord(userId);
    }

    public BigDecimal sumMonthlyRevenueByOwner(long userId) {
        YearMonth now = YearMonth.now(PMSUtils.getZoneId());
        ZonedDateTime start = now.atDay(1).atStartOfDay(PMSUtils.getZoneId());
        return BigDecimal.valueOf(pmsPaymentRepo.sumMonthlyRevenueByLandlord(userId, start, ZonedDateTime.now(PMSUtils.getZoneId())));
    }

    public int countTotalActiveProperties() {
        return propertyRepo.countAllActiveProperty();
    }

    public List<TenancyProjection> countUnitsOccupiedByTenantWithUserId(long userId) {
        return unitTenantRepo.findByUserIdAndActiveTrue(userId);
    }

    public int countUnPaidInvoicesByTenantWithUserId(long userId) {
        return pmsInvoiceRepo.countInvoicesByTenantWithUserIdAndPaidStatus(userId, false);
    }

    public int countPaidInvoicesByTenantWithUserId(long userId) {
        return pmsInvoiceRepo.countInvoicesByTenantWithUserIdAndPaidStatus(userId, true);
    }

    public int countUnitsOccupiedByPropertyManager(long userId) {
        return unitRepo.countOccupiedUnitsByPropertyManager(userId);
    }

    public int countAllUnitsByPropertyManager(long userId) {
        return unitRepo.countAllUnitsByPropertyManager(userId);
    }

    public int countAllPropertyByPropertyManager(long userId) {
        return propertyRepo.countPropertyByPropertyManager(userId);
    }

    public int countUnsignedLeasesByPropertyManager(long userId) {
        return unitTenantRepo.countUnsignedLeasesByPropertyManager(userId);
    }
}
