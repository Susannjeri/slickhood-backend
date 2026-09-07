package org.pms.silverocean.service.payment.invoice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.DomainEventOutbox;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvoiceEmailHandlerTest {
    @Test void persistedInvoiceIdIsUsedForDelivery() throws Exception {
        InvoiceService service=mock(InvoiceService.class);
        DomainEventOutbox event=new DomainEventOutbox();event.setPayload("{\"invoiceId\":42}");
        new InvoiceEmailHandler(new ObjectMapper(),service).handle(event);
        verify(service).sendInvoiceEmail(42);
    }
    @Test void deliveryFailurePropagatesToOutboxRetryInsteadOfBeingAcknowledged() {
        InvoiceService service=mock(InvoiceService.class);
        doThrow(new IllegalStateException("Delivery unavailable")).when(service).sendInvoiceEmail(42);
        DomainEventOutbox event=new DomainEventOutbox();event.setPayload("{\"invoiceId\":42}");
        assertThrows(IllegalStateException.class,()->new InvoiceEmailHandler(new ObjectMapper(),service).handle(event));
    }
}
