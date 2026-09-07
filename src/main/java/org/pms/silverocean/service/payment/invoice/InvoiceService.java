package org.pms.silverocean.service.payment.invoice;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.account.dto.AccountSummaryDTO;
import org.pms.silverocean.service.mustache.RenderService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.notification.email.EmailService;
import org.pms.silverocean.service.payment.PaymentPlatform;
import org.pms.silverocean.service.payment.PaymentPlatformFactory;
import org.pms.silverocean.service.payment.PaymentRequestException;
import org.pms.silverocean.service.payment.invoice.wrappers.InvoiceDTO;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentChannelDTO;
import org.pms.silverocean.service.payment.wrappers.PaymentResponse;
import org.pms.silverocean.service.property.UnitDao;
import org.pms.silverocean.service.property.wrappers.PropertyNameAddressAndTypeProjection;
import org.pms.silverocean.service.property.wrappers.TenantNameEmailPhoneAndUnitRefProjection;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;

@Service
@Slf4j
public class InvoiceService {
    private final InvoiceDao invoiceDao;
    private final UnitDao unitDao;
    private final UserDao userDao;
    private final AccountDao accountDao;
    private final RenderService renderService;
    private final EmailService emailService;
    private final I18NService i18NService;
    private final PaymentPlatformFactory paymentPlatformFactory;
    private final org.pms.silverocean.service.architecture.events.DomainEventOutboxPublisher notificationEvents;
    private static final String INVOICE_TEMPLATE = "invoice";

    public InvoiceService(InvoiceDao invoiceDao, UnitDao unitDao, UserDao userDao, AccountDao accountDao,
                          RenderService renderService, @Qualifier("EMAIL") EmailService emailService,
                          I18NService i18NService,
                          PaymentPlatformFactory paymentPlatformFactory,
                          org.pms.silverocean.service.architecture.events.DomainEventOutboxPublisher notificationEvents) {
        this.invoiceDao = invoiceDao;
        this.unitDao = unitDao;
        this.userDao = userDao;
        this.accountDao = accountDao;
        this.renderService = renderService;
        this.emailService = emailService;
        this.i18NService = i18NService;
        this.paymentPlatformFactory = paymentPlatformFactory;
        this.notificationEvents = notificationEvents;
    }

    @org.springframework.transaction.annotation.Transactional("pmsDBTransactionManager")
    public void createInvoice(long unitId, long tenantUserId, Map<String, Double> invoiceAmounts) {
        createPropertyInvoice(unitId, tenantUserId, invoiceAmounts, "RENTAL", LocalDate.now(PMSUtils.getZoneId()));
    }

    @org.springframework.transaction.annotation.Transactional("pmsDBTransactionManager")
    public PMSInvoice createPropertyInvoice(long unitId, long billedUserId, Map<String, Double> invoiceAmounts,
                                            String billingType, LocalDate dueDate) {
        return createScopedInvoice(unitId, billedUserId, invoiceAmounts, billingType, dueDate, null, null);
    }

    @org.springframework.transaction.annotation.Transactional("pmsDBTransactionManager")
    public PMSInvoice createFundInvoice(long unitId, long billedUserId, long custodianUserId, long paymentAccountId,
                                        Map<String, Double> invoiceAmounts, LocalDate dueDate) {
        return createScopedInvoice(unitId, billedUserId, invoiceAmounts, "COMMUNITY_FUND", dueDate, custodianUserId, paymentAccountId);
    }

    @org.springframework.transaction.annotation.Transactional("pmsDBTransactionManager")
    public PMSInvoice createFundInvoice(long unitId, long billedUserId, long custodianUserId, long paymentAccountId,
                                        Map<String, Double> invoiceAmounts, LocalDate dueDate, String fundCurrency) {
        java.util.Currency.getInstance(fundCurrency);
        return createScopedInvoice(unitId, billedUserId, invoiceAmounts, "COMMUNITY_FUND", dueDate,
                custodianUserId, paymentAccountId, fundCurrency);
    }

