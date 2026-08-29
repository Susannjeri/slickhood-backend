package org.pms.silverocean.service.payment.platforms.flutterwave;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
import org.pms.silverocean.service.payment.platforms.flutterwave.wrappers.CustomizedFWConfigs;
import org.pms.silverocean.service.payment.platforms.flutterwave.wrappers.FWCallbackDTO;
import org.pms.silverocean.service.payment.platforms.flutterwave.wrappers.FWCallbackEvents;
import org.pms.silverocean.service.payment.platforms.flutterwave.wrappers.FWCallbackResponse;
import org.pms.silverocean.service.payment.platforms.flutterwave.wrappers.FWChargeCompletedData;
import org.pms.silverocean.service.payment.platforms.flutterwave.wrappers.FWCustomer;
import org.pms.silverocean.service.payment.platforms.flutterwave.wrappers.FWCustomizations;
import org.pms.silverocean.service.payment.platforms.flutterwave.wrappers.FWTransferCompletedData;
import org.pms.silverocean.service.payment.platforms.flutterwave.wrappers.FWWebhookDTO;
import org.pms.silverocean.service.payment.platforms.flutterwave.wrappers.PaymentLinkDTO;
import org.pms.silverocean.service.payment.platforms.flutterwave.wrappers.PaymentLinkResponseDTO;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service("Card Payment")
@DependsOn("configService")
@Slf4j
public class FlutterWavePlatform extends PaymentPlatform {
    private final UserDao userDao;
    private final PaymentDao paymentDao;
    private final FlutterWaveConfigService flutterWaveConfigService;
    private final EventService eventService;
    private final RestTemplateService restTemplateService;
    private final FWVerifyTransaction fwVerifyTransaction;
    private int sessionDuration;
    private int maxRetries;
    private String icon;

    @Value("${spring.application.name}")
    private String applicationName;

    private final ObjectMapper mapper = new ObjectMapper();

