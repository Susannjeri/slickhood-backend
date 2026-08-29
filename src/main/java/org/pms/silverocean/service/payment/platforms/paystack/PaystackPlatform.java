package org.pms.silverocean.service.payment.platforms.paystack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.common.StaticStrings;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.RestRequestException;
import org.pms.silverocean.service.RestTemplateService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.eventlogger.EventService;
import org.pms.silverocean.service.param.ParamService;
import org.pms.silverocean.service.payment.PaymentCallBackRequest;
import org.pms.silverocean.service.payment.PaymentCallBackResponse;
import org.pms.silverocean.service.payment.PaymentDao;
import org.pms.silverocean.service.payment.PaymentPlatform;
import org.pms.silverocean.service.payment.PaymentRequestException;
import org.pms.silverocean.service.payment.UpdatePaymentService;
import org.pms.silverocean.service.payment.platforms.mpesa.TransactionCategory;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentPropertyKeys;
import org.pms.silverocean.service.payment.wrappers.PaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

@Service("Paystack")
@Slf4j
public class PaystackPlatform extends PaymentPlatform {
    private static final String INITIALIZE_PATH = "/transaction/initialize";
    private static final String VERIFY_PATH = "/transaction/verify/";
    private static final String SUCCESS = "success";

    private final UserDao userDao;
    private final PaymentDao paymentDao;
    private final ParamService paramService;
    private final RestTemplateService restTemplateService;
    private final EventService eventService;
    private final ObjectMapper objectMapper;

    @Value("${payment.paystack.enabled:false}")
    private boolean enabled;
    @Value("${payment.paystack.secret-key:}")
    private String secretKey;
    @Value("${payment.paystack.api-url:https://api.paystack.co}")
    private String apiUrl;
    @Value("${payment.paystack.callback-url:http://localhost:3000/payment/callback}")
    private String callbackUrl;
    @Value("${payment.paystack.currency:KES}")
    private String defaultCurrency;
    @Value("${payment.paystack.channels:card,mobile_money}")
    private String configuredChannels;
    @Value("${payment.paystack.fee-bearer:subaccount}")
    private String feeBearer;
    @Value("${payment.paystack.icon:card_icon.png}")
    private String icon;

