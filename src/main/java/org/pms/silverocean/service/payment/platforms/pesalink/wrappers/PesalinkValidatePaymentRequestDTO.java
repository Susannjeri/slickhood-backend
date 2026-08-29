package org.pms.silverocean.service.payment.platforms.pesalink.wrappers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PesalinkValidatePaymentRequestDTO(String requestId, String signature, String billRef, BigDecimal amount) {
}
