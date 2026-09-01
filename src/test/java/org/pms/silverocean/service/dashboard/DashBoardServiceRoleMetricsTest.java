package org.pms.silverocean.service.dashboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.AffiliateCommissionRepo;
import org.pms.silverocean.database.pms.AffiliateReferralRepo;
import org.pms.silverocean.database.pms.EstateServiceChargeRepo;
import org.pms.silverocean.database.pms.InsuranceEmailExchangeRepo;
import org.pms.silverocean.database.pms.LeaseDocumentRepo;
import org.pms.silverocean.database.pms.PropertyOwnershipRepo;
import org.pms.silverocean.database.pms.SaleTransactionRepo;
import org.pms.silverocean.database.pms.WealthAssetRepo;
import org.pms.silverocean.database.pms.WealthGoalRepo;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.dao.UserReportDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.currencyexchange.CurrencyConversionService;
import org.pms.silverocean.service.dashboard.wrappers.BusinessRoleDashboardDto;
import org.pms.silverocean.service.payment.invoice.InvoiceReportDao;
import org.pms.silverocean.service.property.UnitReportDao;
import org.pms.silverocean.service.sp.dao.ServiceProviderReportDao;
import org.pms.silverocean.service.threadpooling.PMSThreadPoolExecutorService;
import org.pms.silverocean.service.threadpooling.ThreadPoolBeans;
import org.pms.silverocean.service.visitor.VisitorReportDao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashBoardServiceRoleMetricsTest {
    private final UserDao users = mock(UserDao.class);
    private final AffiliateReferralRepo referrals = mock(AffiliateReferralRepo.class);
    private final AffiliateCommissionRepo commissions = mock(AffiliateCommissionRepo.class);
    private final InsuranceEmailExchangeRepo insurance = mock(InsuranceEmailExchangeRepo.class);
    private final WealthAssetRepo assets = mock(WealthAssetRepo.class);
    private final WealthGoalRepo goals = mock(WealthGoalRepo.class);
    private DashBoardService service;

    @BeforeEach
    void setUp() {
        ThreadPoolBeans pools = mock(ThreadPoolBeans.class);
        when(pools.ioExecutorService("REPORTS", 5)).thenReturn(mock(PMSThreadPoolExecutorService.class));
        service = new DashBoardService(mock(UnitReportDao.class), users, pools, mock(UserReportDao.class),
                mock(InvoiceReportDao.class), mock(CurrencyConversionService.class),
                mock(ServiceProviderReportDao.class), mock(VisitorReportDao.class),
                mock(PropertyOwnershipRepo.class), mock(SaleTransactionRepo.class), mock(LeaseDocumentRepo.class),
                mock(EstateServiceChargeRepo.class), insurance, referrals, commissions, assets, goals, 10);
        when(users.getUserId()).thenReturn(91L);
    }

    @Test
    void affiliateDashboardUsesScopedRealCounts() {
        when(users.getActiveRole()).thenReturn(PMSRole.AFFILIATE);
        when(referrals.countByAffiliateUserIdAndActiveTrue(91L)).thenReturn(12L);
        when(referrals.countByAffiliateUserIdAndStatusAndActiveTrue(91L, "CONVERTED")).thenReturn(7L);
        when(commissions.countByAffiliateUserIdAndStatusAndActiveTrue(91L, "EARNED")).thenReturn(3L);
        when(commissions.countByAffiliateUserIdAndStatusAndActiveTrue(91L, "PAID")).thenReturn(4L);

        BusinessRoleDashboardDto result = (BusinessRoleDashboardDto) service
                .getReportDtoPerActiveRole(PMSRole.AFFILIATE).join();

        assertThat(result).extracting(BusinessRoleDashboardDto::primaryCount,
                        BusinessRoleDashboardDto::secondaryCount, BusinessRoleDashboardDto::pendingActions,
                        BusinessRoleDashboardDto::completedCount)
                .containsExactly(12L, 7L, 3L, 4L);
    }

    @Test
    void wealthDashboardUsesOnlyTheActiveOwner() {
        when(users.getActiveRole()).thenReturn(PMSRole.ASSET_PORTFOLIO_MANAGER);
        when(assets.countByOwnerUserIdAndActiveTrue(91L)).thenReturn(8L);
        when(assets.countByOwnerUserIdAndPropertyIdIsNotNullAndActiveTrue(91L)).thenReturn(2L);
        when(goals.countByOwnerUserIdAndActiveTrue(91L)).thenReturn(5L);

        BusinessRoleDashboardDto result = (BusinessRoleDashboardDto) service
                .getReportDtoPerActiveRole(PMSRole.ASSET_PORTFOLIO_MANAGER).join();

        assertThat(result.primaryCount()).isEqualTo(8L);
        assertThat(result.secondaryCount()).isEqualTo(2L);
        assertThat(result.pendingActions()).isEqualTo(5L);
    }

    @Test
    void insuranceDashboardUsesCurrentCorrespondencePipeline() {
        when(users.getActiveRole()).thenReturn(PMSRole.INSURANCE_ADVISER);
        when(insurance.countByDirectionAndStatusAndActiveTrue("OUTBOUND", "QUEUED")).thenReturn(2L);
        when(insurance.countByDirectionAndStatusAndActiveTrue("OUTBOUND", "SENT")).thenReturn(9L);
        when(insurance.countByDirectionAndStatusAndActiveTrue("INBOUND", "RECEIVED_UNVERIFIED")).thenReturn(4L);
        when(insurance.countByDirectionAndStatusAndActiveTrue("OUTBOUND", "FAILED")).thenReturn(1L);

        BusinessRoleDashboardDto result = (BusinessRoleDashboardDto) service
                .getReportDtoPerActiveRole(PMSRole.INSURANCE_ADVISER).join();

        assertThat(result).extracting(BusinessRoleDashboardDto::primaryCount,
                        BusinessRoleDashboardDto::secondaryCount, BusinessRoleDashboardDto::pendingActions,
                        BusinessRoleDashboardDto::completedCount)
                .containsExactly(2L, 9L, 4L, 1L);
    }
}
