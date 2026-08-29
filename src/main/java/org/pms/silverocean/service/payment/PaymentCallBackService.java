package org.pms.silverocean.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.pms.silverocean.service.eventlogger.EventService;
import org.pms.silverocean.service.payment.platforms.flutterwave.FWVerifyTransaction;
import org.springframework.stereotype.Service;

@Service
public class PaymentCallBackService {
    private final PaymentDao paymentDao;
    private final EventService eventService;

    private final FWVerifyTransaction fwVerifyTransaction;

    private final UpdatePaymentService updatePaymentService;




    public PaymentCallBackService(PaymentDao paymentDao, EventService eventService, FWVerifyTransaction fwVerifyTransaction, UpdatePaymentService updatePaymentService) {
        this.paymentDao = paymentDao;
        this.eventService = eventService;
        this.fwVerifyTransaction = fwVerifyTransaction;
        this.updatePaymentService = updatePaymentService;

    }

//    public void stkCallBack(STKCallbackResponse stkCallbackResponse, String sourceIp) {
//        HttpEvent event = new HttpEvent();
//        event.setEventType(STKCallbackResponse.class.getSimpleName());
//        event.setEvent(stkCallbackResponse.toString().getBytes());
//
//        paymentDao.findPaymentByThirdPartyID(stkCallbackResponse.body().stkCallback().checkoutRequestID())
//                .ifPresent(pmsPayment -> {
//                    pmsPayment.setStatus(String.valueOf(stkCallbackResponse.body().stkCallback().resultCode()));
//                    updatePaymentService.setInvoiceTransactionStatusByBillRefNumber(pmsPayment.getBillReference(), false);
//                    pmsPayment.setStatusDesc(stkCallbackResponse.body().stkCallback().resultDesc());
//                    pmsPayment.setSourceIp(sourceIp);
//                    event.setTId(pmsPayment.getId());
//                    Optional.ofNullable(stkCallbackResponse.body().stkCallback().callbackMetadata())
//                            .ifPresent(stkCallbackMetadata -> stkCallbackMetadata.items().forEach(item -> {
//                                if (item.name().equals(MPESA_RECEIPT_NUMBER)) {
//                                    pmsPayment.setThirdPartyTransId(item.value().toString());
//                                    //set invoice to paid
//                                    updatePaymentService.setInvoiceToPaid(pmsPayment.getBillReference(), pmsPayment.getThirdPartyTransId(), pmsPayment.getAmount());
//                                }
//                            }));
//                    paymentDao.savePMSPayment(pmsPayment);
//                });
//        eventService.saveEvent(event);
//    }

//    public MPesaPaymentResponseDTO confirmPayment(MPesaPaymentDTO mpesaPaymentDTO, Long userId, String sourceIp) {
//        PMSPayment payment = new PMSPayment(mpesaPaymentDTO, TransactionCategory.PAYMENT_PROCESSED);
//        payment.setSourceIp(sourceIp);
//        payment.setPayToUserId(Optional.ofNullable(userId).orElse(0L));
//        payment.setStatus(MPesaResultCodes.COMPLETED.getCode());
//        payment.setStatusDesc(MPesaResultCodes.COMPLETED.getDesc());
//        updatePaymentService.getInvoicePayToIDUsingInvoiceRef(mpesaPaymentDTO.billRefNumber())
//                        .ifPresent(invoice -> {
//                            payment.setPayToUserId(invoice.getPayToUserId());
//                            updatePaymentService.setInvoiceToPaid(invoice, payment.getThirdPartyTransId(), payment.getAmount());
//                        });
//        paymentDao.savePMSPayment(payment);
//        eventService.saveEvent(mpesaPaymentDTO, payment.getId());
//        return new MPesaPaymentResponseDTO(MPesaResultCodes.VALID);
//    }
//
//    public MPesaPaymentResponseDTO validatePayment(MPesaPaymentDTO mpesaPaymentDTO, Long userId, String sourceIp) {
//        PMSPayment validatePayment = new PMSPayment(mpesaPaymentDTO, TransactionCategory.PAYMENT_VALIDATION);
//        validatePayment.setSourceIp(sourceIp);
//        validatePayment.setPayToUserId(Optional.ofNullable(userId).orElse(0L));
//
//        Optional<PMSInvoice> paymentInvoice = updatePaymentService.getInvoicePayToIDUsingInvoiceRef(mpesaPaymentDTO.billRefNumber());
//
//        MPesaPaymentResponseDTO mPesaPaymentResponseDTO;
//
//        if (paymentInvoice.isPresent() && paymentInvoice.get().isActive()) {
//            PMSInvoice pmsInvoice = paymentInvoice.get();
//            validatePayment.setPayToUserId(pmsInvoice.getPayToUserId());
//            if (pmsInvoice.isPaid() || Double.parseDouble(mpesaPaymentDTO.transAmount()) > pmsInvoice.getPendingAmount()) {
//                validatePayment.setStatus(MPesaResultCodes.INVALID_AMOUNT.getCode());
//                validatePayment.setStatusDesc(MPesaResultCodes.INVALID_AMOUNT.getDesc());
//                mPesaPaymentResponseDTO = new MPesaPaymentResponseDTO(MPesaResultCodes.INVALID_AMOUNT);
//
//                updatePaymentService.sendInvalidAccountNotification(pmsInvoice.getCustomerPhoneNumber());
//            } else {
//                validatePayment.setStatus(MPesaResultCodes.VALID.getCode());
//                validatePayment.setStatusDesc(MPesaResultCodes.VALID.getDesc());
//                mPesaPaymentResponseDTO = new MPesaPaymentResponseDTO(MPesaResultCodes.VALID);
//
//                pmsInvoice.setTransactionInProgress(true);
//                updatePaymentService.updateInvoice(pmsInvoice);
//            }
//        } else {
//            validatePayment.setStatus(MPesaResultCodes.INVALID_ACCOUNT_NUMBER.getCode());
//            validatePayment.setStatusDesc(MPesaResultCodes.INVALID_ACCOUNT_NUMBER.getDesc());
//            mPesaPaymentResponseDTO = new MPesaPaymentResponseDTO(MPesaResultCodes.INVALID_ACCOUNT_NUMBER);
//        }
//
//        paymentDao.savePMSPayment(validatePayment);
//
//        eventService.cacheEvent(mPesaPaymentResponseDTO, validatePayment.getId());
//        eventService.cacheEvent(mpesaPaymentDTO, validatePayment.getId());
//
//        eventService.flushByTId(validatePayment.getId());
//        return mPesaPaymentResponseDTO;
//    }


}
