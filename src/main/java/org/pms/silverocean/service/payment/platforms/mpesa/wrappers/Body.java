package org.pms.silverocean.service.payment.platforms.mpesa.wrappers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
@JsonIgnoreProperties(ignoreUnknown = true)
public record Body(@JsonProperty("stkCallback") STKCallback stkCallback) {
}
