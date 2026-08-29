package org.pms.silverocean.service.payment.platforms.pesalink.wrappers;

import org.pms.silverocean.service.payment.PaymentCallBackResponse;

public record IPNCallbackResponseDTO(String rrn, String status) implements PaymentCallBackResponse {
}
