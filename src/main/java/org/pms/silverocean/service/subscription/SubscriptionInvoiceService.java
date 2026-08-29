package org.pms.silverocean.service.subscription;

import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@Service
public class SubscriptionInvoiceService {

    private final InvoiceDao invoiceDao;
    private final UserDao userDao;
    private final AccountDao accountDao;
    private final String defaultCurrency;

    public SubscriptionInvoiceService(
            InvoiceDao invoiceDao,
            UserDao userDao,
            AccountDao accountDao,
            @Value("${subscription.default.currency:KES}") String defaultCurrency
    ) {
        this.invoiceDao = invoiceDao;
        this.userDao = userDao;
        this.accountDao = accountDao;
        this.defaultCurrency = defaultCurrency;
    }

    /**
     * Persists a subscription checkout invoice.
     *
     * @param paymentAccountId verified platform payment account selected by the subscriber
     */
    @Transactional
    public SubscriptionPendingCheckoutDTO createSubscriptionCheckout(
            long billedUserId,
            SubscriptionPlan plan,
            PMSRole catalogRole,
            long paymentAccountId
    ) {
        PaymentAccount paymentAccount = accountDao.getAccountById(paymentAccountId);
        if (!paymentAccount.isActive() || !paymentAccount.isVerified()
                || paymentAccount.getCategory() != AccountCategory.SLICKHOOD) {
            throw new PMSCustomException(ResponseCode.SUBSCRIPTION_PAYMENT_PAYEE_NOT_CONFIGURED);
        }
        BigDecimal price = plan.getPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PMSCustomException(ResponseCode.GENERAL_FAILURE);
        }
        double amount = price.doubleValue();
        String currency = StringUtils.isNotBlank(plan.getCurrency())
                ? plan.getCurrency().trim()
                : defaultCurrency;
        String line = "Subscription plan: " + plan.getDisplayName() + " (" + plan.getCode() + ") — " + currency + " " + price;
        String htmlLine = "<tr><td><span>" + StringUtils.replaceEach(line, new String[]{"&", "<", ">"}, new String[]{"&amp;", "&lt;", "&gt;"}) + "</span></td></tr>";

        PMSInvoice invoice = new PMSInvoice();
        invoice.setUnitId(0L);
        invoice.setPropertyId(0L);
        invoice.setSubscriptionPlanCode(plan.getCode());
        invoice.setBillingType("SUBSCRIPTION");
        invoice.setDescription(line.getBytes(StandardCharsets.UTF_8));
        invoice.setHtmlDescription(htmlLine.getBytes(StandardCharsets.UTF_8));
        invoice.setAmount(amount);
        invoice.setPendingAmount(amount);
        invoice.setCurrency(currency);
        invoice.setBilledUserId(billedUserId);
        invoice.setPayToUserId(paymentAccount.getCreatedBy());
        invoice.setActive(true);
        invoice.setPaid(false);
        invoice.setTransactionInProgress(false);

        userDao.findById(billedUserId).ifPresent(user -> {
            invoice.setCustomerPhoneNumber(user.getPhoneNumber());
            invoice.setCustomerEmail(user.getEmail());
        });

        invoiceDao.createInvoice(invoice);
        return new SubscriptionPendingCheckoutDTO(
                invoice.getRef(),
                invoice.getCurrency(),
                invoice.getAmount(),
                plan.getCode(),
                catalogRole.name()
        );
    }
}
