package org.pms.silverocean.service.payment;

import lombok.Getter;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.PMSCustomException;

@Getter
public class PaymentRequestException  extends PMSCustomException {
    public PaymentRequestException(ResponseCode responseCode) {
        super(responseCode);
    }

    public PaymentRequestException(ResponseCode message, Throwable cause) {
        super(message);
        super.initCause(cause);
    }
}
