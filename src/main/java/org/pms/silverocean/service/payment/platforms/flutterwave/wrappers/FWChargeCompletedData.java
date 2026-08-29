package org.pms.silverocean.service.payment.platforms.flutterwave.wrappers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL) @JsonIgnoreProperties(ignoreUnknown = true)
public record FWChargeCompletedData(long id,
                                    @JsonProperty("tx_ref") String txRef,
                                    @JsonProperty("flw_ref") String flwRef,
                                    @JsonProperty("device_fingerprint") String deviceFingerprint,
                                    double amount,
                                    String currency,
                                    @JsonProperty("charged_amount") double chargedAmount,
                                    @JsonProperty("app_fee") double appFee,
                                    @JsonProperty("merchant_fee") double merchantFee,
                                    @JsonProperty("processor_response") String processorResponse,
                                    @JsonProperty("auth_model") String authModel,
                                    String ip,
                                    String narration,
                                    String status,
                                    @JsonProperty("payment_type") String paymentType,
                                    @JsonProperty("created_at") String createdAt,
                                    @JsonProperty("account_id") long accountId,
                                    FWCustomer customer,
                                    FWCard card) {

    @Override
    public @NotNull String toString() {
        return "{" +
                "\"id\":" + id + ',' +
                "\"tx_ref\":\"" + txRef + "\"," +
                "\"flw_ref\":\"" + flwRef + "\"," +
                "\"device_fingerprint\":\"" + deviceFingerprint + "\"," +
                "\"amount\":" + amount + ',' +
                "\"currency\":\"" + currency + "\"," +
                "\"charged_amount\":" + chargedAmount + ',' +
                "\"app_fee\":" + appFee + ',' +
                "\"merchant_fee\":" + merchantFee + ',' +
                "\"processor_response\":\"" + processorResponse + "\"," +
                "\"auth_model\":\"" + authModel + "\"," +
                "\"ip\":\"" + ip + "\"," +
                "\"narration\":\"" + narration + "\"," +
                "\"status\":\"" + status + "\"," +
                "\"payment_type\":\"" + paymentType + "\"," +
                "\"created_at\":\"" + createdAt + "\"," +
                "\"account_id\":" + accountId + ',' +
                "\"customer\":" + (customer == null ? null : customer.toString()) + ',' +
                "\"card\":" + (card == null ? null : card.toString()) +
                '}';
    }
}
