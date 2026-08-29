package org.pms.silverocean.service.payment.platforms.pesalink.wrappers;

public record PesaLinkErrorResponse(
                                    String requestId,

                                    String timestamp,

                                    String errorCode,

                                    String errorMessage) {
}
