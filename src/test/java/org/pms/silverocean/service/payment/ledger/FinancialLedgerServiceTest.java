package org.pms.silverocean.service.payment.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.FinancialJournalRepo;
import org.pms.silverocean.database.pms.FinancialLedgerLineRepo;
import org.pms.silverocean.database.pms.entities.FinancialJournal;
import org.pms.silverocean.database.pms.entities.FinancialLedgerLine;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PaymentOperation;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialLedgerServiceTest {
    @Mock FinancialJournalRepo journals;
    @Mock FinancialLedgerLineRepo lines;
    FinancialLedgerService service;

    @BeforeEach void setup(){service=new FinancialLedgerService(journals,lines);}

    @Test void invoicePostingCreatesBalancedImmutableLines(){
        PMSInvoice invoice=invoice();
        when(journals.save(any())).thenAnswer(invocation->{FinancialJournal journal=invocation.getArgument(0);journal.setId(31L);return journal;});
        assertTrue(service.recordInvoiceIssued(invoice));
        @SuppressWarnings("unchecked") ArgumentCaptor<List<FinancialLedgerLine>> captor=ArgumentCaptor.forClass(List.class);
        verify(lines).saveAll(captor.capture());
        List<FinancialLedgerLine> entries=captor.getValue();
        assertEquals(2,entries.size());
        assertEquals(new BigDecimal("1000.00"),entries.get(0).getDebit());
        assertEquals(new BigDecimal("1000.00"),entries.get(1).getCredit());
        assertEquals(entries.get(0).getDebit(),entries.get(1).getCredit());
        assertEquals("ACCOUNTS_RECEIVABLE",entries.get(0).getAccountCode());
        assertEquals("REVENUE_RENTAL",entries.get(1).getAccountCode());
    }

    @Test void duplicateProviderReferenceIsNotPostedAgain(){
        when(journals.existsByEventKey("PAYMENT_APPLIED:10:MPESA-1")).thenReturn(true);
        assertFalse(service.recordPaymentApplied(invoice(),"MPESA-1",new BigDecimal("500.00")));
        verify(journals,never()).save(any());
        verifyNoInteractions(lines);
    }

    @Test void overpaymentCreditPostsToLiability(){when(journals.save(any())).thenAnswer(invocation->{FinancialJournal journal=invocation.getArgument(0);journal.setId(32L);return journal;});assertTrue(service.recordUnappliedCredit(invoice(),"MPESA-2",new BigDecimal("25.00")));@SuppressWarnings("unchecked") ArgumentCaptor<List<FinancialLedgerLine>> captor=ArgumentCaptor.forClass(List.class);verify(lines).saveAll(captor.capture());assertEquals("CUSTOMER_CREDIT_LIABILITY",captor.getValue().get(1).getAccountCode());assertEquals(new BigDecimal("25.00"),captor.getValue().get(1).getCredit());}

    @Test void confirmedSettlementMovesClearingToBank(){when(journals.save(any())).thenAnswer(invocation->{FinancialJournal journal=invocation.getArgument(0);journal.setId(33L);return journal;});PaymentOperation operation=new PaymentOperation("settle-1","SET-1",7,10,"SETTLEMENT","CONFIRMED",new BigDecimal("900.00"),"KES","PAYSTACK","TR-1",null,java.time.ZonedDateTime.now(),8);assertTrue(service.recordPaymentOperation(invoice(),operation));@SuppressWarnings("unchecked") ArgumentCaptor<List<FinancialLedgerLine>> captor=ArgumentCaptor.forClass(List.class);verify(lines).saveAll(captor.capture());assertEquals("BANK_CASH",captor.getValue().get(0).getAccountCode());assertEquals("CASH_CLEARING",captor.getValue().get(1).getAccountCode());assertEquals(captor.getValue().get(0).getDebit(),captor.getValue().get(1).getCredit());}

    private PMSInvoice invoice(){PMSInvoice invoice=new PMSInvoice();invoice.setId(10L);invoice.setRef("INV-A");invoice.setAmount(1000);invoice.setCurrency("KES");invoice.setBillingType("RENTAL");invoice.setBilledUserId(2);invoice.setPayToUserId(3);invoice.setPropertyId(4);invoice.setUnitId(5);return invoice;}
}
