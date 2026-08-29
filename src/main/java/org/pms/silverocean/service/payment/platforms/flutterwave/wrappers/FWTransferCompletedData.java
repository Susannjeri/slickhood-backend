package org.pms.silverocean.service.payment.platforms.flutterwave.wrappers;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FWTransferCompletedData(long id,
                                      @JsonProperty("account_number") String accountNumber,
                                      @JsonProperty("bank_name") String bankName,
                                      @JsonProperty("bank_code") String bankCode,
                                      String fullname,
                                      @JsonProperty("created_at") String createdAt,
                                      String currency,
                                      @JsonProperty("debit_currency") String debitCurrency,
                                      double amount,
                                      double fee,
                                      String status,
                                      String reference,
                                      Object meta,
                                      String narration,
                                      String approver,
                                      @JsonProperty("complete_message") String completeMessage,
                                      @JsonProperty("requires_approval") int requiresApproval,
                                      @JsonProperty("is_approved") int isApproved) {
    @Override
    public String toString() {
        return "{" +
                "\"id\":" + id + ',' +
                "\"account_number\":\"" + "hidden" + "\"," +
                "\"bank_name\":\"" + "hidden" + "\"," +
                "\"bank_code\":\"" + "hidden" + "\"," +
                "\"fullname\":\"" + fullname + "\"," +
                "\"created_at\":\"" + createdAt + "\"," +
                "\"currency\":\"" + currency + "\"," +
                "\"debit_currency\":\"" + debitCurrency + "\"," +
                "\"amount\":" + amount + ',' +
                "\"fee\":" + fee + ',' +
                "\"status\":\"" + status + "\"," +
                "\"reference\":\"" + reference + "\"," +
                "\"meta\":" + (meta == null ? null : "\"" + meta + "\"") + ',' +
                "\"narration\":\"" + narration + "\"," +
                "\"approver\":" + (approver == null ? null : "\"" + approver + "\"") + ',' +
                "\"complete_message\":\"" + completeMessage + "\"," +
                "\"requires_approval\":" + requiresApproval + ',' +
                "\"is_approved\":" + isApproved +
                '}';
    }

}
