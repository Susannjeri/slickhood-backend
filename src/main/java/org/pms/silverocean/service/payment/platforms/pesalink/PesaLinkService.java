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
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.config.ConfigDTO;
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
import org.pms.silverocean.service.payment.WebhookSignatureVerifier;
import org.pms.silverocean.service.payment.platforms.mpesa.TransactionCategory;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.IPNCallbackDTO;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.IPNCallbackResponseDTO;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.PesalinkCallbackDTO;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.PesalinkStatus;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.PesalinkValidatePaymentRequestDTO;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.PesalinkValidatePaymentResponseDTO;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentPropertyKeys;
import org.pms.silverocean.service.payment.wrappers.PaymentResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Service("PesaLink")
@Slf4j
public class PesaLinkService extends PaymentPlatform {
    private final ConfigService configService;
    private final I18NService i18NService;
    private final EventService eventService;
    private final PaymentDao paymentDao;
    private final ParamService paramService;
    private final AccountDao accountDao;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Supplier<ConfigDTO> ipnPassword;

    protected PesaLinkService(UpdatePaymentService updatePaymentService, ConfigService configService,
                              I18NService i18NService, EventService eventService, PaymentDao paymentDao,
                              ParamService paramService, AccountDao accountDao) {
        super(updatePaymentService);
        this.configService = configService;
        this.i18NService = i18NService;
        this.eventService = eventService;
        this.paymentDao = paymentDao;
        this.paramService = paramService;
        this.accountDao = accountDao;
    }

    @PostConstruct
    public void init() {
        ipnPassword = configService.getConfigByName(PMSConfigs.PESA_LINK_IPN_PASSWORD);
    }

