package org.pms.silverocean.service.payment.invoice;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.database.pms.PMSInvoiceRepo;
import org.pms.silverocean.service.payment.contract.*;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component @RequiredArgsConstructor
public class JpaPaidInvoiceReader implements PaidInvoiceReader {
    private final PMSInvoiceRepo repo;
    @Override public Optional<PaidInvoiceView> findByIdForUpdate(long id){return repo.findByIdForUpdate(id).map(i->new PaidInvoiceView(i.getId(),i.getRef(),i.getSubscriptionPlanCode(),i.isPaid(),i.getPendingAmount(),i.getBilledUserId()));}
}
