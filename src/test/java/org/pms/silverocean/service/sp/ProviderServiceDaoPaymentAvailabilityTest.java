package org.pms.silverocean.service.sp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pms.silverocean.database.pms.ProviderServiceRepo;
import org.pms.silverocean.database.pms.ServiceTierRepo;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.payment.PaymentPlatformFactory;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.springframework.data.domain.PageRequest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderServiceDaoPaymentAvailabilityTest {

    @Mock private ProviderServiceRepo repo;
    @Mock private AuditLogService auditLogService;
    @Mock private ServiceTierRepo tierRepo;
    @Mock private PaymentPlatformFactory paymentPlatformFactory;

    private ProviderServiceDao dao;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        dao = new ProviderServiceDao(repo, auditLogService, tierRepo, paymentPlatformFactory);
    }

    @Test
    void directoryReturnsAnEmptyPageWithoutQueryingWhenEveryRailIsDisabled() {
        when(paymentPlatformFactory.getAvailableChannelsForNewPayments()).thenReturn(Set.of());
        var pageable = PageRequest.of(2, 24);

        var result = dao.findListedByCategoryEnriched(pageable, null);

        assertThat(result).isEmpty();
        assertThat(result.getPageable()).isEqualTo(pageable);
        verify(repo, never()).findListedByCategoryEnriched(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void serviceDetailsAreHiddenWhenEveryRailIsDisabled() {
        when(paymentPlatformFactory.getAvailableChannelsForNewPayments()).thenReturn(Set.of());

        assertThat(dao.findByIdEnriched(41L)).isEmpty();

        verify(repo, never()).findByIdEnriched(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void directoryPredicateReceivesOnlyRuntimeEnabledRails() {
        Set<PaymentChannel> enabled = Set.of(PaymentChannel.MPESA, PaymentChannel.PESA_LINK);
        when(paymentPlatformFactory.getAvailableChannelsForNewPayments()).thenReturn(enabled);
        when(repo.findListedByCategoryEnriched(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        dao.findListedByCategoryEnriched(PageRequest.of(0, 24), 8L);

        verify(repo).findListedByCategoryEnriched(any(), any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(enabled));
    }
}