    @Override
    protected PaymentResponse initPayment(PMSInvoice pmsInvoice, long accountId) throws PaymentRequestException {
        if (!"KES".equalsIgnoreCase(StringUtils.hasText(pmsInvoice.getCurrency()) ? pmsInvoice.getCurrency() : "KES"))
            throw new PaymentRequestException(ResponseCode.PAYMENT_CURRENCY_UNSUPPORTED);
        String bankAccount = paramService.getParamByAccountIdAndType(accountId,
                PaymentChannel.PESA_LINK.findProperty(PaymentPropertyKeys.BANK_ACCOUNT), pmsInvoice.getPropertyId());
        String bankCode = paramService.getParamByAccountIdAndType(accountId,
                PaymentChannel.PESA_LINK.findProperty(PaymentPropertyKeys.BANK_CODE), pmsInvoice.getPropertyId());
        pmsInvoice.setTransactionInProgress(false);
        updatePaymentService.updateInvoice(pmsInvoice);
        return new PaymentResponse(true, ResponseCode.PESALINK_INIT_PAYMENT,
                String.format(i18NService.getLocalizedMessage(ResponseCode.PESALINK_INIT_PAYMENT),
                        bankCode, bankAccount, pmsInvoice.getRef()));
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
        payment.setInProgress(false);

        if (isSignatureValid(ipnCallbackDTO, signature, ipnPassword.get().stringValue())) {
            Optional<PMSInvoice> invoice = updatePaymentService.getInvoicePayToIDUsingInvoiceRef(ipnCallbackDTO.billReference());
            invoice.ifPresent(value -> payment.setCurrencyCode(
                    org.pms.silverocean.service.payment.money.MonetaryPolicy.currency(value.getCurrency())));
            if ("ACCP".equalsIgnoreCase(org.apache.commons.lang3.StringUtils.trimToEmpty(ipnCallbackDTO.status()))
                    && invoice.isPresent() && invoice.get().isActive()
                    && payment.getAmount() != null && payment.getAmount() > 0
                    && callbackMatchesInvoiceDestination(invoice.get(), ipnCallbackDTO)) {
                payment.setPayToUserId(invoice.get().getPayToUserId());
                payment.setAccountId(invoice.get().getPaymentAccountId());
                // A replay is safe to acknowledge only after re-validating the signed
                // provider status and the invoice-pinned recipient destination.
                if (paymentDao.callbackAlreadyProcessed(payment.getThirdPartyTransId(), payment.getBillReference(),
                        payment.getCategory(), PesalinkStatus.SUCCESS.getStatus())) {
                    log.info("Acknowledging duplicate PesaLink IPN for transaction {}", payment.getThirdPartyTransId());
                    return new IPNCallbackResponseDTO(ipnCallbackDTO.rrn(), PesalinkStatus.SUCCESS.getStatus());
                }
                payment.setStatus(PesalinkStatus.SUCCESS.getStatus());
                payment.setStatusDesc(PesalinkStatus.SUCCESS.getDescription());
                updatePaymentService.setInvoiceToPaid(invoice.get(), payment.getThirdPartyTransId(), payment.moneyAmount());
            } else {
                payment.setStatus(PesalinkStatus.INVALID_BILL_REF.getStatus());
                payment.setStatusDesc(PesalinkStatus.INVALID_BILL_REF.getDescription());
                log.warn("PesaLink IPN rejected for unsuccessful status or unknown, inactive, or mismatched invoice destination {}",
                        ipnCallbackDTO.billReference());
            }
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
        // Account validation is advisory and must never reserve or lock an invoice.
        validatePayment.setInProgress(false);

        if (!isValidationSignatureValid(pesalinkValidatePaymentRequestDTO, ipnPassword.get().stringValue())) {
            validatePayment.setStatus(PesalinkStatus.INSECURE.getStatus());
            validatePayment.setStatusDesc(PesalinkStatus.INSECURE.getDescription());
            PesalinkValidatePaymentResponseDTO response = new PesalinkValidatePaymentResponseDTO(
                    pesalinkValidatePaymentRequestDTO, null, PesalinkStatus.INSECURE);
            paymentDao.savePMSPayment(validatePayment);
            eventService.cacheEvent(response, validatePayment.getId());
            eventService.cacheEvent(pesalinkValidatePaymentRequestDTO, validatePayment.getId());
            eventService.flushByTId(validatePayment.getId());
            log.warn("Rejected unauthenticated PesaLink validation request from {}", sourceIp);
            return response;
        }

        Optional<PMSInvoice> paymentInvoice = updatePaymentService.getInvoicePayToIDUsingInvoiceRef(pesalinkValidatePaymentRequestDTO.billRef());
        paymentInvoice.ifPresent(value -> validatePayment.setCurrencyCode(
                org.pms.silverocean.service.payment.money.MonetaryPolicy.currency(value.getCurrency())));

        PesalinkValidatePaymentResponseDTO pesalinkValidatePaymentResponseDTO;


        if (paymentInvoice.isPresent() && paymentInvoice.get().isActive()) {
            PMSInvoice pmsInvoice = paymentInvoice.get();
            validatePayment.setPayToUserId(pmsInvoice.getPayToUserId());
            java.math.BigDecimal amount = org.pms.silverocean.service.payment.money.MonetaryPolicy.amount(
                    pesalinkValidatePaymentRequestDTO.amount());
            if (pmsInvoice.isPaid() || amount.signum() <= 0 || amount.compareTo(pmsInvoice.moneyPendingAmount()) > 0) {
                validatePayment.setStatus(PesalinkStatus.INVALID_AMOUNT.getStatus());
                validatePayment.setStatusDesc(PesalinkStatus.INVALID_AMOUNT.getDescription());
                pesalinkValidatePaymentResponseDTO = new PesalinkValidatePaymentResponseDTO(pesalinkValidatePaymentRequestDTO, null, PesalinkStatus.INVALID_AMOUNT);

                updatePaymentService.sendInvalidAccountNotification(pmsInvoice.getCustomerPhoneNumber());
            } else {
                validatePayment.setStatus(PesalinkStatus.VALID.getStatus());
                pesalinkValidatePaymentResponseDTO = new PesalinkValidatePaymentResponseDTO(pesalinkValidatePaymentRequestDTO, validatePayment.getUuid().toString(), PesalinkStatus.VALID);
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
        if (ipnCallbackDTO == null || !StringUtils.hasText(signature) || !StringUtils.hasText(password)) return false;
        try {
            String requestJson = MAPPER.writeValueAsString(ipnCallbackDTO);
            byte[] signedObject = PMSUtils.signDataUsingHmacSha1(
                    password.getBytes(StandardCharsets.UTF_8), requestJson.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : signedObject) {
                sb.append(String.format("%02x", b));
            }
            log.debug("Calculated PesaLink callback signature for RRN {}", ipnCallbackDTO.rrn());
            return WebhookSignatureVerifier.constantTimeEquals(sb.toString(), signature.toLowerCase(java.util.Locale.ROOT));
        } catch (JsonProcessingException e) {
            log.error("Error Calculating verification signature, returning false", e);
            return false;
        }
    }

    /**
     * Validation requests carry their signature in the JSON body. The provider
     * signs the same ordered request fields with the signature field omitted.
     */
    public static boolean isValidationSignatureValid(PesalinkValidatePaymentRequestDTO request, String password) {
        if (request == null || !StringUtils.hasText(request.signature()) || !StringUtils.hasText(password)) return false;
        try {
            Map<String, Object> unsigned = new LinkedHashMap<>();
            unsigned.put("requestId", request.requestId());
            unsigned.put("billRef", request.billRef());
            unsigned.put("amount", request.amount());
            String requestJson = MAPPER.writeValueAsString(unsigned);
            byte[] signedObject = PMSUtils.signDataUsingHmacSha1(
                    password.getBytes(StandardCharsets.UTF_8), requestJson.getBytes(StandardCharsets.UTF_8));
            StringBuilder expected = new StringBuilder();
            for (byte value : signedObject) expected.append(String.format("%02x", value));
            return WebhookSignatureVerifier.constantTimeEquals(
                    expected.toString(), request.signature().toLowerCase(java.util.Locale.ROOT));
        } catch (JsonProcessingException e) {
            log.error("Error calculating PesaLink validation signature", e);
            return false;
        }
    }

    private boolean callbackMatchesInvoiceDestination(PMSInvoice invoice, IPNCallbackDTO callback) {
        if (invoice.getPaymentAccountId() == null || !StringUtils.hasText(callback.accountDst())
                || !StringUtils.hasText(callback.tillNumber())) {
            return false;
        }
        try {
            var account = accountDao.getAccountById(invoice.getPaymentAccountId());
            if (!account.isActive() || !account.isVerified()
                    || account.getChannel() != PaymentChannel.PESA_LINK
                    || account.getCreatedBy() != invoice.getPayToUserId()
                    || !accountCategoryMatchesInvoice(account.getCategory(), invoice)) {
                return false;
            }
            String expectedAccount = paramService.getParamByAccountIdAndType(account.getId(),
                    PaymentChannel.PESA_LINK.findProperty(PaymentPropertyKeys.BANK_ACCOUNT), invoice.getPropertyId());
            String expectedBankCode = paramService.getParamByAccountIdAndType(account.getId(),
                    PaymentChannel.PESA_LINK.findProperty(PaymentPropertyKeys.BANK_CODE), invoice.getPropertyId());
            return normalizedDestinationEquals(expectedAccount, callback.accountDst())
                    && normalizedDestinationEquals(expectedBankCode, callback.tillNumber());
        } catch (RuntimeException ex) {
            log.warn("PesaLink callback destination could not be verified for invoice {}", invoice.getRef());
            return false;
        }
    }

    private boolean accountCategoryMatchesInvoice(AccountCategory category, PMSInvoice invoice) {
        if (category == null) return false;
        AccountCategory expected = StringUtils.hasText(invoice.getSubscriptionPlanCode())
                ? AccountCategory.SLICKHOOD
                : switch (org.apache.commons.lang3.StringUtils.defaultString(invoice.getBillingType())) {
                    case "RENTAL" -> AccountCategory.LANDLORD;
                    case "SERVICE_CHARGE" -> AccountCategory.ESTATE_MANAGEMENT;
                    case "SALE" -> AccountCategory.PROPERTY_SALES;
                    case "COMMUNITY_FUND" -> AccountCategory.COMMUNITY_FUND;
                    case "SOKO", "SERVICE_MARKETPLACE" -> AccountCategory.MERCHANT;
                    default -> null;
                };
        return expected == null ? category != AccountCategory.SLICKHOOD : category == expected;
    }

    private boolean normalizedDestinationEquals(String expected, String actual) {
        return expected != null && actual != null
                && expected.replaceAll("[\\s-]", "").equals(actual.replaceAll("[\\s-]", ""));
    }
}
