package org.pms.silverocean.service.payment.platforms.pesalink;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.config.ConfigDTO;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.eventlogger.EventService;
import org.pms.silverocean.service.payment.PaymentCallBackRequest;
import org.pms.silverocean.service.payment.PaymentCallBackResponse;
import org.pms.silverocean.service.payment.PaymentDao;
import org.pms.silverocean.service.payment.PaymentPlatform;
import org.pms.silverocean.service.payment.PaymentRequestException;
import org.pms.silverocean.service.payment.UpdatePaymentService;
import org.pms.silverocean.service.payment.WebhookSignatureVerifier;
import org.pms.silverocean.service.payment.platforms.mpesa.TransactionCategory;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.IPNCallbackDTO;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.IPNCallbackResponseDTO;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.PesalinkCallbackDTO;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.PesalinkStatus;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.PesalinkValidatePaymentRequestDTO;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.PesalinkValidatePaymentResponseDTO;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.function.Supplier;

@Service("PesaLink")
@Slf4j
public class PesaLinkService extends PaymentPlatform {
    private final ConfigService configService;
    private final I18NService i18NService;
    private final EventService eventService;
    private final PaymentDao paymentDao;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Supplier<ConfigDTO> ipnPassword;

    protected PesaLinkService(UpdatePaymentService updatePaymentService, ConfigService configService,
                              I18NService i18NService, EventService eventService, PaymentDao paymentDao) {
        super(updatePaymentService);
        this.configService = configService;
        this.i18NService = i18NService;
        this.eventService = eventService;
        this.paymentDao = paymentDao;
    }

    @PostConstruct
    public void init() {
        ipnPassword = configService.getConfigByName(PMSConfigs.PESA_LINK_IPN_PASSWORD);
    }

    @Override
    protected PaymentResponse initPayment(PMSInvoice pmsInvoice, long accountId) throws PaymentRequestException {
        pmsInvoice.setTransactionInProgress(false);
        updatePaymentService.updateInvoice(pmsInvoice);
        return new PaymentResponse(true, ResponseCode.GENERAL_SUCCESS,
                String.format(i18NService.getLocalizedMessage(ResponseCode.PESALINK_INIT_PAYMENT), pmsInvoice.getRef()));
    }

    @Override
    protected boolean isActive() {
        return PMSUtils.booleanizeConfig(configService.getConfigByName(PMSConfigs.PAYMENT_PESA_LINK_ENABLED).get());
    }

    @Override
    protected PaymentChannel channelType() {
        return PaymentChannel.PESA_LINK;
    }

    @Override
    protected String channelIcon() {
        return configService.getConfigByName(PMSConfigs.PESA_LINK_ICON).get().stringValue();
    }

    @Override
    public PaymentCallBackResponse handleCallBack(PaymentCallBackRequest paymentCallBackRequest) {
        if (paymentCallBackRequest instanceof PesalinkCallbackDTO pesalinkCallbackDTO) {
            return switch (pesalinkCallbackDTO.pesalinkCallbackType()) {
                case VALIDATION ->
                        validatePayment(pesalinkCallbackDTO.pesalinkValidatePaymentRequestDTO(), pesalinkCallbackDTO.ip());
                case IPN ->
                        confirmPayment(pesalinkCallbackDTO.ipnCallbackDTO(), pesalinkCallbackDTO.signature(), pesalinkCallbackDTO.ip());
            };
        } else {
            log.error("Pesalink Callback cannot handle the PaymentCallBackRequest implementation {}", paymentCallBackRequest.getClass().getSimpleName());
            throw new PMSCustomException(ResponseCode.GENERAL_FAILURE);
        }
    }


    public IPNCallbackResponseDTO confirmPayment(IPNCallbackDTO ipnCallbackDTO, String signature, String sourceIp) {
        PMSPayment payment = new PMSPayment(ipnCallbackDTO, TransactionCategory.PAYMENT_PROCESSED);
        payment.setSourceIp(sourceIp);

        if (isSignatureValid(ipnCallbackDTO, signature, ipnPassword.get().stringValue())) {
            if (paymentDao.callbackAlreadyProcessed(payment.getThirdPartyTransId(), payment.getBillReference(),
                    payment.getCategory(), PesalinkStatus.SUCCESS.getStatus())) {
                log.info("Acknowledging duplicate PesaLink IPN for transaction {}", payment.getThirdPartyTransId());
                return new IPNCallbackResponseDTO(ipnCallbackDTO.rrn(), PesalinkStatus.SUCCESS.getStatus());
            }
            payment.setStatus(PesalinkStatus.SUCCESS.getStatus());
            payment.setStatusDesc(PesalinkStatus.SUCCESS.getDescription());
            payment.setInProgress(false);
            updatePaymentService.getInvoicePayToIDUsingInvoiceRef(ipnCallbackDTO.billReference())
                    .ifPresent(invoice -> {
                        payment.setPayToUserId(invoice.getPayToUserId());
                        updatePaymentService.setInvoiceToPaid(invoice, payment.getThirdPartyTransId(), payment.getAmount());
                    });
        } else {
            payment.setStatus(PesalinkStatus.INSECURE.getStatus());
            payment.setStatusDesc(PesalinkStatus.INSECURE.getDescription());
        }
        paymentDao.savePMSPayment(payment);
        eventService.saveEvent(ipnCallbackDTO, payment.getId());
        return new IPNCallbackResponseDTO(ipnCallbackDTO.rrn(), payment.getStatus());
    }

