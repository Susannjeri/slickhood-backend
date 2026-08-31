package org.pms.silverocean.service.payment.platforms.mpesa;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.HttpEvent;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.RestRequestException;
import org.pms.silverocean.service.RestTemplateService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.eventlogger.EventService;
import org.pms.silverocean.service.param.ParamService;
import org.pms.silverocean.service.payment.PaymentCallBackRequest;
import org.pms.silverocean.service.payment.PaymentCallBackResponse;
import org.pms.silverocean.service.payment.PaymentDao;
import org.pms.silverocean.service.payment.PaymentPlatform;
import org.pms.silverocean.service.payment.PaymentRequestException;
import org.pms.silverocean.service.payment.UpdatePaymentService;
import org.pms.silverocean.service.payment.platforms.TokenStore;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.AuthenticationResponse;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.MPesaPaymentDTO;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.MPesaPaymentResponseDTO;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.MPesaSTKPushRequest;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.MPesaStaticMembers;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.MpesaCallbackDTO;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.STKCallbackResponse;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.STKResponseDTO;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.StkErrorResponse;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentPropertyKeys;
import org.pms.silverocean.service.payment.wrappers.PaymentResponse;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.pms.silverocean.common.StaticStrings.BASIC_PREFIX;
import static org.pms.silverocean.common.StaticStrings.BEARER_PREFIX;

@Service("M-Pesa")
@Slf4j
@DependsOn("configInitService")
public class MPesaService extends PaymentPlatform {
    private final RestTemplateService restTemplateService;
    private final ConfigService configService;
    private final ParamService paramService;
    private final EventService eventService;
    private final UserDao userDao;
    private final PaymentDao paymentDao;
    private final ObjectMapper objectMapper;

    private final DateTimeFormatter MPESA_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String MPESA_RECEIPT_NUMBER = "MpesaReceiptNumber";

    private final LoadingCache<AccountFilterDetails, TokenStore> tokenCache = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(Duration.ofHours(1))
            .build(new CacheLoader<>() {
                @Override
                public TokenStore load(AccountFilterDetails accountFilterDetails) throws PaymentRequestException {
                    return authenticate(accountFilterDetails);
                }
            });

