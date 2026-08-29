package org.pms.silverocean.service.payment.platforms.mpesa.wrappers;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.pms.silverocean.database.pms.entities.PMSInvoice;

/**
 * Request Body sent to Mpesa Daraja platform on:
 * https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest
 * <p>
 * <p>
 * {
 * "BusinessShortCode": "174379",
 * "Password": "MTc0Mzc5YmZiMjc5ZjlhYTliZGJjZjE1OGU5N2RkNzFhNDY3Y2QyZTBjODkzMDU5YjEwZjc4ZTZiNzJhZGExZWQyYzkxOTIwMTYwMjE2MTY1NjI3",
 * "Timestamp":"20160216165627",
 * "TransactionType": "CustomerPayBillOnline",
 * "Amount": "1",
 * "PartyA":"254708374149",
 * "PartyB":"174379",
 * "PhoneNumber":"254708374149",
 * "CallBackURL": "https://mydomain.com/pat",
 * "AccountReference":"Test",
 * "TransactionDesc":"Test"
 * }
 */
public record MPesaSTKPushRequest(@JsonProperty("BusinessShortCode") String businessShortCode,
                                  @JsonProperty("Password") String password,
                                  @JsonProperty("Timestamp") String timestamp,
                                  @JsonProperty("TransactionType") String transactionType,
                                  @JsonProperty("Amount") String amount,
                                  @JsonProperty("PartyA") String partyA,
                                  @JsonProperty("PartyB") String partyB,
                                  @JsonProperty("PhoneNumber") String phoneNumber,
                                  @JsonProperty("CallBackURL") String callbackURL,
                                  @JsonProperty("AccountReference") String accountReference,
                                  @JsonProperty("TransactionDesc") String transactionDesc) {
    public MPesaSTKPushRequest(MPesaStaticMembers mpesaStaticMembers, PMSInvoice pmsInvoice, String msisdn) {
        this(mpesaStaticMembers.businessShortCode(),
                mpesaStaticMembers.password(),
                mpesaStaticMembers.timestamp(),
                "CustomerPayBillOnline",
                String.valueOf((int) Math.ceil(pmsInvoice.getPendingAmount())),
                msisdn,
                mpesaStaticMembers.businessShortCode(),
                msisdn,
                mpesaStaticMembers.callbackURL(),
                pmsInvoice.getRef(),
                new String(pmsInvoice.getDescription()));
    }

    @Override
    public String toString() {
        return "{\"BusinessShortCode\": \"" + businessShortCode + "\", " +
                "\"Password\": \"hidden\", " +
                "\"Timestamp\": \"" + timestamp + "\", " +
                "\"TransactionType\": \"" + transactionType + "\", " +
                "\"Amount\": \"" + amount + "\", " +
                "\"PartyA\": \"" + partyA + "\", " +
                "\"PartyB\": \"" + partyB + "\", " +
                "\"PhoneNumber\": \"" + phoneNumber + "\", " +
                "\"CallBackURL\": \"hidden\", " +
                "\"AccountReference\": \"" + accountReference + "\", " +
                "\"TransactionDesc\": \"" + transactionDesc + "\"}";
    }

}
