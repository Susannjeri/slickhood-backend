package org.pms.silverocean.service.payment.invoice;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.database.pms.entities.DomainEventOutbox;
import org.pms.silverocean.service.architecture.events.DomainEventHandler;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class InvoiceEmailHandler implements DomainEventHandler {
    public static final String TYPE = "INVOICE_EMAIL_REQUESTED";
    private final ObjectMapper mapper;
    private final InvoiceService invoices;
    @Override public String eventType() { return TYPE; }
    @Override public void handle(DomainEventOutbox event) throws Exception {
        invoices.sendInvoiceEmail(mapper.readValue(event.getPayload(), Request.class).invoiceId());
    }
    public record Request(long invoiceId) {}
}
