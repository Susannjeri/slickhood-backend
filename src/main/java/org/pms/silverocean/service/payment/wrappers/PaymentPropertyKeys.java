package org.pms.silverocean.service.payment.wrappers;

public class PaymentPropertyKeys {

    private PaymentPropertyKeys() {
    }

    // M-Pesa
    public static final String PAYBILL = "paybill";
    public static final String STK_PASSKEY = "stk_passkey";
    public static final String CONSUMER_KEY = "consumer_key";
    public static final String CONSUMER_SECRET = "consumer_secret";

    // FlutterWave
    public static final String SECRET_KEY = "secret_key";
    public static final String PUBLIC_KEY = "public_key";
    public static final String ENCRYPTION_KEY = "encryption_key";

    // Airtel Money
    public static final String TILL_NUMBER = "till_number";
    public static final String API_KEY = "api_key";

    // PesaLink
    public static final String BANK_ACCOUNT = "bank_account";
    public static final String BANK_CODE = "bank_code";

    // Paystack
    public static final String SUBACCOUNT_CODE = "subaccount_code";
}