    public PaystackPlatform(UpdatePaymentService updatePaymentService, UserDao userDao, PaymentDao paymentDao,
                            ParamService paramService, RestTemplateService restTemplateService,
                            EventService eventService, ObjectMapper objectMapper) {
        super(updatePaymentService);
        this.userDao = userDao;
        this.paymentDao = paymentDao;
        this.paramService = paramService;
        this.restTemplateService = restTemplateService;
        this.eventService = eventService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected PaymentResponse initPayment(PMSInvoice invoice, long accountId) throws PaymentRequestException {
        Users customer = userDao.findById(userDao.getUserId()).orElseThrow();
        String subaccountCode = paramService.getParamByAccountIdAndType(accountId,
                PaymentChannel.PAYSTACK.findProperty(PaymentPropertyKeys.SUBACCOUNT_CODE), invoice.getPropertyId());
        PMSPayment payment = new PMSPayment(invoice, customer.getFullName(), accountId);
        payment.setChannel(PaymentChannel.PAYSTACK.getName());
        paymentDao.savePMSPayment(payment);

        try {
            PaystackInitializeRequest request = new PaystackInitializeRequest(
                    customer.getEmail(), toSubunit(invoice.getPendingAmount()),
                    StringUtils.defaultIfBlank(invoice.getCurrency(), defaultCurrency),
                    String.valueOf(payment.getId()), callbackUrl, channels(), subaccountCode, feeBearer);
            PaystackInitializeResponse response = restTemplateService.sendPostRequest(
                    apiUrl + INITIALIZE_PATH, request, authHeaders(), PaystackInitializeResponse.class);
            eventService.saveEvent(response, payment.getId());
            if (response == null || !response.status() || response.data() == null) {
                throw new PaymentRequestException(ResponseCode.PAYMENT_INITIALIZATION_FAILED);
            }
            payment.setThirdPartyTransId(response.data().reference());
            payment.setStatus("initialized");
            payment.setStatusDesc(response.message());
            paymentDao.savePMSPayment(payment);
            return new PaymentResponse(true, ResponseCode.CARD_PAYMENT_INITIALIZED, response.data().authorizationUrl());
        } catch (RestRequestException e) {
            payment.setStatus(String.valueOf(e.getHttpStatusCode()));
            payment.setStatusDesc(e.getMessage());
            paymentDao.savePMSPayment(payment);
            throw new PaymentRequestException(ResponseCode.PAYMENT_INITIALIZATION_FAILED, e);
        } catch (PaymentRequestException e) {
            payment.setStatus("failed");
            payment.setStatusDesc(e.getMessage());
            paymentDao.savePMSPayment(payment);
            throw e;
        }
    }

    @Override
    protected boolean isActive() {
        return enabled && StringUtils.isNotBlank(secretKey);
    }

    @Override
    protected PaymentChannel channelType() {
        return PaymentChannel.PAYSTACK;
    }

    @Override
    protected String channelIcon() {
        return icon;
    }

    @Override
    public PaymentCallBackResponse handleCallBack(PaymentCallBackRequest request) {
        if (!(request instanceof PaystackCallbackDTO callback)) {
            throw new PMSCustomException(ResponseCode.GENERAL_FAILURE);
        }
        try {
            PaystackWebhookEvent event = objectMapper.readValue(callback.rawBody(), PaystackWebhookEvent.class);
            if (!"charge.success".equals(event.event()) || event.data() == null) {
                return new PaystackCallbackResponse("Event acknowledged");
            }
            long paymentId = Long.parseLong(event.data().reference());
            paymentDao.findPaymentByID(paymentId)
                    .filter(PMSPayment::isInProgress)
                    .ifPresent(payment -> verifyAndSettle(payment, callback.sourceIp()));
            return new PaystackCallbackResponse("Callback received");
        } catch (Exception e) {
            log.error("Unable to process Paystack callback", e);
            throw new PMSCustomException(ResponseCode.GENERAL_FAILURE);
        }
    }

    private void verifyAndSettle(PMSPayment payment, String sourceIp) {
        PaystackVerifyResponse response = restTemplateService.sendGetRequest(
                apiUrl + VERIFY_PATH + payment.getId(), authHeaders(), PaystackVerifyResponse.class);
        eventService.saveEvent(response, payment.getId());
        PaystackTransaction data = response == null ? null : response.data();
        boolean valid = response != null && response.status() && data != null
                && SUCCESS.equals(data.status())
                && String.valueOf(payment.getId()).equals(data.reference())
                && toSubunit(payment.getAmount()) == data.amount();

        PMSInvoice invoice = updatePaymentService.getInvoicePayToIDUsingInvoiceRef(payment.getBillReference()).orElse(null);
        valid = valid && invoice != null && StringUtils.equalsIgnoreCase(invoice.getCurrency(), data.currency());

        payment.setSourceIp(sourceIp);
        payment.setInProgress(false);
        payment.setStatus(valid ? TransactionCategory.CARD_PAYMENT.getSuccessString() : "verification_failed");
        payment.setStatusDesc(valid ? data.gatewayResponse() : "Paystack verification mismatch");
        if (data != null) {
            payment.setThirdPartyTransId(String.valueOf(data.id()));
        }
        paymentDao.savePMSPayment(payment);
        updatePaymentService.setInvoiceTransactionStatusByBillRefNumber(payment.getBillReference(), false);
        if (valid) {
            updatePaymentService.setInvoiceToPaid(invoice, payment.getThirdPartyTransId(), fromSubunit(data.amount()));
        } else {
            log.warn("Rejected Paystack settlement for payment {} after verification mismatch", payment.getId());
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, StaticStrings.BEARER_PREFIX + secretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private List<String> channels() {
        return Arrays.stream(configuredChannels.split(","))
                .map(String::trim).filter(StringUtils::isNotBlank).toList();
    }

    private static long toSubunit(double amount) {
        return BigDecimal.valueOf(amount).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }

    private static double fromSubunit(long amount) {
        return BigDecimal.valueOf(amount, 2).doubleValue();
    }

    record PaystackInitializeRequest(String email, long amount, String currency, String reference,
                                     @JsonProperty("callback_url") String callbackUrl,
                                     List<String> channels, String subaccount, String bearer) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaystackInitializeResponse(boolean status, String message, PaystackInitializeData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaystackInitializeData(@JsonProperty("authorization_url") String authorizationUrl,
                                  @JsonProperty("access_code") String accessCode, String reference) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaystackWebhookEvent(String event, PaystackTransaction data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaystackVerifyResponse(boolean status, String message, PaystackTransaction data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaystackTransaction(long id, String status, String reference, long amount, String currency,
                               @JsonProperty("gateway_response") String gatewayResponse) {
    }
}
