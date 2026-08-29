package org.pms.silverocean.service.payment.platforms.mpesa.wrappers;

public record StkErrorResponse(String requestId,
                               String errorCode,
                               String errorMessage) {
}
