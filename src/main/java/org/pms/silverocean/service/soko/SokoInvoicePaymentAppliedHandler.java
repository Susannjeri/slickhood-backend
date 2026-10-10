package org.pms.silverocean.service.soko;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.database.pms.entities.DomainEventOutbox;
import org.pms.silverocean.service.architecture.events.DomainEventHandler;
import org.pms.silverocean.service.payment.contract.InvoicePaymentAppliedEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SokoInvoicePaymentAppliedHandler implements DomainEventHandler {
    private final ObjectMapper objectMapper;
    private final SokoService sokoService;

    @Override
    public String eventType() {
        return InvoicePaymentAppliedEvent.TYPE;
    }

    @Override
    public void handle(DomainEventOutbox event) throws Exception {
        InvoicePaymentAppliedEvent payment = objectMapper.readValue(event.getPayload(), InvoicePaymentAppliedEvent.class);
        sokoService.applyInvoicePayment(payment.invoiceRef(), payment.providerReference());
    }
}
