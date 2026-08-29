package org.pms.silverocean.service.payment;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.mustache.RenderService;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentReceiptService {
    private static final String TEMPLATE = "receipt";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm z");

    private final PaymentDao payments;
    private final InvoiceDao invoices;
    private final UserDao users;
    private final RenderService renderer;

    public void render(long paymentId, OutputStream output) {
        long userId = users.getUserId();
        PMSPayment payment = users.hasRole(PMSRole.SUPER_ADMIN)
                ? payments.findPaymentByID(paymentId).orElseThrow(this::invalid)
                : payments.findPaymentByIdForAuthorizedUser(paymentId, userId).orElseThrow(this::invalid);
        if (!payment.isCompletedSuccessfully() || StringUtils.isBlank(payment.getThirdPartyTransId())) {
            throw invalid();
        }
        PMSInvoice invoice = invoices.getInvoiceByRef(payment.getBillReference()).orElseThrow(this::invalid);
        Map<String, Object> model = new HashMap<>();
        model.put("receiptNumber", "RCP-" + payment.getId());
        model.put("invoiceRef", invoice.getRef());
        model.put("providerReference", payment.getThirdPartyTransId());
        model.put("channel", payment.getChannel());
        model.put("currency", StringUtils.defaultIfBlank(invoice.getCurrency(), ""));
        model.put("amount", BigDecimal.valueOf(payment.getAmount()).setScale(2, RoundingMode.HALF_UP));
        model.put("paidAt", payment.getCreatedOn() == null ? "" : DATE_TIME.format(payment.getCreatedOn()));
        model.put("payer", StringUtils.defaultIfBlank(payment.getCustomerName(), invoice.getCustomerEmail()));
        model.put("payee", users.findById(invoice.getPayToUserId()).map(u -> u.getFullName()).orElse("Property owner"));
        try {
            renderer.toPdf(renderer.render(TEMPLATE, model), output);
        } catch (IOException exception) {
            throw new PMSCustomException(ResponseCode.GENERAL_FAILURE, exception);
        }
    }

    private PMSCustomException invalid() {
        return new PMSCustomException(ResponseCode.GENERAL_FAILURE);
    }
}
