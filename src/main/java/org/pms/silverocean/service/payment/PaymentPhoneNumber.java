package org.pms.silverocean.service.payment;

import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;

/** Normalizes payer-supplied Kenyan mobile numbers for STK payment rails. */
public final class PaymentPhoneNumber {
    private PaymentPhoneNumber() {}

    public static String normalizeKenyanMsisdn(String value) {
        String digits = StringUtils.defaultString(value).replaceAll("\\D", "");
        if (digits.matches("0[17]\\d{8}")) digits = "254" + digits.substring(1);
        else if (digits.matches("[17]\\d{8}")) digits = "254" + digits;
        if (!digits.matches("254[17]\\d{8}")) {
            throw new PaymentRequestException(ResponseCode.INVALID_PHONENUMBER);
        }
        return digits;
    }
}
