package org.pms.silverocean.service.subscription.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.database.pms.entities.DomainEventOutbox;
import org.pms.silverocean.service.architecture.events.DomainEventHandler;
import org.pms.silverocean.service.payment.contract.InvoicePaidEvent;
import org.pms.silverocean.service.subscription.SubscriptionPaymentCompletionService;
import org.pms.silverocean.service.soko.SokoService;
import org.pms.silverocean.service.affiliate.AffiliateService;
import org.pms.silverocean.service.sp.ServiceBookingService;
import org.pms.silverocean.service.communityfund.CommunityFundService;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class PaidInvoiceSubscriptionHandler implements DomainEventHandler {
    private final ObjectMapper objectMapper;private final SubscriptionPaymentCompletionService completionService;private final SokoService sokoService;private final AffiliateService affiliateService;private final ServiceBookingService bookingService;private final CommunityFundService communityFundService;
    @Override public String eventType(){return InvoicePaidEvent.TYPE;}
    @Override public void handle(DomainEventOutbox event)throws Exception{InvoicePaidEvent paid=objectMapper.readValue(event.getPayload(),InvoicePaidEvent.class);completionService.completePaidSubscriptionAfterPayment(paid.invoiceId(),paid.providerReference());sokoService.completePaidInvoice(paid.invoiceRef(),paid.providerReference());bookingService.completePaidInvoice(paid.invoiceRef(),paid.providerReference());affiliateService.recordPaidConversion(paid.invoiceId(),paid.providerReference());communityFundService.completePaidInvoice(paid.invoiceId(),paid.providerReference(),paid.paidAmount(),paid.currency(),paid.paidAt());}
}
