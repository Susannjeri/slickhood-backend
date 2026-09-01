package org.pms.silverocean.service.dashboard;

import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.dao.UserReportDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.currencyexchange.CurrencyConversionService;
import org.pms.silverocean.service.dashboard.wrappers.GuardDto;
import org.pms.silverocean.service.dashboard.wrappers.LandLordDto;
import org.pms.silverocean.service.dashboard.wrappers.PropertyManagerDto;
import org.pms.silverocean.service.dashboard.wrappers.ReportDto;
import org.pms.silverocean.service.dashboard.wrappers.ServiceProviderReportDto;
import org.pms.silverocean.service.dashboard.wrappers.SuperAdminReportDto;
import org.pms.silverocean.service.dashboard.wrappers.TenantDto;
import org.pms.silverocean.service.dashboard.wrappers.BusinessRoleDashboardDto;
import org.pms.silverocean.database.pms.PropertyOwnershipRepo;
import org.pms.silverocean.database.pms.SaleTransactionRepo;
import org.pms.silverocean.database.pms.LeaseDocumentRepo;
import org.pms.silverocean.database.pms.EstateServiceChargeRepo;
import org.pms.silverocean.service.sales.SaleStatus;
import org.pms.silverocean.service.leasedocument.LeaseDocumentStatus;
import org.pms.silverocean.service.lease.wrappers.TenancyProjection;
import org.pms.silverocean.service.payment.invoice.InvoiceReportDao;
import org.pms.silverocean.service.property.UnitReportDao;
import org.pms.silverocean.service.sp.dao.ServiceProviderReportDao;
import org.pms.silverocean.service.threadpooling.PMSThreadPoolExecutorService;
import org.pms.silverocean.service.threadpooling.ThreadPoolBeans;
import org.pms.silverocean.service.visitor.VisitorReportDao;
import org.pms.silverocean.database.pms.InsuranceEmailExchangeRepo;
import org.pms.silverocean.database.pms.AffiliateCommissionRepo;
import org.pms.silverocean.database.pms.AffiliateReferralRepo;
import org.pms.silverocean.database.pms.WealthAssetRepo;
import org.pms.silverocean.database.pms.WealthGoalRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class DashBoardService {
    private final UnitReportDao unitReportDao;
    private final UserDao userDao;
    private final PMSThreadPoolExecutorService reportExecutorService;
    private final UserReportDao userReportDao;
    private final InvoiceReportDao invoiceReportDao;
    private final CurrencyConversionService currencyConversionService;
    private final ServiceProviderReportDao serviceProviderReportDao;
    private final VisitorReportDao visitorReportDao;
    private final PropertyOwnershipRepo ownershipRepo;
    private final SaleTransactionRepo saleRepo;
    private final LeaseDocumentRepo documentRepo;
    private final EstateServiceChargeRepo chargeRepo;
    private final InsuranceEmailExchangeRepo insuranceEmailExchangeRepo;
    private final AffiliateReferralRepo affiliateReferralRepo;
    private final AffiliateCommissionRepo affiliateCommissionRepo;
    private final WealthAssetRepo wealthAssetRepo;
    private final WealthGoalRepo wealthGoalRepo;

    public DashBoardService(UnitReportDao unitReportDao, UserDao userDao, ThreadPoolBeans threadPoolBeans,
                            UserReportDao userReportDao, InvoiceReportDao invoiceReportDao,
                            CurrencyConversionService currencyConversionService, ServiceProviderReportDao serviceProviderReportDao, VisitorReportDao visitorReportDao,
                            PropertyOwnershipRepo ownershipRepo, SaleTransactionRepo saleRepo, LeaseDocumentRepo documentRepo,
                            EstateServiceChargeRepo chargeRepo, InsuranceEmailExchangeRepo insuranceEmailExchangeRepo,
                            AffiliateReferralRepo affiliateReferralRepo, AffiliateCommissionRepo affiliateCommissionRepo,
                            WealthAssetRepo wealthAssetRepo, WealthGoalRepo wealthGoalRepo,
                            @Value("${spring.datasource.hikari.maximum-pool-size:10}") int maxPoolSize) {
        this.unitReportDao = unitReportDao;
        this.userDao = userDao;
        this.reportExecutorService = threadPoolBeans.ioExecutorService("REPORTS", Math.max(1, maxPoolSize / 2));
        this.userReportDao = userReportDao;
        this.invoiceReportDao = invoiceReportDao;
        this.currencyConversionService = currencyConversionService;
        this.serviceProviderReportDao = serviceProviderReportDao;
        this.visitorReportDao = visitorReportDao;
        this.ownershipRepo = ownershipRepo;
        this.saleRepo = saleRepo;
        this.documentRepo = documentRepo;
        this.chargeRepo = chargeRepo;
        this.insuranceEmailExchangeRepo = insuranceEmailExchangeRepo;
        this.affiliateReferralRepo = affiliateReferralRepo;
        this.affiliateCommissionRepo = affiliateCommissionRepo;
        this.wealthAssetRepo = wealthAssetRepo;
        this.wealthGoalRepo = wealthGoalRepo;
    }

    public CompletableFuture<ReportDto> getReportDtoPerActiveRole(PMSRole activeRole) {
        if (activeRole != userDao.getActiveRole()) {
            return CompletableFuture.failedFuture(new PMSCustomException(ResponseCode.INVALID_ROLE));
        }
        return switch (activeRole) {
            case LANDLORD -> buildLandlordDto().thenApply(dto -> (ReportDto) dto);
            case SERVICE_PROVIDER -> buildServiceProviderDto().thenApply(dto -> (ReportDto) dto);
            case ASSET_PORTFOLIO_MANAGER -> buildWealthDto(activeRole).thenApply(dto -> (ReportDto) dto);
            case AFFILIATE -> buildAffiliateDto(activeRole).thenApply(dto -> (ReportDto) dto);
            case SUPPORT, SALES_MARKETING, FINANCE -> buildPlatformStaffDto(activeRole).thenApply(dto -> (ReportDto) dto);
            case TENANT -> buildTenantDto().thenApply(dto -> (ReportDto) dto);
            case PROPERTY_MANAGER -> buildPropertyManagerDto().thenApply(dto -> (ReportDto) dto);
            case WORKSPACE_ADMIN, PROPERTY_ACCOUNTANT, LEASING_OFFICER, ESTATE_OPERATIONS_MANAGER,
                 SECURITY_SUPERVISOR, WORKSPACE_VIEWER -> buildWorkspaceStaffDto(activeRole).thenApply(dto -> (ReportDto) dto);
            case SALES_COORDINATOR, LISTING_AGENT -> buildSalesTeamDto(activeRole).thenApply(dto -> (ReportDto) dto);
            case ESTATE_MANAGER -> buildEstateManagerDto().thenApply(dto -> (ReportDto) dto);
            case HOMEOWNER -> buildHomeownerDto().thenApply(dto -> (ReportDto) dto);
            case SALES_AGENT -> buildSalesAgentDto().thenApply(dto -> (ReportDto) dto);
            case BUYER -> buildBuyerDto().thenApply(dto -> (ReportDto) dto);
            case INSURANCE_ADVISER, INSURANCE_MANAGER -> buildInsuranceDto(activeRole).thenApply(dto -> (ReportDto) dto);
            case GUARD -> buildGuardDto().thenApply(dto -> (ReportDto) dto);
            case SUPER_ADMIN -> buildSuperAdminDto().thenApply(dto -> (ReportDto) dto);
        };
    }

    private CompletableFuture<BusinessRoleDashboardDto> buildPlatformStaffDto(PMSRole role) {
        long activeProperties = unitReportDao.countTotalActiveProperties();
        long monthlyUsers = userReportDao.getUserLoggedInWithinCurrentMonth();
        long healthyUsers = Math.max(0, Math.round(100 - userReportDao.getInActiveUserPercentage()));
        long subscriptionCollections = invoiceReportDao.getTotalPaidSubscriptionsWithinCurrentMonth().stream()
                .map(amount -> currencyConversionService.convert(amount.getAmount(), amount.getCurrency(), "KES"))
                .reduce(BigDecimal.ZERO, BigDecimal::add).longValue();
        return CompletableFuture.completedFuture(new BusinessRoleDashboardDto(role.name(), activeProperties, monthlyUsers,
                healthyUsers, subscriptionCollections, "Active properties", "Users active this month",
                "Active user rate %", "Subscription collections (KES)"));
    }

    private CompletableFuture<BusinessRoleDashboardDto> buildInsuranceDto(PMSRole role) {
        long queued = insuranceEmailExchangeRepo.countByDirectionAndStatusAndActiveTrue("OUTBOUND", "QUEUED");
        long sent = insuranceEmailExchangeRepo.countByDirectionAndStatusAndActiveTrue("OUTBOUND", "SENT");
        long received = insuranceEmailExchangeRepo.countByDirectionAndStatusAndActiveTrue("INBOUND", "RECEIVED_UNVERIFIED");
        long failed = insuranceEmailExchangeRepo.countByDirectionAndStatusAndActiveTrue("OUTBOUND", "FAILED");
        return CompletableFuture.completedFuture(new BusinessRoleDashboardDto(role.name(), queued, sent, received,
                failed, "Requests queued", "Requests sent", "Responses awaiting review", "Failed requests"));
    }

    private CompletableFuture<BusinessRoleDashboardDto> buildAffiliateDto(PMSRole role) {
        long userId = userDao.getUserId();
        long referrals = affiliateReferralRepo.countByAffiliateUserIdAndActiveTrue(userId);
        long conversions = affiliateReferralRepo.countByAffiliateUserIdAndStatusAndActiveTrue(userId, "CONVERTED");
        long available = affiliateCommissionRepo.countByAffiliateUserIdAndStatusAndActiveTrue(userId, "EARNED");
        long paid = affiliateCommissionRepo.countByAffiliateUserIdAndStatusAndActiveTrue(userId, "PAID");
        return CompletableFuture.completedFuture(new BusinessRoleDashboardDto(role.name(), referrals, conversions,
                available, paid, "Referrals", "Conversions", "Available commissions", "Paid commissions"));
    }

    private CompletableFuture<BusinessRoleDashboardDto> buildWealthDto(PMSRole role) {
        long userId = userDao.getUserId();
        long assets = wealthAssetRepo.countByOwnerUserIdAndActiveTrue(userId);
        long connectedProperties = wealthAssetRepo.countByOwnerUserIdAndPropertyIdIsNotNullAndActiveTrue(userId);
        long goals = wealthGoalRepo.countByOwnerUserIdAndActiveTrue(userId);
        return CompletableFuture.completedFuture(new BusinessRoleDashboardDto(role.name(), assets,
                connectedProperties, goals, 0, "Active assets", "Connected properties", "Active goals",
                "Actions completed"));
    }

    private CompletableFuture<BusinessRoleDashboardDto> buildWorkspaceStaffDto(PMSRole role) {
        long userId = userDao.getUserId();
        long units = unitReportDao.countAllUnitsByPropertyManager(userId);
        long properties = unitReportDao.countAllPropertyByPropertyManager(userId);
        long occupied = unitReportDao.countUnitsOccupiedByPropertyManager(userId);
        long unsigned = unitReportDao.countUnsignedLeasesByPropertyManager(userId);
        return CompletableFuture.completedFuture(new BusinessRoleDashboardDto(role.name(), properties, units, occupied,
                unsigned, "Assigned properties", "Managed units", "Occupied units", "Leases awaiting signature"));
    }

    private CompletableFuture<BusinessRoleDashboardDto> buildSalesTeamDto(PMSRole role) {
        long userId = userDao.getUserId();
        return CompletableFuture.completedFuture(new BusinessRoleDashboardDto(role.name(),
                saleRepo.countBySalesAgentUserIdAndActiveTrue(userId),
                saleRepo.countBySalesAgentUserIdAndStatusAndActiveTrue(userId, SaleStatus.OFFERED),
                saleRepo.countBySalesAgentUserIdAndStatusAndActiveTrue(userId, SaleStatus.DUE_DILIGENCE),
                saleRepo.countBySalesAgentUserIdAndStatusAndActiveTrue(userId, SaleStatus.COMPLETED),
                "Active sales", "Offers", "Due diligence", "Completed sales"));
    }

    private CompletableFuture<BusinessRoleDashboardDto> buildEstateManagerDto() {
        long userId = userDao.getUserId();
        return CompletableFuture.completedFuture(new BusinessRoleDashboardDto("ESTATE_MANAGER",
                ownershipRepo.countActiveByManager(userId, PMSRole.ESTATE_MANAGER.name()), chargeRepo.countOutstandingByManager(userId, PMSRole.ESTATE_MANAGER.name()),
                documentRepo.countByRecipientUserIdAndStatusAndActiveTrue(userId, LeaseDocumentStatus.ISSUED), 0,
                "Managed homeowners", "Outstanding service charges", "Documents awaiting action", "Resolved work orders"));
    }

    private CompletableFuture<BusinessRoleDashboardDto> buildHomeownerDto() {
        long userId = userDao.getUserId();
        return CompletableFuture.completedFuture(new BusinessRoleDashboardDto("HOMEOWNER",
                ownershipRepo.countByHomeownerUserIdAndActiveTrue(userId), chargeRepo.countOutstandingByHomeowner(userId),
                documentRepo.countByRecipientUserIdAndStatusAndActiveTrue(userId, LeaseDocumentStatus.ISSUED), 0,
                "Owned units", "Outstanding service charges", "Documents awaiting action", "Completed service requests"));
    }

    private CompletableFuture<BusinessRoleDashboardDto> buildSalesAgentDto() {
        long userId = userDao.getUserId();
        return CompletableFuture.completedFuture(new BusinessRoleDashboardDto("SALES_AGENT",
                saleRepo.countBySalesAgentUserIdAndActiveTrue(userId),
                saleRepo.countBySalesAgentUserIdAndStatusAndActiveTrue(userId, SaleStatus.OFFERED),
                saleRepo.countBySalesAgentUserIdAndStatusAndActiveTrue(userId, SaleStatus.DUE_DILIGENCE),
                saleRepo.countBySalesAgentUserIdAndStatusAndActiveTrue(userId, SaleStatus.COMPLETED),
                "Active sales", "Offers", "Due diligence", "Completed sales"));
    }

    private CompletableFuture<BusinessRoleDashboardDto> buildBuyerDto() {
        long userId = userDao.getUserId();
        return CompletableFuture.completedFuture(new BusinessRoleDashboardDto("BUYER",
                saleRepo.countByBuyerUserIdAndActiveTrue(userId),
                saleRepo.countByBuyerUserIdAndStatusAndActiveTrue(userId, SaleStatus.OFFERED),
                documentRepo.countByRecipientUserIdAndStatusAndActiveTrue(userId, LeaseDocumentStatus.ISSUED),
                saleRepo.countByBuyerUserIdAndStatusAndActiveTrue(userId, SaleStatus.COMPLETED),
                "Properties in progress", "Offers to review", "Documents awaiting action", "Completed purchases"));
    }

    private CompletableFuture<LandLordDto> buildLandlordDto() {
        if (!userDao.hasRole(PMSRole.LANDLORD)) {
            return CompletableFuture.failedFuture(new PMSCustomException(ResponseCode.INVALID_ROLE));
        }
        long userId = userDao.getUserId();

        CompletableFuture<Integer> totalPropertiesFuture = reportExecutorService.submit(() -> unitReportDao.countPropertiesByOwner(userId));
        CompletableFuture<Integer> totalUnitsFuture = reportExecutorService.submit(() -> unitReportDao.countUnitsByOwner(userId));
        CompletableFuture<Integer> activeTenantsFuture = reportExecutorService.submit(() -> unitReportDao.countActiveTenantsByOwner(userId));
        CompletableFuture<BigDecimal> monthlyRevenueFuture = reportExecutorService.submit(() -> unitReportDao.sumMonthlyRevenueByOwner(userId));

        return CompletableFuture.allOf(totalPropertiesFuture, totalUnitsFuture, activeTenantsFuture, monthlyRevenueFuture)
                .thenApply(__ -> new LandLordDto(totalPropertiesFuture.join(), totalUnitsFuture.join(), activeTenantsFuture.join(), monthlyRevenueFuture.join()));

    }

    private CompletableFuture<SuperAdminReportDto> buildSuperAdminDto() {
        if (!userDao.hasRole(PMSRole.SUPER_ADMIN)) {
            return CompletableFuture.failedFuture(new PMSCustomException(ResponseCode.INVALID_ROLE));
        }

        CompletableFuture<Double> inActiveUserPercentFuture = reportExecutorService.submit(userReportDao::getInActiveUserPercentage);
        CompletableFuture<Integer> userLoggedInWithinCurrentMonthFuture = reportExecutorService.submit(userReportDao::getUserLoggedInWithinCurrentMonth);
        CompletableFuture<Integer> totalActivePropertiesFuture = reportExecutorService.submit(unitReportDao::countTotalActiveProperties);
        CompletableFuture<BigDecimal> totalSubscriptionPaidWithinCurrentMonthFuture = reportExecutorService.submit(() -> invoiceReportDao.getTotalPaidSubscriptionsWithinCurrentMonth()
                .stream()
                .map(amountAndCurrency -> currencyConversionService.convert(amountAndCurrency.getAmount(), amountAndCurrency.getCurrency(), "KES"))
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        return CompletableFuture.allOf(inActiveUserPercentFuture, userLoggedInWithinCurrentMonthFuture, totalActivePropertiesFuture, totalSubscriptionPaidWithinCurrentMonthFuture)
                .thenApply(__ -> new SuperAdminReportDto(inActiveUserPercentFuture.join(), userLoggedInWithinCurrentMonthFuture.join(), totalActivePropertiesFuture.join(), totalSubscriptionPaidWithinCurrentMonthFuture.join()));


    }

    private CompletableFuture<ServiceProviderReportDto> buildServiceProviderDto() {
        if (!userDao.hasRole(PMSRole.SERVICE_PROVIDER)) {
            return CompletableFuture.failedFuture(new PMSCustomException(ResponseCode.INVALID_ROLE));
        }
        long userId = userDao.getUserId();


        CompletableFuture<Integer> bookingCountThisMonthFuture = reportExecutorService.submit(() -> serviceProviderReportDao.getServiceProviderBookingCountForCurrentMonth(userId));
        CompletableFuture<Integer> bookingCountLastMonthFuture = reportExecutorService.submit(() -> serviceProviderReportDao.getServiceProviderBookingCountForPreviousMonth(userId));
        CompletableFuture<Double> averageRatingFuture = reportExecutorService.submit(() -> serviceProviderReportDao.getServiceProviderAverageRatingForAllBookings(userId));
        CompletableFuture<Integer> mostRecentRatingFuture = reportExecutorService.submit(() -> serviceProviderReportDao.getMostRecentRatingForServiceProvider(userId));

        return CompletableFuture.allOf(bookingCountThisMonthFuture, bookingCountLastMonthFuture, averageRatingFuture, mostRecentRatingFuture)
                .thenApply(__ -> new ServiceProviderReportDto(bookingCountThisMonthFuture.join(), bookingCountLastMonthFuture.join(), averageRatingFuture.join(), mostRecentRatingFuture.join()));
    }

    private CompletableFuture<TenantDto> buildTenantDto() {
        if (!userDao.hasRole(PMSRole.TENANT)) {
            return CompletableFuture.failedFuture(new PMSCustomException(ResponseCode.INVALID_ROLE));
        }
        long userId = userDao.getUserId();


        CompletableFuture<List<TenancyProjection>> tenancyFuture = reportExecutorService.submit(() -> unitReportDao.countUnitsOccupiedByTenantWithUserId(userId));
        CompletableFuture<Integer> totalUnpaidInvoicesFuture = reportExecutorService.submit(() -> unitReportDao.countUnPaidInvoicesByTenantWithUserId(userId));
        CompletableFuture<Integer> totalPaidInvoicesFuture = reportExecutorService.submit(() -> unitReportDao.countPaidInvoicesByTenantWithUserId(userId));

        return CompletableFuture.allOf(tenancyFuture, totalUnpaidInvoicesFuture, totalPaidInvoicesFuture)
                .thenApply(__ -> {
                    List<TenancyProjection>  tenancyProjections = tenancyFuture.join();
                    int totalOccupiedUnits = 0, totalPendingLeases = 0;
                    for (TenancyProjection tenancy : tenancyProjections) {
                        if (tenancy.getLeaseAccepted()) {
                            totalOccupiedUnits++;
                        } else {
                            totalPendingLeases++;
                        }
                    }
                    return new TenantDto(totalOccupiedUnits, totalPendingLeases, totalUnpaidInvoicesFuture.join(), totalPaidInvoicesFuture.join());
                });
    }

    private CompletableFuture<GuardDto> buildGuardDto() {
        if (!userDao.hasRole(PMSRole.GUARD)) {
            return CompletableFuture.failedFuture(new PMSCustomException(ResponseCode.INVALID_ROLE));
        }
        long userId = userDao.getUserId();


        CompletableFuture<Integer> totalInsidePropertyFuture = reportExecutorService.submit(() -> visitorReportDao.countVisitorsInsidePropertyByGuardUserId(userId));
        CompletableFuture<Integer> totalDeliveryTodayFuture = reportExecutorService.submit(() -> visitorReportDao.countDeliveryExpectedTodayInPropertyByGuardUserId(userId));
        CompletableFuture<Integer> totalContractorsTodayFuture = reportExecutorService.submit(() -> visitorReportDao.countContractorsExpectedTodayInPropertyByGuardUserId(userId));
        CompletableFuture<Integer> totalGuestsTodayFuture = reportExecutorService.submit(() -> visitorReportDao.countGuestsExpectedTodayInPropertyByGuardUserId(userId));

        return CompletableFuture.allOf(totalInsidePropertyFuture, totalDeliveryTodayFuture, totalContractorsTodayFuture, totalGuestsTodayFuture)
                .thenApply(__ -> new GuardDto(totalInsidePropertyFuture.join(), totalDeliveryTodayFuture.join(), totalContractorsTodayFuture.join(), totalGuestsTodayFuture.join()));
    }

    private CompletableFuture<PropertyManagerDto> buildPropertyManagerDto() {
        if (!userDao.hasRole(PMSRole.PROPERTY_MANAGER)) {
            return CompletableFuture.failedFuture(new PMSCustomException(ResponseCode.INVALID_ROLE));
        }
        long userId = userDao.getUserId();


        CompletableFuture<Integer> totalManagedUnitsFuture = reportExecutorService.submit(() -> unitReportDao.countAllUnitsByPropertyManager(userId));
        CompletableFuture<Integer> totalManagedPropertiesFuture = reportExecutorService.submit(() -> unitReportDao.countAllPropertyByPropertyManager(userId));
        CompletableFuture<Integer> totalOccupiedUnitsFuture = reportExecutorService.submit(() -> unitReportDao.countUnitsOccupiedByPropertyManager(userId));
        CompletableFuture<Integer> totalPendingLeaseSignsFuture = reportExecutorService.submit(() -> unitReportDao.countUnsignedLeasesByPropertyManager(userId));

        return CompletableFuture.allOf(totalManagedUnitsFuture, totalManagedPropertiesFuture, totalOccupiedUnitsFuture, totalPendingLeaseSignsFuture)
                .thenApply(__ -> new PropertyManagerDto(totalManagedUnitsFuture.join(), totalManagedPropertiesFuture.join(), totalOccupiedUnitsFuture.join(), totalPendingLeaseSignsFuture.join()));
    }
}
