package org.pms.silverocean.service.payment.platforms.pesawise;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.RestRequestException;
import org.pms.silverocean.service.RestTemplateService;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.eventlogger.EventService;
import org.pms.silverocean.service.param.ParamService;
import org.pms.silverocean.service.payment.PaymentCallBackRequest;
import org.pms.silverocean.service.payment.PaymentCallBackResponse;
import org.pms.silverocean.service.payment.PaymentDao;
import org.pms.silverocean.service.payment.PaymentPlatform;
import org.pms.silverocean.service.payment.PaymentRequestException;
import org.pms.silverocean.service.payment.UpdatePaymentService;
import org.pms.silverocean.service.payment.WebhookSignatureVerifier;
import org.pms.silverocean.service.payment.platforms.mpesa.TransactionCategory;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentPropertyKeys;
import org.pms.silverocean.service.payment.wrappers.PaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

@Service("PesaWise")
@Slf4j
public class PesawisePlatform extends PaymentPlatform {
    private static final String STK_PATH = "/api/payments/stk-push";
    private static final String STATUS_PATH = "/api/payments/payment-status";

    private final PaymentDao paymentDao;
    private final AccountDao accountDao;
    private final ParamService paramService;
    private final RestTemplateService restTemplateService;
    private final EventService eventService;
    private final ObjectMapper objectMapper;

    @Value("${payment.pesawise.enabled:false}")
    private boolean enabled;
    @Value("${payment.pesawise.api-url:https://api.pesawise.xyz}")
    private String apiUrl;
    @Value("${payment.pesawise.icon:mpesa_icon.png}")
    private String icon;