    public PesalinkValidatePaymentResponseDTO validatePayment(PesalinkValidatePaymentRequestDTO pesalinkValidatePaymentRequestDTO, String sourceIp) {
        PMSPayment validatePayment = new PMSPayment(pesalinkValidatePaymentRequestDTO, TransactionCategory.PAYMENT_VALIDATION);
        validatePayment.setSourceIp(sourceIp);

        Optional<PMSInvoice> paymentInvoice = updatePaymentService.getInvoicePayToIDUsingInvoiceRef(pesalinkValidatePaymentRequestDTO.billRef());

        PesalinkValidatePaymentResponseDTO pesalinkValidatePaymentResponseDTO;


        if (paymentInvoice.isPresent() && paymentInvoice.get().isActive()) {
            PMSInvoice pmsInvoice = paymentInvoice.get();
            validatePayment.setPayToUserId(pmsInvoice.getPayToUserId());
            if (pmsInvoice.isPaid() || pesalinkValidatePaymentRequestDTO.amount().doubleValue() > pmsInvoice.getPendingAmount()) {
                validatePayment.setStatus(PesalinkStatus.INVALID_AMOUNT.getStatus());
                validatePayment.setStatusDesc(PesalinkStatus.INVALID_AMOUNT.getDescription());
                pesalinkValidatePaymentResponseDTO = new PesalinkValidatePaymentResponseDTO(pesalinkValidatePaymentRequestDTO, null, PesalinkStatus.INVALID_AMOUNT);

                updatePaymentService.sendInvalidAccountNotification(pmsInvoice.getCustomerPhoneNumber());
            } else {
                validatePayment.setStatus(PesalinkStatus.VALID.getStatus());
                pesalinkValidatePaymentResponseDTO = new PesalinkValidatePaymentResponseDTO(pesalinkValidatePaymentRequestDTO, validatePayment.getUuid().toString(), PesalinkStatus.VALID);

                pmsInvoice.setTransactionInProgress(true);
                updatePaymentService.updateInvoice(pmsInvoice);
            }
        } else {
            validatePayment.setStatus(PesalinkStatus.INVALID_BILL_REF.getStatus());
            validatePayment.setStatusDesc(PesalinkStatus.INVALID_BILL_REF.getDescription());
            pesalinkValidatePaymentResponseDTO = new PesalinkValidatePaymentResponseDTO(pesalinkValidatePaymentRequestDTO, null, PesalinkStatus.INVALID_BILL_REF);
        }

        paymentDao.savePMSPayment(validatePayment);

        eventService.cacheEvent(pesalinkValidatePaymentResponseDTO, validatePayment.getId());
        eventService.cacheEvent(pesalinkValidatePaymentRequestDTO, validatePayment.getId());

        eventService.flushByTId(validatePayment.getId());
        return pesalinkValidatePaymentResponseDTO;
    }

    public static boolean isSignatureValid(IPNCallbackDTO ipnCallbackDTO, String signature, String password) {
        try {
            String requestJson = MAPPER.writeValueAsString(ipnCallbackDTO);
            byte[] signedObject = PMSUtils.signDataUsingHmacSha1(password.getBytes(), requestJson.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : signedObject) {
                sb.append(String.format("%02x", b));
            }
            log.debug("Calculated PesaLink callback signature for RRN {}", ipnCallbackDTO.rrn());
            return StringUtils.hasText(signature)
                    && WebhookSignatureVerifier.constantTimeEquals(sb.toString(), signature.toLowerCase(java.util.Locale.ROOT));
        } catch (JsonProcessingException e) {
            log.error("Error Calculating verification signature, returning false", e);
            return false;
        }
    }
}
