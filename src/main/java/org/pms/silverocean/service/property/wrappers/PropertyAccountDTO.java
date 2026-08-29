package org.pms.silverocean.service.property.wrappers;

import org.pms.silverocean.service.payment.wrappers.PaymentChannel;

public record PropertyAccountDTO(String name, PaymentChannel channel, String account, String iconURL) {

}
