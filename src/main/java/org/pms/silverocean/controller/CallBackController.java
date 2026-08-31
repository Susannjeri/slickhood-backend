package org.pms.silverocean.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.service.notification.sms.SMSService;
import org.pms.silverocean.service.notification.sms.africastalking.wrappers.ATSMSFailureReason;
import org.pms.silverocean.service.notification.sms.africastalking.wrappers.ATSMSNetworkCode;
import org.pms.silverocean.service.notification.sms.africastalking.wrappers.ATSMSStatus;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.callback.WAWebHook;
import org.pms.silverocean.service.payment.PaymentCallBackResponse;
import org.pms.silverocean.service.payment.PaymentPlatformFactory;
import org.pms.silverocean.service.payment.WebhookSignatureVerifier;
import org.pms.silverocean.service.payment.platforms.flutterwave.wrappers.FWCallbackDTO;
import org.pms.silverocean.service.payment.platforms.flutterwave.wrappers.FWWebhookDTO;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.MPesaBankCallbackDTO;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.MPesaPaymentDTO;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.MpesaCallBackType;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.MpesaCallbackDTO;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.STKCallbackResponse;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.IPNCallbackDTO;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.PesalinkCallbackDTO;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.PesalinkCallbackType;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.PesalinkValidatePaymentRequestDTO;
import org.pms.silverocean.service.payment.platforms.paystack.PaystackCallbackDTO;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/callback") @Slf4j
public class CallBackController {
    private final PaymentPlatformFactory paymentPlatformFactory;
    private final SMSService smsService;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, (JsonDeserializer<LocalDateTime>)
                    (json, type, ctx) -> LocalDateTime.parse(json.getAsString(), formatter))
            .create();
    @Value("${whatsapp.verifyToken}")
    private  String whatsAppVerifyToken;
    @Value("${whatsapp.appSecret:}")
    private String whatsAppAppSecret;
    @Value("${payment.flutterwave.webhook-secret:}")
    private String flutterwaveWebhookSecret;
    @Value("${payment.mpesa.callback-token:}")
    private String mpesaCallbackToken;
    @Value("${payment.paystack.secret-key:}")
    private String paystackSecretKey;

    public CallBackController(PaymentPlatformFactory paymentPlatformFactory, SMSService smsService) {
        this.paymentPlatformFactory = paymentPlatformFactory;
        this.smsService = smsService;
    }

    @PostMapping("/stk")
    public ResponseEntity<PaymentCallBackResponse> receiveMPesaSTKCallback(HttpServletRequest request,
                                                                           @RequestParam String token,
                                                                           @RequestBody STKCallbackResponse stkCallbackResponse) {
        if (!isValidMpesaToken(token, request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        PaymentCallBackResponse paymentCallBackResponse = paymentPlatformFactory.getPlatform(PaymentChannel.MPESA)
                .handleCallBack(new MpesaCallbackDTO(null, stkCallbackResponse, null, PMSUtils.getIPAddress(request), MpesaCallBackType.STK));
        return ResponseEntity.ok(paymentCallBackResponse);
    }

    @PostMapping("/validation")
    public ResponseEntity<PaymentCallBackResponse> receiveMPesaValidationReq(HttpServletRequest request,
                                                                              @RequestParam String token,
                                                                              @RequestParam(required = false) Long userId,
                                                                              @RequestBody MPesaPaymentDTO mPesaPaymentDTO) {
        if (!isValidMpesaToken(token, request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        PaymentCallBackResponse paymentCallBackResponse = paymentPlatformFactory.getPlatform(PaymentChannel.MPESA)
                .handleCallBack(new MpesaCallbackDTO(mPesaPaymentDTO, null, userId, PMSUtils.getIPAddress(request), MpesaCallBackType.VALIDATION));
        return ResponseEntity.ok(paymentCallBackResponse);
    }

    @PostMapping("/confirmation")
    public ResponseEntity<PaymentCallBackResponse> receiveMPesaConfirmationReq(HttpServletRequest request,
                                                                                @RequestParam String token,
                                                                                @RequestParam(required = false) Long userId,
                                                                                @RequestBody MPesaPaymentDTO mPesaPaymentDTO) {
        if (!isValidMpesaToken(token, request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        PaymentCallBackResponse paymentCallBackResponse = paymentPlatformFactory.getPlatform(PaymentChannel.MPESA)
                .handleCallBack(new MpesaCallbackDTO(mPesaPaymentDTO, null, userId, PMSUtils.getIPAddress(request), MpesaCallBackType.CONFIRMATION));
        return ResponseEntity.ok(paymentCallBackResponse);
    }

    @PostMapping("/mpesa/paybill/validation")
    public ResponseEntity<PaymentCallBackResponse> receiveMPesaPaybillValidation(
            HttpServletRequest request, @RequestParam String token,
            @RequestParam(required = false) Long userId,
            @RequestBody MPesaPaymentDTO payment) {
        return receiveMPesaValidationReq(request, token, userId, payment);
    }

    @PostMapping("/mpesa/paybill/confirmation")
    public ResponseEntity<PaymentCallBackResponse> receiveMPesaPaybillConfirmation(
            HttpServletRequest request, @RequestParam String token,
            @RequestParam(required = false) Long userId,
            @RequestBody MPesaPaymentDTO payment) {
        return receiveMPesaConfirmationReq(request, token, userId, payment);
    }

    @PostMapping("/mpesa/bank/confirmation")
    public ResponseEntity<PaymentCallBackResponse> receiveMPesaBankConfirmation(
            HttpServletRequest request, @RequestParam String token,
            @RequestParam(required = false) Long userId,
            @Valid @RequestBody MPesaBankCallbackDTO payment) {
        if (!isValidMpesaToken(token, request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        PaymentCallBackResponse response = paymentPlatformFactory.getPlatform(PaymentChannel.MPESA_BANK)
                .handleCallBack(new MpesaCallbackDTO(payment.toMPesaPaymentDTO(), null, userId,
                        PMSUtils.getIPAddress(request), MpesaCallBackType.CONFIRMATION));
        return ResponseEntity.ok(response);
    }

    @RequestMapping("/sms")
    public ResponseEntity<String> receiveATSMSDeliveryCallback(HttpServletRequest request, @RequestParam Optional<String> id, @RequestParam Optional<ATSMSStatus> status,
                                                               @RequestParam Optional<String> phoneNumber, @RequestParam Optional<ATSMSNetworkCode> networkCode,
                                                               @RequestParam Optional<ATSMSFailureReason> failureReason, @RequestParam Optional<Integer> retryCount) {
        smsService.receiveATCallback(PMSUtils.getIPAddress(request), id.orElse(null), status.orElse(null), phoneNumber.orElse(null), networkCode.orElse(null), failureReason.orElse(null));
        return ResponseEntity.ok("Callback received");
    }

    @RequestMapping("/text/sms")
    public ResponseEntity<String> receiveTextSMSDeliveryCallback(HttpServletRequest request) {
        handleUnknownRequestStructure(request);

        return ResponseEntity.ok("Callback received");
    }

    @PostMapping("/fw/payments")
    public ResponseEntity<PaymentCallBackResponse> receiveFlutterWaveCallback(
            HttpServletRequest request,
            @RequestHeader(value = "flutterwave-signature", required = false) String signature,
            @RequestHeader(value = "verif-hash", required = false) String legacySignature,
            @RequestBody String rawBody) {
        if (!WebhookSignatureVerifier.isFlutterwaveSignatureValid(rawBody, signature, legacySignature, flutterwaveWebhookSecret)) {
            log.warn("Rejected unauthenticated Flutterwave callback from {}", PMSUtils.getIPAddress(request));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        FWWebhookDTO<?> body = gson.fromJson(rawBody, FWWebhookDTO.class);
        log.info("Received authenticated Flutterwave event {}", body.event());
        PaymentCallBackResponse paymentCallBackResponse = paymentPlatformFactory.getPlatform(PaymentChannel.FLUTTER_WAVE)
                .handleCallBack(new FWCallbackDTO<>(body,  PMSUtils.getIPAddress(request)));
        return ResponseEntity.ok(paymentCallBackResponse);
    }

    private boolean isValidMpesaToken(String token, HttpServletRequest request) {
        boolean valid = WebhookSignatureVerifier.isSharedTokenValid(token, mpesaCallbackToken);
        if (!valid) {
            log.warn("Rejected unauthenticated M-Pesa callback from {}", PMSUtils.getIPAddress(request));
        }
        return valid;
    }

    @PostMapping("/paystack")
    public ResponseEntity<PaymentCallBackResponse> receivePaystackCallback(
            HttpServletRequest request,
            @RequestHeader("x-paystack-signature") String signature,
            @RequestBody String rawBody) {
        if (!WebhookSignatureVerifier.isPaystackSignatureValid(rawBody, signature, paystackSecretKey)) {
            log.warn("Rejected unauthenticated Paystack callback from {}", PMSUtils.getIPAddress(request));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        PaymentCallBackResponse response = paymentPlatformFactory.getPlatform(PaymentChannel.PAYSTACK)
                .handleCallBack(new PaystackCallbackDTO(rawBody, PMSUtils.getIPAddress(request)));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/whatsapp")
    public String verifyWebhook(@RequestParam("hub.verify_token") String token,
                                @RequestParam("hub.challenge") String challenge) {
        // Match this token with the one you set in the Meta Dashboard
        if (whatsAppVerifyToken.equals(token)) {
            return challenge;
        }
        return "Verification Failed";
    }

    @PostMapping("/whatsapp")
    public ResponseEntity<Void> handleIncomingEvents(
            HttpServletRequest request,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String rawBody) {
        if (!WebhookSignatureVerifier.isMetaSignatureValid(rawBody, signature, whatsAppAppSecret)) {
            log.warn("Rejected unauthenticated WhatsApp callback from {}", PMSUtils.getIPAddress(request));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        WAWebHook whatsAppCallback = gson.fromJson(rawBody, WAWebHook.class);
        smsService.receiveWhatsAppCallback(whatsAppCallback, PMSUtils.getIPAddress(request));
        return ResponseEntity.ok().build();
    }

    private void handleUnknownRequestStructure(HttpServletRequest request) {
        String parameters = request.getParameterMap().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));


        String headers = Collections.list(request.getHeaderNames()).stream()
                .map(headerName -> headerName + ": " + Collections.list(request.getHeaders(headerName)))
                .collect(Collectors.joining(", ", "{", "}"));


        String body = "";
        try {
            body = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (Exception e) {
            body = "Could not read body: " + e.getMessage();
        }

        log.info("SMS Callback Received Details:");
        log.info("URI: {}", request.getRequestURI());
        log.info("Query Params: {}", parameters);
        log.info("Headers: {}", headers);
        log.info("Body: {}", body);
    }

    @PostMapping("/pesalink/ipn")
    public ResponseEntity<PaymentCallBackResponse> pesalinkPaymentNotification(HttpServletRequest request, @RequestBody String ipnCallbackPayload, @RequestHeader("X-Signature") String signature) {
        log.debug("Received Pesalink IPN callback payload: {}", ipnCallbackPayload);
        IPNCallbackDTO ipnCallbackDTO = gson.fromJson(ipnCallbackPayload, IPNCallbackDTO.class);
        PaymentCallBackResponse paymentCallBackResponse = paymentPlatformFactory.getPlatform(PaymentChannel.PESA_LINK)
                .handleCallBack(new PesalinkCallbackDTO(ipnCallbackDTO, null,  PMSUtils.getIPAddress(request), signature, PesalinkCallbackType.IPN));
        return ResponseEntity.ok(paymentCallBackResponse);
    }

    @PostMapping("/pesalink/validate")
    public ResponseEntity<PaymentCallBackResponse> validatePesalinkPayment(HttpServletRequest request, @RequestBody PesalinkValidatePaymentRequestDTO validateRequest) {
        PaymentCallBackResponse paymentCallBackResponse = paymentPlatformFactory.getPlatform(PaymentChannel.PESA_LINK)
                .handleCallBack(new PesalinkCallbackDTO(null, validateRequest,  PMSUtils.getIPAddress(request), null, PesalinkCallbackType.VALIDATION));
        return ResponseEntity.ok(paymentCallBackResponse);
    }
}