    public FlutterWavePlatform(ConfigService configService, ParamService paramService,
                               RestTemplateService restTemplateService, EventService eventService,
                               UserDao userDao, PaymentDao paymentDao, UpdatePaymentService updatePaymentService, FWVerifyTransaction fwVerifyTransaction) {
        super(updatePaymentService);
        this.userDao = userDao;
        this.paymentDao = paymentDao;
        this.fwVerifyTransaction = fwVerifyTransaction;
        this.flutterWaveConfigService = new FlutterWaveConfigService(configService, paramService);
        this.eventService = eventService;
        this.restTemplateService = restTemplateService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initAfterStartUp() {
        flutterWaveConfigService.initCommonFlutterWaveConfig();
        sessionDuration =  flutterWaveConfigService.configService.getConfigByName(PMSConfigs.FW_SESSION_DURATION).get().intValue();
        maxRetries =  flutterWaveConfigService.configService.getConfigByName(PMSConfigs.FW_MAX_RETRIES).get().intValue();
        icon = flutterWaveConfigService.configService.getConfigByName(PMSConfigs.FW_ICON).get().stringValue();
    }

    @Override
    public PaymentResponse initPayment(PMSInvoice pmsInvoice, long accountId) throws PaymentRequestException {
        Users users = userDao.findById(userDao.getUserId()).orElseThrow();

        PMSPayment payment = new PMSPayment(pmsInvoice, users.getFullName(), accountId);
        paymentDao.savePMSPayment(payment);
        PaymentLinkDTO flutterWavePayment = new PaymentLinkDTO(payment.getId(), pmsInvoice,  flutterWaveConfigService.redirectUrl,
                new FWCustomer(users.getEmail()), sessionDuration, maxRetries, new FWCustomizations(applicationName, new String(pmsInvoice.getDescription()),  flutterWaveConfigService.logoUrl));
        try {
            CustomizedFWConfigs customizedFWConfigs =  flutterWaveConfigService.getUserFWConfigs(accountId, pmsInvoice.getPropertyId());
            PaymentLinkResponseDTO paymentLinkResponseDTO = sendPayment(flutterWavePayment, payment.getId(), customizedFWConfigs.secretKey());
            payment.setStatus(paymentLinkResponseDTO.status());
            payment.setStatusDesc(paymentLinkResponseDTO.message());
            return new PaymentResponse(true, ResponseCode.CARD_PAYMENT_INITIALIZED, paymentLinkResponseDTO.data().link());
        } catch (RestRequestException e) {
            log.error(e.getMessage(), e);
            payment.setStatus(String.valueOf(e.getHttpStatusCode()));
            payment.setStatusDesc(e.getMessage());
            throw new PaymentRequestException(ResponseCode.PAYMENT_INITIALIZATION_FAILED, e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            payment.setStatus(String.valueOf(989));
            payment.setStatusDesc(e.getMessage());
            throw new PaymentRequestException(ResponseCode.PAYMENT_INITIALIZATION_FAILED, e);
        } finally {
            paymentDao.savePMSPayment(payment);
            eventService.flushByTId(payment.getId());
        }
    }

    @Override
    protected boolean isActive() {
        return PMSUtils.booleanizeConfig(flutterWaveConfigService.configService.getConfigByName(PMSConfigs.PAYMENT_FW_ENABLED).get());
    }

    @Override
    protected PaymentChannel channelType() {
        return PaymentChannel.FLUTTER_WAVE;
    }

    @Override
    protected String channelIcon() {
        return icon;
    }

    @Override
    public PaymentCallBackResponse handleCallBack(PaymentCallBackRequest paymentCallBackRequest) {
        if (paymentCallBackRequest instanceof FWCallbackDTO<?> fwCallbackDTO) {
            return receiveFlutterWaveCallback(fwCallbackDTO.fwWebhookDTO(), fwCallbackDTO.sourceIp());
        } else {
            log.error("Flutterwave Callback cannot handle the PaymentCallBackRequest implementation {}", paymentCallBackRequest.getClass());
            throw new PMSCustomException(ResponseCode.GENERAL_FAILURE);
        }
    }

    private PaymentLinkResponseDTO sendPayment(PaymentLinkDTO paymentLinkDTO, long paymentId, String secretKey) {
        eventService.cacheEvent(paymentLinkDTO, paymentId);

        PaymentLinkResponseDTO paymentLinkResponseDTO =  restTemplateService
                .sendPostRequest( flutterWaveConfigService.flutterWaveUrl +  flutterWaveConfigService.INIT_PAYMENT_URL_SUFFIX,
                        paymentLinkDTO, flutterWaveConfigService.buildAuthHeaders(secretKey), PaymentLinkResponseDTO.class);
        eventService.cacheEvent(paymentLinkResponseDTO, paymentId);
        return paymentLinkResponseDTO;
    }


    public <T> FWCallbackResponse receiveFlutterWaveCallback(FWWebhookDTO<T> fwWebhookDTO, String sourceIp) {
        FWCallbackEvents flutterWaveCallback = FWCallbackEvents.getEvent(fwWebhookDTO.event());
        HttpEvent event = new HttpEvent();

        switch (flutterWaveCallback) {
            case TRANSFER_COMPLETED -> {
                FWTransferCompletedData transferEvent = mapper.convertValue(fwWebhookDTO.data(), FWTransferCompletedData.class);
                event.setEventType(FWTransferCompletedData.class.getSimpleName());
                event.setEvent(transferEvent.toString().getBytes());


            }
            case CHARGE_COMPLETED -> {
                FWChargeCompletedData chargeEvent = mapper.convertValue(fwWebhookDTO.data(), FWChargeCompletedData.class);
                event.setEventType(FWChargeCompletedData.class.getSimpleName());
                event.setEvent(chargeEvent.toString().getBytes());
                long paymentId = Long.parseLong(chargeEvent.txRef());
                event.setTId(paymentId);
                fwVerifyTransaction.cancelScheduledVerifyFlutterWaveTransaction(paymentId);
                paymentDao.findPaymentByID(paymentId)
                        .ifPresent(payment -> {
                            updatePaymentService.setInvoiceTransactionStatusByBillRefNumber(payment.getBillReference(), false);
                            if (!payment.getStatus().equals(chargeEvent.status())) {
                                payment.setStatus(chargeEvent.status());
                                payment.setStatusDesc(chargeEvent.processorResponse());
                                payment.setSourceIp(sourceIp);
                                payment.setThirdPartyTransId(String.valueOf(chargeEvent.id()));
                                paymentDao.savePMSPayment(payment);
                                if ("successful".equals(chargeEvent.status())) {
                                    fwVerifyTransaction.verifyFlutterWaveTransaction(paymentId);
                                }
                            }
                        });
            }
        }
        eventService.saveEvent(event);
        return new FWCallbackResponse("Callback received");
    }
}
