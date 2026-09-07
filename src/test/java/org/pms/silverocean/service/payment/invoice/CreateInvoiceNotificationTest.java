package org.pms.silverocean.service.payment.invoice;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.config.ConfigDTO;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.lease.LeaseDao;
import org.pms.silverocean.service.payment.invoice.wrappers.ProcessLeaseInvoiceDTO;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;

class CreateInvoiceNotificationTest {
    @Test void shrinkingDueQueueUsesKeysetAndPreservesContractDueDate() {
        LeaseDao leases=mock(LeaseDao.class);ConfigService config=mock(ConfigService.class);InvoiceService invoices=mock(InvoiceService.class);
        LocalDate due=LocalDate.of(2026,9,1);
        when(config.getConfigByName(PMSConfigs.LEASE_PAYMENT_RECORDS_PAGE_SIZE)).thenReturn(()->new ConfigDTO(1,"size","1",1,false));
        var first=new ProcessLeaseInvoiceDTO(4L,"RENT",due.minusMonths(1),due,1000d,"KES",false,7,8);
        var next=new ProcessLeaseInvoiceDTO(9L,"RENT",due.minusMonths(1),due,1000d,"KES",false,17,18);
        when(leases.getLeasePaymentsDueAfter(eq(0L),any())).thenReturn(new SliceImpl<>(List.of(first),PageRequest.of(0,1),true));
        when(leases.getLeasePaymentsDueAfter(eq(4L),any())).thenReturn(new SliceImpl<>(List.of(next),PageRequest.of(0,1),false));
        new CreateInvoiceService(leases,config,invoices).processPendingInvoices();
        verify(invoices).createPropertyInvoice(eq(7L),eq(8L),any(),eq("RENTAL"),eq(due));
        verify(invoices).createPropertyInvoice(eq(17L),eq(18L),any(),eq("RENTAL"),eq(due));
        verify(leases,never()).getLeasePaymentsDueToday(any());
    }
}
