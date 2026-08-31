package org.pms.silverocean.service.payment.wrappers;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum PaymentChannel {
    MPESA("M-Pesa Direct Paybill", "Direct Safaricom M-Pesa Paybill and STK Push payments", List.of(
            new AccountPropertyDefinition(PaymentPropertyKeys.PAYBILL,         "mpesa.paybill",         "mpesa.paybill.description",         false, true),
            new AccountPropertyDefinition(PaymentPropertyKeys.STK_PASSKEY,     "mpesa.stk.passkey",     "mpesa.stk.passkey.description",     true,  false),
            new AccountPropertyDefinition(PaymentPropertyKeys.CONSUMER_KEY,    "mpesa.consumer.key",    "mpesa.consumer.key.description",    true,  false),
            new AccountPropertyDefinition(PaymentPropertyKeys.CONSUMER_SECRET, "mpesa.consumer.secret", "mpesa.consumer.secret.description", true,  false)
    )),
    MPESA_BANK("M-Pesa via Bank Paybill", "M-Pesa collected through a bank Paybill and reconciled from the bank callback", List.of(
            new AccountPropertyDefinition(PaymentPropertyKeys.BANK_PAYBILL, "mpesa.bank.paybill", "mpesa.bank.paybill.description", false, true),
            new AccountPropertyDefinition(PaymentPropertyKeys.BANK_ACCOUNT, "mpesa.bank.account", "mpesa.bank.account.description", false, true),
            new AccountPropertyDefinition(PaymentPropertyKeys.BANK_CODE, "mpesa.bank.code", "mpesa.bank.code.description", false, true)
    )),
    FLUTTER_WAVE("Card Payment", "Card Payment via FlutterWave APIs", List.of(
            new AccountPropertyDefinition(PaymentPropertyKeys.SECRET_KEY,     "flutterwave.secret.key",     "flutterwave.secret.key.description",     true, false),
            new AccountPropertyDefinition(PaymentPropertyKeys.PUBLIC_KEY,     "flutterwave.public.key",     "flutterwave.public.key.description",     true, true),
            new AccountPropertyDefinition(PaymentPropertyKeys.ENCRYPTION_KEY, "flutterwave.encryption.key", "flutterwave.encryption.key.description", true, false)
    )),
    AIRTEL_MONEY("Airtel Money", "Airtel customers", List.of(
            new AccountPropertyDefinition(PaymentPropertyKeys.TILL_NUMBER, "airtel.till.number", "airtel.till.number.description", false, true),
            new AccountPropertyDefinition(PaymentPropertyKeys.API_KEY,     "airtel.api.key",     "airtel.api.key.description",     true,  false)
    )),
    PESA_LINK("PesaLink", "Kenyan Bank to Bank payments", List.of(
            new AccountPropertyDefinition(PaymentPropertyKeys.BANK_ACCOUNT, "pesalink.bank.account", "pesalink.bank.account.description", false, true),
            new AccountPropertyDefinition(PaymentPropertyKeys.BANK_CODE,    "pesalink.bank.code",    "pesalink.bank.code.description",    false, true)
    )),
    PAYSTACK("Paystack", "Card and mobile money payments routed to a landlord subaccount", List.of(
            new AccountPropertyDefinition(PaymentPropertyKeys.SUBACCOUNT_CODE, "paystack.subaccount.code", "paystack.subaccount.code.description", true, true)
    ));

    private final String name;
    private final String description;
    private final List<AccountPropertyDefinition> accountProperties;

    PaymentChannel(String name, String description, List<AccountPropertyDefinition> accountProperties) {
        this.name = name;
        this.description = description;
        this.accountProperties = accountProperties;
    }

    public static PaymentChannel fromName(String value) {
        return Arrays.stream(values())
                .filter(cfg -> cfg.getName().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid param name: " + value));
    }

    public AccountPropertyDefinition findProperty(String key) {
        return accountProperties.stream()
                .filter(p -> p.key().equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown property key '" + key + "' for channel " + this.name()));
    }

    public int expectedPropertyCount() {
        return accountProperties.size();
    }

    /** Find a property definition by its labelKey across all channels. */
    public static AccountPropertyDefinition findPropertyByLabelKey(String labelKey) {
        return Arrays.stream(values())
                .flatMap(c -> c.getAccountProperties().stream())
                .filter(p -> p.labelKey().equalsIgnoreCase(labelKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown property labelKey: " + labelKey));
    }

    /** Find the channel that owns a property with the given labelKey. */
    public static PaymentChannel findChannelByPropertyLabelKey(String labelKey) {
        return Arrays.stream(values())
                .filter(c -> c.getAccountProperties().stream().anyMatch(p -> p.labelKey().equalsIgnoreCase(labelKey)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No channel found for property labelKey: " + labelKey));
    }
}
