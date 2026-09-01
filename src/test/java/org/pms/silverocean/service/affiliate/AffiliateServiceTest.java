package org.pms.silverocean.service.affiliate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AffiliateServiceTest {
    @Mock AffiliateProfileRepo profiles;
    @Mock AffiliateReferralRepo referrals;
    @Mock AffiliateCommissionRepo commissions;
    @Mock AffiliatePayoutRepo payouts;
    @Mock UserDao users;
    @Mock InvoiceDao invoices;
    @Mock AccountDao accounts;
    AffiliateService service;

    @BeforeEach
    void setup() {
        service = new AffiliateService(profiles, referrals, commissions, payouts, users, invoices, accounts);
        ReflectionTestUtils.setField(service, "defaultRate", BigDecimal.valueOf(25));
        ReflectionTestUtils.setField(service, "eligiblePaymentCount", 3);
        ReflectionTestUtils.setField(service, "defaultMinimumPayout", BigDecimal.valueOf(1000));
        ReflectionTestUtils.setField(service, "commissionHoldDays", 14);
    }

    @Test
    void paidReferredSubscriptionCreatesPendingCommissionOnce() {
        PMSInvoice invoice = paidSubscription();
        AffiliateReferral referral = referral();
        AffiliateProfile profile = profile();
        when(invoices.getInvoiceById(10L)).thenReturn(Optional.of(invoice));
        when(referrals.findForCommissionByReferredUserId(8L)).thenReturn(Optional.of(referral));
        when(profiles.findByUserIdAndActiveTrue(4L)).thenReturn(Optional.of(profile));
        when(commissions.save(any())).thenAnswer(i -> i.getArgument(0));

        ZonedDateTime before = ZonedDateTime.now().plusDays(13);
        service.recordPaidConversion(10L, "PAY-1");

        verify(commissions).save(argThat(c ->
                c.getCommissionAmount().compareTo(BigDecimal.valueOf(875).setScale(2)) == 0
                        && "PENDING".equals(c.getStatus())
                        && c.getEligibleSequence() == 1
                        && c.getAvailableAt().isAfter(before)));
        assertEquals("CONVERTED", referral.getStatus());
        when(commissions.existsByInvoiceId(10L)).thenReturn(true);
        service.recordPaidConversion(10L, "PAY-1");
        verify(commissions, times(1)).save(any());
    }

    @Test
    void maturedCommissionsAreReleasedInBoundedBatch() {
        AffiliateCommission commission = new AffiliateCommission();
        commission.setStatus("PENDING");
        when(commissions.findTop200ByStatusAndActiveTrueAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
                eq("PENDING"), any())).thenReturn(List.of(commission));

        service.releaseMaturedCommissions();

        assertEquals("EARNED", commission.getStatus());
        verify(commissions).saveAll(List.of(commission));
    }

    @Test
    void nonSubscriptionPaymentDoesNotEarnCommission() {
        PMSInvoice invoice = paidSubscription();
        invoice.setBillingType("SOKO");
        when(invoices.getInvoiceById(10L)).thenReturn(Optional.of(invoice));

        service.recordPaidConversion(10L, "PAY-1");

        verifyNoInteractions(referrals);
        verify(commissions, never()).save(any());
    }

    @Test
    void fourthEligiblePaymentDoesNotEarnCommission() {
        PMSInvoice invoice = paidSubscription();
        invoice.setId(14L);
        invoice.setRef("INV-D");
        AffiliateReferral referral = referral();
        when(invoices.getInvoiceById(14L)).thenReturn(Optional.of(invoice));
        when(referrals.findForCommissionByReferredUserId(8L)).thenReturn(Optional.of(referral));
        when(profiles.findByUserIdAndActiveTrue(4L)).thenReturn(Optional.of(profile()));
        when(commissions.countByReferredUserIdAndActiveTrue(8L)).thenReturn(3L);

        service.recordPaidConversion(14L, "PAY-4");

        verify(commissions, never()).save(any());
    }

    @Test
    void payoutReservesNetEarningsAndSnapshotsVerifiedDestination() {
        AffiliateProfile profile = profile();
        profile.setCurrency("KES");
        profile.setMinimumPayout(BigDecimal.valueOf(1000));
        profile.setPayoutAccountId(12L);
        PaymentAccount account = new PaymentAccount();
        account.setId(12L);
        account.setName("Affiliate M-Pesa");
        account.setChannel(PaymentChannel.MPESA);
        account.setCategory(AccountCategory.AFFILIATE);
        account.setVerified(true);
        account.setActive(true);
        when(users.hasRole(PMSRole.AFFILIATE)).thenReturn(true);
        when(users.getUserId()).thenReturn(4L);
        when(profiles.findByUserIdAndActiveTrue(4L)).thenReturn(Optional.of(profile));
        when(profiles.findForUpdateByUserId(4L)).thenReturn(Optional.of(profile));
        when(accounts.getAccountByIdAndCreatedBy(12L, 4L)).thenReturn(account);
        when(commissions.sumByStatus(4L, "KES", "EARNED")).thenReturn(BigDecimal.valueOf(1800));
        when(commissions.sumByStatus(4L, "KES", "CLAWBACK_DUE")).thenReturn(BigDecimal.valueOf(200));
        when(payouts.save(any())).thenAnswer(i -> {
            AffiliatePayout payout = i.getArgument(0);
            payout.setId(44L);
            return payout;
        });

        var result = service.requestPayout();

        assertEquals(0, result.amount().compareTo(BigDecimal.valueOf(1600)));
        verify(commissions).claimEarned(4L, "KES", 44L);
        verify(commissions).claimClawbacks(4L, "KES", 44L);
        verify(payouts).save(argThat(p -> "Affiliate M-Pesa".equals(p.getPayoutAccountName())
                && "MPESA".equals(p.getPayoutChannel())));
    }

    @Test
    void reversalAfterPaidCommissionCreatesClawback() {
        AffiliateCommission commission = new AffiliateCommission();
        commission.setInvoiceId(10L);
        commission.setStatus("PAID");
        commission.setCommissionAmount(BigDecimal.valueOf(300));
        when(commissions.findForUpdateByInvoiceId(10L)).thenReturn(Optional.of(commission));

        service.reverseCommission(10L, "Chargeback confirmed");

        assertEquals("CLAWBACK_DUE", commission.getStatus());
        assertEquals("Chargeback confirmed", commission.getReversalReason());
        assertNotNull(commission.getReversedAt());
        verify(commissions).save(commission);
    }

    private static PMSInvoice paidSubscription() {
        PMSInvoice invoice = new PMSInvoice();
        invoice.setId(10L);
        invoice.setRef("INV-A");
        invoice.setBilledUserId(8L);
        invoice.setAmount(3500);
        invoice.setCurrency("KES");
        invoice.setBillingType("SUBSCRIPTION");
        invoice.setPaid(true);
        invoice.setPendingAmount(0);
        return invoice;
    }

    private static AffiliateReferral referral() {
        AffiliateReferral referral = new AffiliateReferral();
        referral.setAffiliateUserId(4L);
        referral.setReferredUserId(8L);
        referral.setStatus("REGISTERED");
        referral.setActive(true);
        return referral;
    }

    private static AffiliateProfile profile() {
        AffiliateProfile profile = new AffiliateProfile();
        profile.setUserId(4L);
        profile.setStatus("ACTIVE");
        profile.setCommissionRate(BigDecimal.valueOf(25));
        profile.setActive(true);
        return profile;
    }
}