    public MPesaService(RestTemplateService restTemplateService, ConfigService configService, ParamService paramService, EventService eventService, UserDao userDao, PaymentDao paymentDao, UpdatePaymentService updatePaymentService, ObjectMapper objectMapper) {
        super(updatePaymentService);
        this.restTemplateService = restTemplateService;
        this.configService = configService;
        this.paramService = paramService;
        this.eventService = eventService;
        this.userDao = userDao;
        this.paymentDao = paymentDao;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentResponse initPayment(PMSInvoice pmsInvoice, long accountId) throws PaymentRequestException {
        String msisdn = userDao.findById(pmsInvoice.getBilledUserId())
                .map(Users::getPhoneNumber)
                .orElseThrow(() -> new PaymentRequestException(ResponseCode.PHONENUMBER_NOT_CONFIGURED_ERROR));
        return initPayment(pmsInvoice, msisdn, accountId);
    }

    public PaymentResponse initPayment(PMSInvoice pmsInvoice, String msisdn, long accountId) throws PaymentRequestException {
        if (StringUtils.isBlank(msisdn)) {
            log.info("MSISDN is blank {}", msisdn);
            throw new PaymentRequestException(ResponseCode.PHONENUMBER_NOT_CONFIGURED_ERROR);
        }
        log.info("Processing Mpesa STK Payment {} ", pmsInvoice.getRef());
        msisdn = msisdn.replaceFirst("^\\+", "");

        String businessShortCode = paramService.getParamByAccountIdAndType(accountId, PaymentChannel.MPESA.findProperty(PaymentPropertyKeys.PAYBILL), pmsInvoice.getPropertyId());
        String passKey = paramService.getParamByAccountIdAndType(accountId, PaymentChannel.MPESA.findProperty(PaymentPropertyKeys.STK_PASSKEY), pmsInvoice.getPropertyId());
        Pair<String, String> timestampAndPassword = getTimestampAndPassword(businessShortCode, passKey);

        String stkCallbackUrl = configService.getConfigByName(PMSConfigs.MPESA_STK_CALLBACK_BASE_URL).get().stringValue();
        MPesaSTKPushRequest mpesaSTKPushRequest = new MPesaSTKPushRequest(
                new MPesaStaticMembers(businessShortCode, timestampAndPassword.getRight(),
                        timestampAndPassword.getLeft(), stkCallbackUrl),
                pmsInvoice, msisdn);

        HttpHeaders headers = new HttpHeaders();

        PMSPayment stkPayment = new PMSPayment(mpesaSTKPushRequest, accountId);
        stkPayment.setPayToUserId(pmsInvoice.getPayToUserId());
        try {
            AccountFilterDetails accountFilterDetails = new AccountFilterDetails(accountId, pmsInvoice.getPropertyId());
            TokenStore token = tokenCache.get(accountFilterDetails);
            if (token.expiryTime().isBefore(LocalDateTime.now())) {
                tokenCache.invalidate(accountFilterDetails);
                token = tokenCache.getUnchecked(accountFilterDetails);
            }
            headers.add(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token.accessToken());

            paymentDao.savePMSPayment(stkPayment);
            eventService.cacheEvent(mpesaSTKPushRequest, stkPayment.getId());
            String initStkUrl = configService.getConfigByName(PMSConfigs.MPESA_STK_INIT_URL).get().stringValue();

            STKResponseDTO stkResponseDTO = restTemplateService.sendPostRequest(initStkUrl, mpesaSTKPushRequest, headers, STKResponseDTO.class);
            eventService.cacheEvent(stkResponseDTO, stkPayment.getId());
            stkPayment.setThirdPartyTransId(stkResponseDTO.checkoutRequestID());
            stkPayment.setStatus(stkResponseDTO.responseCode());
            stkPayment.setStatusDesc(stkResponseDTO.responseDescription());
            boolean success = "0".equals(stkResponseDTO.responseCode());

            return new PaymentResponse(
                    success,
                    success ? ResponseCode.MPESA_PAYMENT_INITIALIZED
                            : ResponseCode.MPESA_PAYMENT_INITIALIZATION_FAILED
            );
        } catch (RestRequestException e) {
            log.error(e.getMessage(), e);
            stkPayment.setStatus(String.valueOf(e.getHttpStatusCode()));
            if (StringUtils.isNotBlank(e.getResponseBody())) {
                try {
                    StkErrorResponse stkError = objectMapper.readValue(e.getResponseBody(), StkErrorResponse.class);
                    stkPayment.setStatusDesc(stkError.errorMessage());
                } catch (Exception parseEx) {
                    log.warn("Could not parse STK error response body: {}", e.getResponseBody(), parseEx);
                    stkPayment.setStatusDesc(e.getMessage());
                }
            } else {
                stkPayment.setStatusDesc(e.getMessage());
            }
            stkPayment.setInProgress(false);
            throw new PaymentRequestException(ResponseCode.PAYMENT_INITIALIZATION_FAILED, e);
        } catch (UncheckedExecutionException e) {
            log.error("Payment request exception", e);
            stkPayment.setStatus("575");
            stkPayment.setInProgress(false);
            Throwable cause = e.getCause();
            if (cause instanceof PaymentRequestException pre) {
                stkPayment.setStatusDesc(pre.getMessage());
                throw pre;
            }
            stkPayment.setStatusDesc(e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            stkPayment.setInProgress(false);
            stkPayment.setStatus("585");
            stkPayment.setStatusDesc("unhandled.exception");
        } finally {
            paymentDao.savePMSPayment(stkPayment);
            eventService.flushByTId(stkPayment.getId());
        }
        return new PaymentResponse(false, ResponseCode.MPESA_PAYMENT_INITIALIZATION_FAILED);
    }

    @Override
    protected boolean isActive() {
        return PMSUtils.booleanizeConfig(configService.getConfigByName(PMSConfigs.PAYMENT_MPESA_ENABLED).get());
    }

    @Override
    protected PaymentChannel channelType() {
        return PaymentChannel.MPESA;
    }

    @Override
    protected String channelIcon() {
        return configService.getConfigByName(PMSConfigs.MPESA_ICON).get().stringValue();
    }

    @Override
    public PaymentCallBackResponse handleCallBack(PaymentCallBackRequest paymentCallBackRequest) {
        if (paymentCallBackRequest instanceof MpesaCallbackDTO mpesaCallBackRequest) {
            return switch (mpesaCallBackRequest.mpesaCallBackType()) {
                case VALIDATION ->
                        validatePayment(mpesaCallBackRequest.mpesaPaymentDTO(), mpesaCallBackRequest.userId(), mpesaCallBackRequest.sourceIp());
                case CONFIRMATION ->
                        confirmPayment(mpesaCallBackRequest.mpesaPaymentDTO(), mpesaCallBackRequest.userId(), mpesaCallBackRequest.sourceIp());
                case STK -> stkCallBack(mpesaCallBackRequest.stkCallbackResponse(), mpesaCallBackRequest.sourceIp());
            };
        } else {
            log.error("Mpesa Callback cannot handle the PaymentCallBackRequest implementation {}", paymentCallBackRequest.getClass().getSimpleName());
            throw new PMSCustomException(ResponseCode.GENERAL_FAILURE);
        }


    }

    private TokenStore authenticate(AccountFilterDetails accountFilterDetails) {
        String consumerKey = paramService.getParamByAccountIdAndType(accountFilterDetails.accountId(),  PaymentChannel.MPESA.findProperty(PaymentPropertyKeys.CONSUMER_KEY), accountFilterDetails.propertyId());
        String consumerSecret = paramService.getParamByAccountIdAndType(accountFilterDetails.accountId(), PaymentChannel.MPESA.findProperty(PaymentPropertyKeys.CONSUMER_SECRET), accountFilterDetails.propertyId());
        String mPesaCredentials = PMSUtils.encodeToBase64(consumerKey + ":" + consumerSecret);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, BASIC_PREFIX + mPesaCredentials);

        try {
            String authUrl = configService.getConfigByName(PMSConfigs.MPESA_STK_AUTH_URL).get().stringValue();
            AuthenticationResponse authResponse = restTemplateService.sendGetRequest(authUrl, headers, AuthenticationResponse.class);
            return new TokenStore(authResponse.accessToken(), LocalDateTime.now().plusSeconds(Long.parseLong(authResponse.expiresIn())));
        } catch (RestRequestException e) {
            log.debug("Authentication exception {}", e.getMessage(), e);
            throw new PaymentRequestException(ResponseCode.MPESA_INITIALIZATION_ERROR, e);
        } catch (Exception e) {
            throw new PaymentRequestException(ResponseCode.MPESA_INITIALIZATION_ERROR);
        }
    }

    private Pair<String, String> getTimestampAndPassword(String businessShortCode, String passKey) {
        String timestamp = MPESA_DATE_FORMAT.format(LocalDateTime.now());
        return Pair.of(timestamp, PMSUtils.encodeToBase64(businessShortCode + passKey + timestamp));
    }


    public MPesaPaymentResponseDTO confirmPayment(MPesaPaymentDTO mpesaPaymentDTO, Long userId, String sourceIp) {
        PMSPayment payment = new PMSPayment(mpesaPaymentDTO, TransactionCategory.PAYMENT_PROCESSED);
        payment.setSourceIp(sourceIp);
        payment.setPayToUserId(Optional.ofNullable(userId).orElse(0L));
        if (paymentDao.callbackAlreadyProcessed(payment.getThirdPartyTransId(), payment.getBillReference(),
                payment.getCategory(), MPesaResultCodes.COMPLETED.getCode())) {
            log.info("Acknowledging duplicate M-Pesa confirmation for transaction {}", payment.getThirdPartyTransId());
            return new MPesaPaymentResponseDTO(MPesaResultCodes.VALID);
        }
        payment.setInProgress(false);
        Optional<PMSInvoice> invoice = updatePaymentService.getInvoicePayToIDUsingInvoiceRef(mpesaPaymentDTO.billRefNumber());
        if (invoice.isPresent() && invoice.get().isActive() && payment.getAmount() != null && payment.getAmount() > 0) {
            payment.setStatus(MPesaResultCodes.COMPLETED.getCode());
            payment.setStatusDesc(MPesaResultCodes.COMPLETED.getDesc());
            payment.setPayToUserId(invoice.get().getPayToUserId());
            updatePaymentService.setInvoiceToPaid(invoice.get(), payment.getThirdPartyTransId(), payment.getAmount());
        } else {
            payment.setStatus(MPesaResultCodes.INVALID_ACCOUNT_NUMBER.getCode());
            payment.setStatusDesc(MPesaResultCodes.INVALID_ACCOUNT_NUMBER.getDesc());
            log.warn("M-Pesa confirmation rejected for unknown/inactive invoice reference {}", mpesaPaymentDTO.billRefNumber());
        }
        paymentDao.savePMSPayment(payment);
        eventService.saveEvent(mpesaPaymentDTO, payment.getId());
        return new MPesaPaymentResponseDTO(MPesaResultCodes.VALID);
    }

    public MPesaPaymentResponseDTO validatePayment(MPesaPaymentDTO mpesaPaymentDTO, Long userId, String sourceIp) {
        PMSPayment validatePayment = new PMSPayment(mpesaPaymentDTO, TransactionCategory.PAYMENT_VALIDATION);
        validatePayment.setSourceIp(sourceIp);
        validatePayment.setPayToUserId(Optional.ofNullable(userId).orElse(0L));

        Optional<PMSInvoice> paymentInvoice = updatePaymentService.getInvoicePayToIDUsingInvoiceRef(mpesaPaymentDTO.billRefNumber());

        MPesaPaymentResponseDTO mPesaPaymentResponseDTO;

        if (paymentInvoice.isPresent() && paymentInvoice.get().isActive()) {
            PMSInvoice pmsInvoice = paymentInvoice.get();
            validatePayment.setPayToUserId(pmsInvoice.getPayToUserId());
            double amount = Double.parseDouble(mpesaPaymentDTO.transAmount());
            if (pmsInvoice.isPaid() || amount <= 0 || amount > pmsInvoice.getPendingAmount()) {
                validatePayment.setStatus(MPesaResultCodes.INVALID_AMOUNT.getCode());
                validatePayment.setStatusDesc(MPesaResultCodes.INVALID_AMOUNT.getDesc());
                mPesaPaymentResponseDTO = new MPesaPaymentResponseDTO(MPesaResultCodes.INVALID_AMOUNT);

                updatePaymentService.sendInvalidAccountNotification(pmsInvoice.getCustomerPhoneNumber());
            } else {
                validatePayment.setStatus(MPesaResultCodes.VALID.getCode());
                validatePayment.setStatusDesc(MPesaResultCodes.VALID.getDesc());
                mPesaPaymentResponseDTO = new MPesaPaymentResponseDTO(MPesaResultCodes.VALID);

                pmsInvoice.setTransactionInProgress(true);
                updatePaymentService.updateInvoice(pmsInvoice);
            }
        } else {
            validatePayment.setStatus(MPesaResultCodes.INVALID_ACCOUNT_NUMBER.getCode());
            validatePayment.setStatusDesc(MPesaResultCodes.INVALID_ACCOUNT_NUMBER.getDesc());
            mPesaPaymentResponseDTO = new MPesaPaymentResponseDTO(MPesaResultCodes.INVALID_ACCOUNT_NUMBER);
        }

        paymentDao.savePMSPayment(validatePayment);

        eventService.cacheEvent(mPesaPaymentResponseDTO, validatePayment.getId());
        eventService.cacheEvent(mpesaPaymentDTO, validatePayment.getId());

        eventService.flushByTId(validatePayment.getId());
        return mPesaPaymentResponseDTO;
    }

    public MPesaPaymentResponseDTO stkCallBack(STKCallbackResponse stkCallbackResponse, String sourceIp) {
        HttpEvent event = new HttpEvent();
        event.setEventType(STKCallbackResponse.class.getSimpleName());
        event.setEvent(stkCallbackResponse.toString().getBytes());

        paymentDao.findPaymentByThirdPartyID(stkCallbackResponse.body().stkCallback().checkoutRequestID())
                .ifPresent(pmsPayment -> {
                    event.setTId(pmsPayment.getId());
                    if (!pmsPayment.isInProgress()) {
                        log.info("Acknowledging duplicate M-Pesa STK callback for payment {}", pmsPayment.getId());
                        return;
                    }
                    pmsPayment.setStatus(String.valueOf(stkCallbackResponse.body().stkCallback().resultCode()));
                    pmsPayment.setInProgress(false);
                    updatePaymentService.setInvoiceTransactionStatusByBillRefNumber(pmsPayment.getBillReference(), false);
                    pmsPayment.setStatusDesc(stkCallbackResponse.body().stkCallback().resultDesc());
                    pmsPayment.setSourceIp(sourceIp);
                    Optional.ofNullable(stkCallbackResponse.body().stkCallback().callbackMetadata())
                            .ifPresent(stkCallbackMetadata -> stkCallbackMetadata.items().forEach(item -> {
                                if (item.name().equals(MPESA_RECEIPT_NUMBER)) {
                                    pmsPayment.setThirdPartyTransId(item.value().toString());
                                    //set invoice to paid
                                    updatePaymentService.setInvoiceToPaid(pmsPayment.getBillReference(), pmsPayment.getThirdPartyTransId(), pmsPayment.getAmount());
                                }
                            }));
                    paymentDao.savePMSPayment(pmsPayment);
                });
        eventService.saveEvent(event);
        return new MPesaPaymentResponseDTO(MPesaResultCodes.VALID);
    }
}
