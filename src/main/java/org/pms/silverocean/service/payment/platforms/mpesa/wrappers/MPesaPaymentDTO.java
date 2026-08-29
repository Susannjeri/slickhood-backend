package org.pms.silverocean.service.payment.platforms.mpesa.wrappers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 * {
 * "TransactionCategory": "Pay Bill",
 * "TransID":"RKTQDM7W6S",
 * "TransTime":"20191122063845",
 * "TransAmount":"10"
 * "BusinessShortCode": "600638",
 * "BillRefNumber":"invoice008",
 * "InvoiceNumber":"",
 * "OrgAccountBalance":""
 * "ThirdPartyTransID": "",
 * "MSISDN":"25470****149",
 * "FirstName":"John",
 * "MiddleName":""
 * "LastName":"Doe"
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MPesaPaymentDTO(
        @JsonProperty("TransactionType")
        String transactionType,
        @JsonProperty("TransID")
        String transId,
        @JsonProperty("TransTime")
        String transTime,
        @JsonProperty("TransAmount")
        String transAmount,
        @JsonProperty("BusinessShortCode")
        String businessShortCode,
        @JsonProperty("BillRefNumber")
        String billRefNumber,
        @JsonProperty("InvoiceNumber")
        String invoiceNumber,
        @JsonProperty("OrgAccountBalance")
        String orgAccountBalance,
        @JsonProperty("ThirdPartyTransId")
        String thirdPartyTransId,
        @JsonProperty("MSISDN")
        String msisdn,
        @JsonProperty("FirstName")
        String firstName,
        @JsonProperty("MiddleName")
        String middleName,
        @JsonProperty("LastName")
        String lastName) {

    public String toString() {
        return "{\"TransactionCategory\": \"" + transactionType + "\", " +
                "\"TransID\": \"" + transId + "\", " +
                "\"TransTime\": \"" + transTime + "\", " +
                "\"TransAmount\": \"" + transAmount + "\", " +
                "\"BusinessShortCode\": \"" + businessShortCode + "\", " +
                "\"BillRefNumber\": \"" + billRefNumber + "\", " +
                "\"InvoiceNumber\": \"" + invoiceNumber + "\", " +
                "\"OrgAccountBalance\": \"" + orgAccountBalance + "\", " +
                "\"ThirdPartyTransId\": \"" + thirdPartyTransId + "\", " +
                "\"MSISDN\": \"" + msisdn + "\", " +
                "\"FirstName\": \"" + firstName + "\", " +
                "\"MiddleName\": \"" + middleName + "\", " +
                "\"LastName\": \"" + lastName + "\"}";
    }
}
