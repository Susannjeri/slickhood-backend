package org.pms.silverocean.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.utils.OutputStreamErrorHandler;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.payment.PaymentRequestException;
import org.pms.silverocean.service.payment.PaymentService;
import org.pms.silverocean.service.payment.PaymentReceiptService;
import org.pms.silverocean.service.payment.invoice.InvoiceService;
import org.pms.silverocean.service.payment.invoice.wrappers.InvoiceDTO;
import org.pms.silverocean.service.payment.wrappers.ManualPaymentDTO;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentChannelDTO;
import org.pms.silverocean.service.payment.wrappers.PaymentDTO;
import org.pms.silverocean.service.payment.wrappers.PaymentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/payment")
@Slf4j
public class PaymentController extends OutputStreamErrorHandler {
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final PaymentReceiptService paymentReceiptService;

    public PaymentController(InvoiceService invoiceService, I18NService i18NService, PaymentService paymentService,
                             PaymentReceiptService paymentReceiptService) {
        super(i18NService);
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
        this.paymentReceiptService = paymentReceiptService;
    }

    @GetMapping(value = "/view/receipt")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_PAYMENT_LIST)")
    public void viewReceipt(@RequestParam long paymentId, HttpServletResponse response) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            paymentReceiptService.render(paymentId, buffer);
            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename("receipt_" + paymentId + ".pdf").build().toString());
            buffer.writeTo(response.getOutputStream());
            response.getOutputStream().flush();
        } catch (PMSCustomException exception) {
            writeErrorResponse(response, exception.getResponseCode());
        } catch (IOException exception) {
            writeErrorResponse(response, ResponseCode.GENERAL_FAILURE);
        }
    }

    @GetMapping("/channel/type")
    public ResponseEntity<ResponseDTO> getPaymentType() {
        Set<PaymentChannelDTO> paymentTypes = invoiceService.getPaymentTypes();
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.PAYMENT_CHANNEL_LIST.getCode(), i18NService.getLocalizedMessage(ResponseCode.PAYMENT_CHANNEL_LIST), paymentTypes));
    }

    @GetMapping(value = "/view/invoice")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_INVOICE_PDF)")
    public void viewInvoicePDF(@RequestParam long invoiceId, HttpServletResponse response) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            invoiceService.viewInvoicePDF(invoiceId, buffer);
            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.builder("inline") // "attachment" for download
                            .filename("invoice_" + invoiceId + ".pdf")
                            .build().toString());
            buffer.writeTo(response.getOutputStream());
            response.getOutputStream().flush();
        } catch (PMSCustomException ex) {
            writeErrorResponse(response, ex.getResponseCode());
        } catch (IOException ex) {
            writeErrorResponse(response, ResponseCode.GENERAL_FAILURE);
        }
    }

    @GetMapping("/invoice/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_INVOICE_LIST)")
    public ResponseEntity<ResponseDTO> listInvoice(Pageable pageable, @RequestParam Optional<Long> tenantId, @RequestParam Optional<Long> landlordId, @RequestParam Optional<Long> propertyId, @RequestParam Optional<Long> unitId) {
        Page<InvoiceDTO> invoiceList = invoiceService.getInvoiceList(pageable, tenantId.orElse(null), landlordId.orElse(null), propertyId.orElse(null), unitId.orElse(null));
        ResponseDTO responseDTO = new ResponseDTO(true, ResponseCode.INVOICE_LIST.getCode(), i18NService.getLocalizedMessage(ResponseCode.INVOICE_LIST.getDescription()), invoiceList.getContent());
        responseDTO.setTotalElements(invoiceList.getTotalElements());
        responseDTO.setTotalPages(invoiceList.getTotalPages());
        responseDTO.setSize(invoiceList.getSize());
        return ResponseEntity.ok(responseDTO);
    }

    @GetMapping("/invoice/payment-account")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_INVOICE_LIST)")
    public ResponseEntity<ResponseDTO> invoicePaymentAccount(@RequestParam long invoiceId){
        return ResponseEntity.ok(new ResponseDTO(true,ResponseCode.ACCOUNT_LIST.getCode(),i18NService.getLocalizedMessage(ResponseCode.ACCOUNT_LIST),invoiceService.getInvoicePaymentAccount(invoiceId)));
    }

    @GetMapping("/init")
    public ResponseEntity<ResponseDTO> initPayment(@RequestParam String invoiceRef, @RequestParam PaymentChannel paymentChannel, @RequestParam(required = false) Optional<String> phoneNumber,
                                                   @RequestParam long accountId) {
        try {
            log.info("Invoice {} , channel {} , phone {} ", invoiceRef, paymentChannel.getName(), phoneNumber.orElse("null"));
            PaymentResponse paymentResponse = invoiceService.initInvoicePayment(invoiceRef, paymentChannel, phoneNumber.orElse(null), accountId);
            if (paymentResponse.success()) {
                return paymentResponse.body() == null ? ResponseEntity.ok(new ResponseDTO(true, paymentResponse.responseCode().getCode(), i18NService.getLocalizedMessage(paymentResponse.responseCode())))
                        : ResponseEntity.ok(new ResponseDTO(true, paymentResponse.responseCode().getCode(), i18NService.getLocalizedMessage(paymentResponse.responseCode()), paymentResponse.body()));
            }
            return
                    ResponseEntity.status(HttpStatus.CONFLICT).body(new ResponseDTO(false, paymentResponse.responseCode().getCode(), i18NService.getLocalizedMessage(paymentResponse.responseCode())));
        } catch (PaymentRequestException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ResponseDTO(false, ResponseCode.PAYMENT_INITIALIZATION_FAILED.getCode(), i18NService.getLocalizedMessage(e.getMessage())));
        }
    }

    @PostMapping("/record")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).RECORD_MANUAL_PAYMENT)")
    public ResponseEntity<ResponseDTO> recordManualPayment(@RequestBody @Valid ManualPaymentDTO manualPaymentDTO) {
        paymentService.recordManualPayment(manualPaymentDTO);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS)));
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_PAYMENT_LIST)")
    public ResponseEntity<ResponseDTO> listPayment(Pageable pageable, @RequestParam Optional<String> filter) {
        Page<PaymentDTO> payments = paymentService.getPayments(pageable, filter.orElse(null));
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.LIST_OF_PAYMENTS.getCode(), i18NService.getLocalizedMessage(ResponseCode.LIST_OF_PAYMENTS),
                payments.toList(),
                payments.getTotalPages(), payments.getTotalElements(), payments.getSize()));

    }

    @GetMapping("/fw/update")
    public ResponseEntity<ResponseDTO> updateFlutterWavePayment(@RequestParam String status, @RequestParam("tx_ref") long paymentId, @RequestParam("transaction_id") String thirdPartyId) {
        paymentService.updateFlutterWavePayment(status, paymentId, thirdPartyId);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.FW_PAYMENT_UPDATED.getCode(), i18NService.getLocalizedMessage(ResponseCode.FW_PAYMENT_UPDATED)));
    }

}
