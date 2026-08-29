package org.pms.silverocean.service.payment.platforms;

import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.service.payment.PaymentCallBackRequest;
import org.pms.silverocean.service.payment.PaymentCallBackResponse;
import org.pms.silverocean.service.payment.PaymentPlatform;
import org.pms.silverocean.service.payment.UpdatePaymentService;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentResponse;
import org.springframework.stereotype.Component;

@Component("Airtel Money")
public class AirtelMoneyPlatform extends PaymentPlatform {

    protected AirtelMoneyPlatform(UpdatePaymentService updatePaymentService) {
        super(updatePaymentService);
    }

    @Override
    public PaymentResponse initPayment(PMSInvoice pmsInvoice, long accountId) {
        //TODO save payment details to db
        return new PaymentResponse(true, ResponseCode.PAYMENT_INITIALIZED);
    }

    @Override
    protected boolean isActive() {
        return false;
    }

    @Override
    protected PaymentChannel channelType() {
        return PaymentChannel.AIRTEL_MONEY;
    }

    @Override
    protected String channelIcon() {
        return "";
    }

    @Override
    public PaymentCallBackResponse handleCallBack(PaymentCallBackRequest paymentCallBackRequest) {
        return null;
    }


}
