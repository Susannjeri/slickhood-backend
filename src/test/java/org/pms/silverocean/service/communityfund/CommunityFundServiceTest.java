package org.pms.silverocean.service.communityfund;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.payment.invoice.InvoiceService;
import org.pms.silverocean.service.payment.ledger.FinancialLedgerService;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityFundServiceTest {
    @Mock CommunityFundRepo funds;
    @Mock CommunityFundContributionRepo contributions;
    @Mock CommunityFundExpenditureRepo expenditures;
    @Mock CommunityFundTransactionRepo transactions;
    @Mock PropertyRepo properties;
    @Mock PropertyOwnershipRepo ownerships;
    @Mock UnitTenantRepo tenancies;
    @Mock AccountDao accounts;
    @Mock UserDao users;
    @Mock InvoiceService invoices;
    @Mock FinancialLedgerService ledger;
    CommunityFundService service;

    @BeforeEach void setup() {
        service = new CommunityFundService(funds, contributions, expenditures, transactions, properties,
                ownerships, tenancies, accounts, users, invoices, ledger);
    }

    @Test void callbackRejectsWrongCurrencyWithoutRecordingReceipt() {
        callbackFixture();
        assertThrows(PMSCustomException.class, () -> service.completePaidInvoice(90L, "receipt", new BigDecimal("100"), "USD", null));
        verify(contributions, never()).save(any());
        verify(transactions, never()).save(any());
    }

    @Test void callbackRejectsPartialAmountRatherThanClaimingPaid() {
        callbackFixture();
        assertThrows(PMSCustomException.class, () -> service.completePaidInvoice(90L, "receipt", new BigDecimal("10"), "KES", null));
        verify(contributions, never()).save(any());
    }

    @Test void completedReceiptReplayDoesNotWriteAgain() {
        var contribution = new CommunityFundContribution(); contribution.setFundId(1L);
        when(contributions.findByInvoiceIdAndActiveTrue(90L)).thenReturn(Optional.of(contribution));
        when(transactions.existsByEventKey("COMMUNITY_FUND_CONTRIBUTION:90")).thenReturn(true);
        service.completePaidInvoice(90L, "receipt", new BigDecimal("100"), "KES", null);
        verifyNoInteractions(funds);
        verify(contributions, never()).save(any());
    }

    @Test void makerCannotApproveOwnRequestWithDualApproval() {
        var expense = new CommunityFundExpenditure(); expense.setFundId(1L); expense.setActive(true);
        expense.setStatus("REQUESTED"); expense.setCreatedBy(7L);
        var fund = new CommunityFund(); fund.setId(1L); fund.setPropertyId(10L); fund.setActive(true); fund.setDualApprovalRequired(true);
        when(users.getUserId()).thenReturn(7L); when(users.getActiveRole()).thenReturn(PMSRole.LANDLORD);
        when(expenditures.findForUpdate(2L)).thenReturn(Optional.of(expense));
        when(funds.findForUpdate(1L)).thenReturn(Optional.of(fund));
        when(funds.findById(1L)).thenReturn(Optional.of(fund));
        when(properties.findByIdAndCreatedByAndActiveTrue(10L,7L)).thenReturn(Optional.of(new Property()));
        assertThrows(PMSCustomException.class, () -> service.approve(2L));
        verify(expenditures, never()).save(any());
    }

    @Test void memberActivityDoesNotDiscloseAnotherContributorsPaymentReference() {
        var t = new CommunityFundTransaction(); t.setId(1L); t.setContributorUserId(7L); t.setExternalReference("PRIVATE-RECEIPT");
        assertNull(CommunityFundModels.FundTransactionView.forViewer(t, 8L, false).externalReference());
        assertEquals("PRIVATE-RECEIPT", CommunityFundModels.FundTransactionView.forViewer(t, 7L, false).externalReference());
        assertEquals("PRIVATE-RECEIPT", CommunityFundModels.FundTransactionView.forViewer(t, 8L, true).externalReference());
    }

    @Test void monetaryMutationRepositoriesRequireDatabaseWriteLocks() throws Exception {
        for (var repo : java.util.List.of(CommunityFundRepo.class, CommunityFundExpenditureRepo.class, CommunityFundContributionRepo.class)) {
            var lock = repo.getMethod("findForUpdate", long.class).getAnnotation(org.springframework.data.jpa.repository.Lock.class);
            assertEquals(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE, lock.value());
        }
    }

    private void callbackFixture() {
        var contribution = new CommunityFundContribution(); contribution.setFundId(1L); contribution.setAssessedAmount(new BigDecimal("100"));
        var fund = new CommunityFund(); fund.setCurrency("KES");
        when(contributions.findByInvoiceIdAndActiveTrue(90L)).thenReturn(Optional.of(contribution));
        when(funds.findForUpdate(1L)).thenReturn(Optional.of(fund));
    }
}
