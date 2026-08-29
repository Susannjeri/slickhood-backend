package org.pms.silverocean.service.payment.platforms.pesalink.wrappers;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IPNCallbackDTO(String sender,
                             String recipient,
                             @JsonInclude(JsonInclude.Include.NON_NULL)
                             String bankSrc,
                             String bankDst,
                             String accountSrc,
                             String accountDst,
                             String rrn,
                             BigDecimal amount,
                             String paymentReason,
                             String status,
                             String phoneSrc,
                             String phoneDst,

                             @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
                             LocalDateTime date,

                             @JsonProperty("Transaction_type")
                             @SerializedName("Transaction_type")
                             @JsonInclude(JsonInclude.Include.NON_NULL)
                             String transactionType,

                             @JsonProperty("Original_Amount")
                             @SerializedName("Original_Amount")
                             @JsonInclude(JsonInclude.Include.NON_NULL)
                             String originalAmount,

                             @JsonProperty("Bill_reference")
                             @SerializedName("Bill_reference")
                             @JsonInclude(JsonInclude.Include.NON_NULL)
                             String billReference,
                             @JsonInclude(JsonInclude.Include.NON_NULL)
                             String tillNumber) {
}
