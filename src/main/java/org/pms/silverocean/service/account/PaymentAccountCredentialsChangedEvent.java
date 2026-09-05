package org.pms.silverocean.service.account;

import org.pms.silverocean.service.payment.wrappers.PaymentChannel;

public record PaymentAccountCredentialsChangedEvent(long accountId, PaymentChannel channel) {
}
