package org.pms.silverocean.service.payment;


import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentResponse;

public abstract class PaymentPlatform {
    protected final UpdatePaymentService updatePaymentService;

    protected PaymentPlatform(UpdatePaymentService updatePaymentService) {
        this.updatePaymentService = updatePaymentService;
    }

    public PaymentResponse processPayment(PMSInvoice pmsInvoice, String phoneNumber, long accountId) throws PaymentRequestException {
        pmsInvoice.setTransactionInProgress(true);
        updatePaymentService.updateInvoice(pmsInvoice);
        PaymentResponse response = null;
        try {

            response = initPayment(pmsInvoice, phoneNumber, accountId);
        } finally {
            if (response == null || !response.success()) {
                pmsInvoice.setTransactionInProgress(false);
                updatePaymentService.updateInvoice(pmsInvoice);
            }
        }
        return response;
    }

    protected abstract PaymentResponse initPayment(PMSInvoice pmsInvoice, long accountId) throws PaymentRequestException;

    /** Providers that collect from a phone can override this without coupling the base class to them. */
    protected PaymentResponse initPayment(PMSInvoice pmsInvoice, String phoneNumber, long accountId)
            throws PaymentRequestException {
        return initPayment(pmsInvoice, accountId);
    }

    protected abstract boolean isActive();

    protected abstract PaymentChannel channelType();

    protected abstract String channelIcon();

    public abstract PaymentCallBackResponse handleCallBack(PaymentCallBackRequest paymentCallBackRequest);
}
