package org.pms.silverocean.service.soko;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.DomainEventOutbox;
import org.pms.silverocean.service.payment.contract.InvoicePaymentAppliedEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SokoInvoicePaymentAppliedHandlerTest {
    @Test void everyDurableAppliedPaymentReconcilesTheSokoOrderFromItsInvoice() throws Exception {
        ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
        SokoService service=mock(SokoService.class);
        SokoInvoicePaymentAppliedHandler handler=new SokoInvoicePaymentAppliedHandler(mapper,service);
        var payment=new InvoicePaymentAppliedEvent(9L,"INV-9","PAY-1",new BigDecimal("25"),
                new BigDecimal("25"),new BigDecimal("75"),"KES",LocalDateTime.now());
        DomainEventOutbox event=new DomainEventOutbox();event.setPayload(mapper.writeValueAsString(payment));

        handler.handle(event);

        assertEquals(InvoicePaymentAppliedEvent.TYPE,handler.eventType());
        verify(service).applyInvoicePayment("INV-9","PAY-1");
    }
}
