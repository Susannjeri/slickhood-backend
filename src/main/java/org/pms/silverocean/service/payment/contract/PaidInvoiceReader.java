package org.pms.silverocean.service.payment.contract;
import java.util.Optional;
public interface PaidInvoiceReader {Optional<PaidInvoiceView> findByIdForUpdate(long invoiceId);}