    @org.springframework.transaction.annotation.Transactional("pmsDBTransactionManager")
    public PMSInvoice createSaleInvoice(long unitId, long billedUserId, long salesRecipientUserId,
                                        long paymentAccountId, Map<String, Double> invoiceAmounts,
                                        LocalDate dueDate) {
        PaymentAccount account = accountDao.getAccountById(paymentAccountId);
        if (!account.isActive() || !account.isVerified()
                || account.getCreatedBy() != salesRecipientUserId
                || account.getCategory() != AccountCategory.PROPERTY_SALES) {
            throw new PMSCustomException(ResponseCode.ACCOUNT_UNAUTHORIZED);
        }
        return createScopedInvoice(unitId, billedUserId, invoiceAmounts, "SALE", dueDate,
                salesRecipientUserId, paymentAccountId);
    }

    private PMSInvoice createScopedInvoice(long unitId, long billedUserId, Map<String, Double> invoiceAmounts,
                                           String billingType, LocalDate dueDate, Long payToUserId, Long paymentAccountId) {
        return createScopedInvoice(unitId, billedUserId, invoiceAmounts, billingType, dueDate, payToUserId, paymentAccountId, null);
    }

    private PMSInvoice createScopedInvoice(long unitId, long billedUserId, Map<String, Double> invoiceAmounts,
                                           String billingType, LocalDate dueDate, Long payToUserId, Long paymentAccountId,
                                           String currencyOverride) {
        Unit unit = unitDao.findById(unitId).orElseThrow();

        double totalAmount = 0.0;

        StringBuilder descriptionBuilder = new StringBuilder();
        StringBuilder htmlDescriptionBuilder = new StringBuilder();
        String currency = currencyOverride == null ? unit.getCurrency() : currencyOverride;

        for (Map.Entry<String, Double> entry : invoiceAmounts.entrySet()) {
            String formattedAmount = String.format("%,.2f", entry.getValue());
            String line = String.format("%s: %s %,.2f", entry.getKey(), currency, entry.getValue());
            descriptionBuilder.append(line).append("\n");
            totalAmount += entry.getValue();
            htmlDescriptionBuilder.append("<tr>")
                    .append("<td>")
                    .append("<span>").append(org.springframework.web.util.HtmlUtils.htmlEscape(entry.getKey())).append("</span>")
                    .append("</td>")
                    .append("<td class='amount-col'>")
                    .append(formattedAmount)
                    .append("</td>")
                    .append("</tr>");
        }

        String finalDescription = descriptionBuilder.toString().trim();
        String finalHtmlDescription = htmlDescriptionBuilder.toString().trim();

        PMSInvoice pmsInvoice = new PMSInvoice();
        pmsInvoice.setUnitId(unitId);
        pmsInvoice.setDescription(finalDescription.getBytes());
        pmsInvoice.setHtmlDescription(finalHtmlDescription.getBytes());
        pmsInvoice.setAmount(totalAmount);
        pmsInvoice.setBilledUserId(billedUserId);
        pmsInvoice.setCurrency(currency);
        pmsInvoice.setPropertyId(unit.getPropertyId());
        pmsInvoice.setPayToUserId(payToUserId == null
                ? unitDao.findPropertyOwnerId(unitId).orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND))
                : payToUserId);
        pmsInvoice.setPaymentAccountId(paymentAccountId);
        pmsInvoice.setActive(true);
        pmsInvoice.setPendingAmount(totalAmount);
        pmsInvoice.setBillingType(billingType);
        pmsInvoice.setDueDate(dueDate);

        userDao.findById(billedUserId).ifPresent(user -> {
            pmsInvoice.setCustomerPhoneNumber(user.getPhoneNumber());
            pmsInvoice.setCustomerEmail(user.getEmail());
        });

        invoiceDao.createInvoice(pmsInvoice);
        createInvoicePDFAndSendEmail(pmsInvoice, true, null);
        return pmsInvoice;
    }

    public void viewInvoicePDF(long invoiceId, OutputStream outputStream) {
        Long userId = userDao.getUserId();

        PMSInvoice pmsInvoice = userDao.hasRole(PMSRole.SUPER_ADMIN) ?
                invoiceDao.getInvoiceById(invoiceId).orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_INVOICE_NUMBER))
                : invoiceDao.getInvoiceForOwnerOrTenantView(invoiceId, userId).orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_INVOICE_NUMBER));
        createInvoicePDFAndSendEmail(pmsInvoice, false, outputStream);
    }

    private Map<String, Object> compilePDFData(PMSInvoice invoice) {
        Map<String, Object> invoiceData = new HashMap<>();
        invoiceData.put("invoiceRef", invoice.getRef());
        invoiceData.put("billingType", resolveBillingType(invoice));
        invoiceData.put("issuerName", resolveIssuerName(invoice));
        invoiceData.put("issuerType", resolveIssuerType(invoice));

        if (StringUtils.isNotBlank(invoice.getSubscriptionPlanCode())) {
            Users billed = userDao.findById(invoice.getBilledUserId()).orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_INVOICE_NUMBER));
            invoiceData.put("propertyName", "Subscription");
            invoiceData.put("propertyAddress", "");
            invoiceData.put("unitRef", invoice.getSubscriptionPlanCode());
            invoiceData.put("customerName", billed.getFullName());
            invoiceData.put("customerEmail", billed.getEmail());
            invoiceData.put("customerPhone", billed.getPhoneNumber());
        } else if ("SOKO".equals(invoice.getBillingType()) || "SERVICE_MARKETPLACE".equals(invoice.getBillingType())) {
            Users billed = userDao.findById(invoice.getBilledUserId()).orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_INVOICE_NUMBER));
            boolean serviceMarketplace = "SERVICE_MARKETPLACE".equals(invoice.getBillingType());
            invoiceData.put("propertyName", serviceMarketplace ? "Slickhood Services" : "Slickhood Soko");
            invoiceData.put("propertyAddress", serviceMarketplace ? "Service marketplace booking" : "Marketplace order");
            invoiceData.put("unitRef", invoice.getRef());
            invoiceData.put("customerName", billed.getFullName());
            invoiceData.put("customerEmail", billed.getEmail());
            invoiceData.put("customerPhone", billed.getPhoneNumber());
        } else if ("SERVICE_CHARGE".equals(invoice.getBillingType()) || "SALE".equals(invoice.getBillingType()) || "COMMUNITY_FUND".equals(invoice.getBillingType())) {
            PropertyNameAddressAndTypeProjection property = unitDao.getPropertyDetailsFromUnitId(invoice.getUnitId()).orElseThrow();
            Unit unit = unitDao.findById(invoice.getUnitId()).orElseThrow();
            Users billed = userDao.findById(invoice.getBilledUserId()).orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_INVOICE_NUMBER));
            invoiceData.put("propertyName", property.getName());
            invoiceData.put("propertyAddress", property.getAddress());
            invoiceData.put("unitRef", unit.getRef());
            invoiceData.put("customerName", billed.getFullName());
            invoiceData.put("customerEmail", billed.getEmail());
            invoiceData.put("customerPhone", billed.getPhoneNumber());
        } else {
            PropertyNameAddressAndTypeProjection propertyDetailsFromUnitId = unitDao.getPropertyDetailsFromUnitId(invoice.getUnitId()).orElseThrow();
            TenantNameEmailPhoneAndUnitRefProjection tenantNameEmailPhoneAndUnitRefProjection = unitDao.getTenantAndUnitDetailsByUnitId(invoice.getUnitId(), invoice.getBilledUserId()).orElseThrow();
            invoiceData.put("propertyName", propertyDetailsFromUnitId.getName());
            invoiceData.put("propertyAddress", propertyDetailsFromUnitId.getAddress());
            invoiceData.put("unitRef", tenantNameEmailPhoneAndUnitRefProjection.getUnitRef());
            invoiceData.put("customerName", tenantNameEmailPhoneAndUnitRefProjection.getTenantName());
            invoiceData.put("customerEmail", tenantNameEmailPhoneAndUnitRefProjection.getTenantEmail());
            invoiceData.put("customerPhone", tenantNameEmailPhoneAndUnitRefProjection.getTenantPhone());
        }

        invoiceData.put("dateIssued", PMSUtils.toFormattedDay(invoice.getCreatedOn()));
        invoiceData.put("dueDate", invoice.getDueDate() == null ? PMSUtils.toFormattedDay(invoice.getCreatedOn()) : invoice.getDueDate().toString());

        invoiceData.put("currency", invoice.getCurrency());
        invoiceData.put("descriptionHtml", new String(invoice.getHtmlDescription()));
        invoiceData.put("totalAmount", invoice.getAmount());
        invoiceData.put("pendingAmount", invoice.getPendingAmount());

        invoiceData.put("isPaid", invoice.isPaid());
        return invoiceData;
    }

    private void createInvoicePDFAndSendEmail(PMSInvoice invoice, boolean sendEmail, OutputStream outputStream) {
        if (sendEmail) {
            notificationEvents.publish(InvoiceEmailHandler.TYPE, "INVOICE", invoice.getId().toString(),
                    InvoiceEmailHandler.TYPE + ":" + invoice.getId(), new InvoiceEmailHandler.Request(invoice.getId()));
        } else if (outputStream != null) {
            String renderedInvoice = renderService.render(INVOICE_TEMPLATE, compilePDFData(invoice));
            try {
                renderService.toPdf(renderedInvoice, outputStream);
            } catch (IOException e) {
                throw new PMSCustomException(ResponseCode.GENERAL_FAILURE, e);
            }
        } else {
            throw new PMSCustomException(ResponseCode.GENERAL_FAILURE);
        }

    }

    /** Called by the durable outbox after invoice commit. Failures remain retryable, not swallowed. */
    public void sendInvoiceEmail(long invoiceId) {
        PMSInvoice invoice = invoiceDao.getInvoiceById(invoiceId).filter(PMSInvoice::isActive).orElse(null);
        if (invoice == null) return;
        try {
            Map<String, Object> invoiceData = compilePDFData(invoice);
            var bytes = new java.io.ByteArrayOutputStream();
            renderService.toPdf(renderService.render(INVOICE_TEMPLATE, invoiceData), bytes);
            String name = org.springframework.web.util.HtmlUtils.htmlEscape(String.valueOf(invoiceData.get("customerName")));
            String message = String.format(i18NService.getLocalizedMessage(NotificationType.INVOICE_EMAIL.getBody()), name, invoiceData.get("dateIssued"));
            emailService.sendAttachment(String.valueOf(invoiceData.get("customerEmail")), message,
                    i18NService.getLocalizedMessage(NotificationType.INVOICE_EMAIL.getSubject()), bytes.toByteArray(),
                    "Invoice_" + invoiceId + ".pdf");
        } catch (Exception failure) {
            // The provider exception may include personal data. Persist only a safe error classification.
            throw new IllegalStateException("Invoice email delivery failed (" + failure.getClass().getSimpleName() + ")");
        }
    }

    public Page<InvoiceDTO> getInvoiceList(Pageable pageable, Long tenantId, Long landlordId, Long propertyId, Long unitId) {
        Long userId = userDao.getUserId();
        if (userDao.hasRole(PMSRole.SUPER_ADMIN)) {
            return invoiceDao.getInvoicesForSuperAdminView(pageable, tenantId, propertyId, unitId, landlordId).map(this::mapInvoiceEntityToDTO);
        } else {
            return invoiceDao.getInvoicesForOwnerAndTenantView(pageable, userId, propertyId, unitId).map(this::mapInvoiceEntityToDTO);
        }
    }

    private InvoiceDTO mapInvoiceEntityToDTO(PMSInvoice invoice) {
        if (StringUtils.isNotBlank(invoice.getSubscriptionPlanCode())) {
            Users billed = userDao.findById(invoice.getBilledUserId()).orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_INVOICE_NUMBER));
            return buildInvoiceDTO(invoice, "Subscription plan: " + invoice.getSubscriptionPlanCode(),
                    null, "Subscription", invoice.getSubscriptionPlanCode(), billed.getFullName());
        }
        if ("SOKO".equals(invoice.getBillingType()) || "SERVICE_MARKETPLACE".equals(invoice.getBillingType())) {
            Users billed = userDao.findById(invoice.getBilledUserId()).orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_INVOICE_NUMBER));
            String label = "SERVICE_MARKETPLACE".equals(invoice.getBillingType()) ? "Slickhood service booking" : "Slickhood Soko order";
            return buildInvoiceDTO(invoice, label, null,
                    "SERVICE_MARKETPLACE".equals(invoice.getBillingType()) ? "SlickHood Services" : "SlickHood Soko",
                    invoice.getRef(), billed.getFullName());
        }
        PropertyNameAddressAndTypeProjection propertyDetails = unitDao.getPropertyDetailsFromUnitId(invoice.getUnitId()).orElseThrow();
        if ("SERVICE_CHARGE".equals(invoice.getBillingType()) || "SALE".equals(invoice.getBillingType()) || "COMMUNITY_FUND".equals(invoice.getBillingType())) {
            Unit unit = unitDao.findById(invoice.getUnitId()).orElseThrow();
            Users billed = userDao.findById(invoice.getBilledUserId()).orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_INVOICE_NUMBER));
            return buildInvoiceDTO(invoice, String.format("%s: %s - Unit: %s",
                    invoice.getBillingType().replace('_', ' '), propertyDetails.getName(), unit.getRef()),
                    invoice.getPropertyId(), propertyDetails.getName(), unit.getRef(), billed.getFullName());
        }
        TenantNameEmailPhoneAndUnitRefProjection tenantAndUnit = unitDao.getTenantAndUnitDetailsByUnitId(invoice.getUnitId(), invoice.getBilledUserId()).orElseThrow();

        return buildInvoiceDTO(invoice, String.format("Property: %s - Unit: %s",
                propertyDetails.getName(), tenantAndUnit.getUnitRef()), invoice.getPropertyId(),
                propertyDetails.getName(), tenantAndUnit.getUnitRef(), tenantAndUnit.getTenantName());
    }

    private InvoiceDTO buildInvoiceDTO(PMSInvoice invoice, String propertyDetails, Long propertyId,
                                       String propertyName, String unitRef, String tenantName) {
        return new InvoiceDTO(invoice.getId(), invoice.getCreatedOn(), propertyDetails, propertyId,
                propertyName, unitRef, tenantName, invoice.getRef(), invoice.getCurrency(), invoice.getAmount(),
                invoice.getPendingAmount(), invoice.isPaid(), invoice.getPaymentAccountId(),
                resolveBillingType(invoice), invoice.getDueDate(), resolveIssuerName(invoice),
                resolveIssuerType(invoice), isSlickHoodInvoice(invoice) ? "/slicklogo.svg" : null,
                !invoice.isPaid() && invoice.isActive() && invoice.getBilledUserId() == userDao.getUserId(),
                !invoice.isPaid() && invoice.isActive()
                        && userDao.hasPermission(org.pms.silverocean.service.auth.roles.enums.Permission.RECORD_MANUAL_PAYMENT)
                        && invoice.getBilledUserId() != userDao.getUserId());
    }

    private String resolveBillingType(PMSInvoice invoice) {
        if (StringUtils.isNotBlank(invoice.getSubscriptionPlanCode())) return "SUBSCRIPTION";
        return StringUtils.defaultIfBlank(invoice.getBillingType(), "RENTAL");
    }

    private String resolveIssuerType(PMSInvoice invoice) {
        return switch (resolveBillingType(invoice)) {
            case "SERVICE_CHARGE", "COMMUNITY_FUND" -> "ESTATE_MANAGEMENT";
            case "SALE" -> "PROPERTY_SALE_MANAGEMENT";
            case "SOKO" -> "SOKO";
            case "SERVICE_MARKETPLACE" -> "SERVICES";
            case "SUBSCRIPTION" -> "SLICKHOOD";
            default -> "LANDLORD";
        };
    }

    private boolean isSlickHoodInvoice(PMSInvoice invoice) {
        return Set.of("SLICKHOOD", "SOKO", "SERVICES").contains(resolveIssuerType(invoice));
    }

    private String resolveIssuerName(PMSInvoice invoice) {
        if (isSlickHoodInvoice(invoice)) {
            return switch (resolveIssuerType(invoice)) {
                case "SOKO" -> "SlickHood Soko";
                case "SERVICES" -> "SlickHood Services";
                default -> "SlickHood";
            };
        }
        return userDao.findById(invoice.getPayToUserId())
                .map(user -> StringUtils.defaultIfBlank(user.getOrganizationName(), user.getFullName()))
                .orElse("SlickHood payment partner");
    }

    public AccountSummaryDTO getInvoicePaymentAccount(long invoiceId){
        long userId=userDao.getUserId();
        PMSInvoice invoice=userDao.hasRole(PMSRole.SUPER_ADMIN)?invoiceDao.getInvoiceById(invoiceId).orElseThrow(()->new PMSCustomException(ResponseCode.INVALID_INVOICE_NUMBER)):
                invoiceDao.getInvoiceForOwnerOrTenantView(invoiceId,userId).orElseThrow(()->new PMSCustomException(ResponseCode.INVALID_INVOICE_NUMBER));
        if(invoice.getPaymentAccountId()==null)throw new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND);
        PaymentAccount account=accountDao.getAccountById(invoice.getPaymentAccountId());
        return new AccountSummaryDTO(account,paymentPlatformFactory.getChannelImage(account.getChannel()));
    }

    public PaymentResponse initInvoicePayment(String invoiceRef, PaymentChannel paymentChannel, String phoneNumber, long accountId) {
        if (paymentChannel == PaymentChannel.FLUTTER_WAVE) {
            throw new PaymentRequestException(ResponseCode.PAYMENT_INITIALIZATION_FAILED);
        }
        long userId = userDao.getUserId();
        PMSInvoice invoice = userDao.hasRole(PMSRole.SUPER_ADMIN)
                ? invoiceDao.getInvoiceByRef(invoiceRef).orElseThrow(() -> new PaymentRequestException(ResponseCode.INVALID_INVOICE_NUMBER))
                : invoiceDao.getInvoiceForOwnerOrTenantView(invoiceRef, userId)
                .orElseThrow(() -> new PaymentRequestException(ResponseCode.INVALID_INVOICE_NUMBER));
        // Viewing an invoice as its issuer, manager or administrator must never
        // imply authority to initiate a charge on behalf of the billed customer.
        if (invoice.getBilledUserId() != userId) {
            throw new PaymentRequestException(ResponseCode.INVALID_INVOICE_NUMBER);
        }
        if (invoice.isPaid()) {
            return new PaymentResponse(false, ResponseCode.INVOICE_ALREADY_PAID);
        } else if (!invoice.isActive()) {
            return new PaymentResponse(false, ResponseCode.INVALID_INVOICE_NUMBER);
        } else if (invoice.isTransactionInProgress()) {
            return new PaymentResponse(false, ResponseCode.TRANSACTION_IN_PROGRESS);
        }
        validateSubscriptionPaymentAccount(invoice, paymentChannel, accountId);
        PaymentPlatform platform = paymentPlatformFactory.getPlatform(paymentChannel);
        return platform.processPayment(invoice, phoneNumber, accountId);
    }

    private void validateSubscriptionPaymentAccount(PMSInvoice invoice, PaymentChannel paymentChannel, long accountId) {
        if (StringUtils.isBlank(invoice.getSubscriptionPlanCode())) {
            PaymentAccount account = accountDao.getAccountById(accountId);
            AccountCategory expectedCategory = expectedAccountCategory(invoice);
            if (!account.isActive() || !account.isVerified() || account.getCreatedBy() != invoice.getPayToUserId()
                    || account.getChannel() != paymentChannel || account.getCategory() == AccountCategory.SLICKHOOD
                    || expectedCategory != null && account.getCategory() != expectedCategory
                    || invoice.getPaymentAccountId()!=null&&!invoice.getPaymentAccountId().equals(accountId)) {
                throw new PaymentRequestException(ResponseCode.ACCOUNT_UNAUTHORIZED);
            }
            return;
        }
        if (invoice.getBilledUserId() != userDao.getUserId()) {
            throw new PaymentRequestException(ResponseCode.INVALID_INVOICE_NUMBER);
        }

        PaymentAccount account = accountDao.getAccountById(accountId);
        boolean validPlatformAccount = account.isActive()
                && account.isVerified()
                && account.getCategory() == AccountCategory.SLICKHOOD
                && account.getCreatedBy() == invoice.getPayToUserId()
                && account.getChannel() == paymentChannel;
        if (!validPlatformAccount) {
            throw new PaymentRequestException(ResponseCode.PAYMENT_INITIALIZATION_FAILED);
        }
    }

    private AccountCategory expectedAccountCategory(PMSInvoice invoice) {
        if (invoice.getSubscriptionPlanCode() != null) return AccountCategory.SLICKHOOD;
        return switch (StringUtils.defaultString(invoice.getBillingType())) {
            case "RENTAL" -> AccountCategory.LANDLORD;
            case "SERVICE_CHARGE" -> AccountCategory.ESTATE_MANAGEMENT;
            case "SALE" -> AccountCategory.PROPERTY_SALES;
            case "COMMUNITY_FUND" -> AccountCategory.COMMUNITY_FUND;
            case "SOKO", "SERVICE_MARKETPLACE" -> AccountCategory.MERCHANT;
            default -> null;
        };
    }

    public Set<PaymentChannelDTO> getPaymentTypes() {
        return paymentPlatformFactory.getPaymentTypes();
    }
}
