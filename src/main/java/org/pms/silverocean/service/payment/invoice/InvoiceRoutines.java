package org.pms.silverocean.service.payment.invoice;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class InvoiceRoutines {
    private final CreateInvoiceService createInvoiceService;


    public InvoiceRoutines(CreateInvoiceService createInvoiceService) {
        this.createInvoiceService = createInvoiceService;
    }

    @Scheduled(cron = "${pms.invoice.cron:0 0 1 * * *}")
    public void runCreateInvoiceService() {
        createInvoiceService.processPendingInvoices();
    }
}