    public PesawisePlatform(UpdatePaymentService updatePaymentService, PaymentDao paymentDao,
                            AccountDao accountDao, ParamService paramService,
                            RestTemplateService restTemplateService, EventService eventService,
                            ObjectMapper objectMapper) {
        super(updatePaymentService);
        this.paymentDao = paymentDao;
        this.accountDao = accountDao;
        this.paramService = paramService;
        this.restTemplateService = restTemplateService;
        this.eventService = eventService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected PaymentResponse initPayment(PMSInvoice invoice, long accountId) throws PaymentRequestException {
        return initPayment(invoice, invoice.getCustomerPhoneNumber(), accountId);
    }

    @Override
    protected PaymentResponse initPayment(PMSInvoice invoice, String phoneNumber, long accountId)
            throws PaymentRequestException {
        PaymentAccount account = requireAccount(invoice, accountId);
        String phone = normalizePhone(phoneNumber);
        long balanceId = balanceId(accountId, invoice.getPropertyId());
        HttpHeaders headers = credentials(accountId, invoice.getPropertyId());

        PMSPayment payment = new PMSPayment(invoice, "PesaWise customer", accountId);
        payment.setChannel(PaymentChannel.PESAWISE.getName());
        payment.setCategory(TransactionCategory.CARD_PAYMENT.name());
        payment.setReceivingAccountNumber(String.valueOf(balanceId));
        paymentDao.savePMSPayment(payment);

        try {
            PesawisePaymentResponse response = restTemplateService.sendPostRequest(
                    baseUrl() + STK_PATH,
                    new PesawiseStkRequest(balanceId, money(invoice.getPendingAmount()).doubleValue(), phone, invoice.getRef()),
                    headers, PesawisePaymentResponse.class);
            eventService.saveEvent(response, payment.getId());
            if (response == null || !response.success() || StringUtils.isBlank(response.paymentId())
                    || !StringUtils.equals(invoice.getRef(), response.reference())) {
                throw new PaymentRequestException(ResponseCode.PAYMENT_INITIALIZATION_FAILED);
            }
            payment.setThirdPartyTransId(response.paymentId());
            payment.setStatus("initialized");
            payment.setStatusDesc(response.detail());
            paymentDao.savePMSPayment(payment);
            return new PaymentResponse(true, ResponseCode.MPESA_PAYMENT_INITIALIZED,
                    "Approve the PesaWise M-Pesa request sent to " + maskedPhone(phone) + ".");
        } catch (RestRequestException exception) {
            payment.setStatus(String.valueOf(exception.getHttpStatusCode()));
            payment.setStatusDesc(exception.getMessage());
            payment.setInProgress(false);
            paymentDao.savePMSPayment(payment);
            throw new PaymentRequestException(ResponseCode.PAYMENT_INITIALIZATION_FAILED, exception);
        }
    }

    @Override
    protected boolean isActive() {
        return enabled && baseUrl().startsWith("https://");
    }

    @Override
    protected PaymentChannel channelType() {
        return PaymentChannel.PESAWISE;
    }

    @Override
    protected String channelIcon() {
        return icon;
    }

    @Override
    @Transactional("pmsDBTransactionManager")
    public PaymentCallBackResponse handleCallBack(PaymentCallBackRequest request) {
        if (!(request instanceof PesawiseCallbackDTO callback)) {
            throw new PMSCustomException(ResponseCode.GENERAL_FAILURE);
        }
        try {
            PesawiseWebhook body = objectMapper.readValue(callback.rawBody(), PesawiseWebhook.class);
            if (body == null || StringUtils.isBlank(body.reference())) {
                throw new PMSCustomException(ResponseCode.GENERAL_FAILURE);
            }
            PMSPayment payment = paymentDao.findPaymentByThirdPartyID(body.requestId())
                    .or(() -> paymentDao.findLatestInProgressPayment(body.reference(), PaymentChannel.PESAWISE))
                    .orElseThrow(() -> new PMSCustomException(ResponseCode.GENERAL_FAILURE));
            if (!payment.isInProgress()) return new PesawiseCallbackResponse("Event acknowledged");

            PMSInvoice invoice = updatePaymentService.getInvoicePayToIDUsingInvoiceRef(payment.getBillReference())
                    .orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_INVOICE_NUMBER));
            PaymentAccount account = requireAccount(invoice, payment.getAccountId());
            String expectedSecret = property(payment.getAccountId(), PaymentPropertyKeys.PESAWISE_WEBHOOK_SECRET,
                    invoice.getPropertyId());
            if (!WebhookSignatureVerifier.isSharedTokenValid(callback.secretHash(), expectedSecret)
                    || !"merchant.stk-push.update".equalsIgnoreCase(StringUtils.trim(callback.eventType()))) {
                throw new PMSCustomException(ResponseCode.GENERAL_FAILURE);
            }

            PesawisePaymentStatus status = verify(payment, invoice);
            boolean successful = "SUCCESS".equalsIgnoreCase(status.paymentStatus());
            if (!successful) {
                if ("FAILED".equalsIgnoreCase(status.paymentStatus())) {
                    payment.setInProgress(false);
                    payment.setStatus("failed");
                    payment.setStatusDesc(status.description());
                    updatePaymentService.setInvoiceTransactionStatusByBillRefNumber(invoice.getRef(), false);
                    paymentDao.savePMSPayment(payment);
                }
                return new PesawiseCallbackResponse("Event acknowledged");
            }

            String receipt = StringUtils.defaultIfBlank(status.uniqueReference(), status.paymentId());
            boolean valid = StringUtils.equals(payment.getThirdPartyTransId(), status.paymentId())
                    && StringUtils.equals(invoice.getRef(), status.reference())
                    && money(payment.getAmount()).compareTo(money(status.amount())) == 0
                    && StringUtils.equalsIgnoreCase(invoice.getCurrency(), status.currency())
                    && StringUtils.equals(payment.getReceivingAccountNumber(), status.globalAccountId())
                    && !paymentDao.providerReceiptAlreadyProcessed(PaymentChannel.PESAWISE.getName(), receipt, payment.getId());
            if (!valid) throw new PMSCustomException(ResponseCode.GENERAL_FAILURE);

            payment.setProviderReceipt(receipt);
            payment.setStatus(TransactionCategory.CARD_PAYMENT.getSuccessString());
            payment.setStatusDesc(status.description());
            payment.setSourceIp(callback.sourceIp());
            payment.setInProgress(false);
            paymentDao.savePMSPayment(payment);
            updatePaymentService.setInvoiceTransactionStatusByBillRefNumber(invoice.getRef(), false);
            updatePaymentService.setInvoiceToPaid(invoice, receipt, status.amount());
            return new PesawiseCallbackResponse("Payment verified");
        } catch (PMSCustomException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("PesaWise callback could not be verified: {}", exception.getClass().getSimpleName());
            throw new PMSCustomException(ResponseCode.GENERAL_FAILURE);
        }
    }

    private PesawisePaymentStatus verify(PMSPayment payment, PMSInvoice invoice) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl() + STATUS_PATH)
                .queryParam("paymentId", payment.getThirdPartyTransId()).build().encode().toUriString();
        PesawisePaymentStatus response = restTemplateService.sendGetRequest(
                url, credentials(payment.getAccountId(), invoice.getPropertyId()), PesawisePaymentStatus.class);
        eventService.saveEvent(response, payment.getId());
        if (response == null) throw new PMSCustomException(ResponseCode.GENERAL_FAILURE);
        return response;
    }

    private PaymentAccount requireAccount(PMSInvoice invoice, Long accountId) {
        if (accountId == null || invoice.getPaymentAccountId() == null || !invoice.getPaymentAccountId().equals(accountId)) {
            throw new PaymentRequestException(ResponseCode.ACCOUNT_UNAUTHORIZED);
        }
        PaymentAccount account = accountDao.getAccountById(accountId);
        boolean subscription = StringUtils.isNotBlank(invoice.getSubscriptionPlanCode());
        if (!account.isActive() || !account.isVerified() || account.getChannel() != PaymentChannel.PESAWISE
                || !Objects.equals(account.getCreatedBy(), invoice.getPayToUserId())
                || subscription != (account.getCategory() == AccountCategory.SLICKHOOD)) {
            throw new PaymentRequestException(ResponseCode.ACCOUNT_UNAUTHORIZED);
        }
        return account;
    }

    private HttpHeaders credentials(long accountId, long propertyId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("api-key", property(accountId, PaymentPropertyKeys.PESAWISE_API_KEY, propertyId));
        headers.set("api-secret", property(accountId, PaymentPropertyKeys.PESAWISE_API_SECRET, propertyId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private long balanceId(long accountId, long propertyId) {
        try {
            long value = Long.parseLong(property(accountId, PaymentPropertyKeys.PESAWISE_BALANCE_ID, propertyId));
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new PaymentRequestException(ResponseCode.ACCOUNT_INCOMPLETE_PROPERTIES);
        }
    }

    private String property(long accountId, String key, long propertyId) {
        return paramService.getParamByAccountIdAndType(accountId,
                PaymentChannel.PESAWISE.findProperty(key), propertyId);
    }

    private String baseUrl() {
        return StringUtils.removeEnd(StringUtils.trimToEmpty(apiUrl), "/");
    }

    private static String normalizePhone(String value) {
        String digits = StringUtils.defaultString(value).replaceAll("\\D", "");
        if (digits.startsWith("0") && digits.length() == 10) digits = "254" + digits.substring(1);
        if (!digits.matches("254\\d{9}")) {
            throw new PaymentRequestException(ResponseCode.INVALID_PHONENUMBER);
        }
        return digits;
    }

    private static String maskedPhone(String phone) {
        return "+254 *** *** " + phone.substring(phone.length() - 3);
    }

    private static BigDecimal money(Double value) {
        if (value == null) throw new PaymentRequestException(ResponseCode.INVALID_AMOUNT);
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PesawiseStkRequest(long balanceId, double amount, String phoneNumber, String reference) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PesawisePaymentResponse(String paymentId, boolean success, String detail, String reference) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PesawiseWebhook(String requestId, String reference, boolean success, String details, Double amount) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PesawisePaymentStatus(String paymentStatus, String paymentId, String reference,
                                 String uniqueReference, Double amount, String currency,
                                 String description, String globalAccountId) {}
}
