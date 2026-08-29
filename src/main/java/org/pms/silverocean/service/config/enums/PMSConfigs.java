package org.pms.silverocean.service.config.enums;

import lombok.Getter;
import org.pms.silverocean.service.config.ConfigInitService;

import static org.pms.silverocean.service.config.ConfigInitService.AFRICAS_TALKING_PASSWORD;
import static org.pms.silverocean.service.config.ConfigInitService.AFRICAS_TALKING_URL;
import static org.pms.silverocean.service.config.ConfigInitService.AFRICAS_TALKING_USERNAME;
import static org.pms.silverocean.service.config.ConfigInitService.DUPLICATE_UNITS_MAX_LIMIT;
import static org.pms.silverocean.service.config.ConfigInitService.EMAIL_MAXIMUM_RETRIES;
import static org.pms.silverocean.service.config.ConfigInitService.EMAIL_NEXT_RETRY_IN_SECONDS;
import static org.pms.silverocean.service.config.ConfigInitService.EMAIL_RETRY_QUEUE_CAPACITY;
import static org.pms.silverocean.service.config.ConfigInitService.EMAIL_RETRY_THREAD_POOL;
import static org.pms.silverocean.service.config.ConfigInitService.EMAIL_SMS_OTP_VALIDITY_SECONDS;
import static org.pms.silverocean.service.config.ConfigInitService.FLUTTER_WAVE_CALLBACK_URL;
import static org.pms.silverocean.service.config.ConfigInitService.FLUTTER_WAVE_ENCRYPTION_KEY;
import static org.pms.silverocean.service.config.ConfigInitService.FLUTTER_WAVE_LOGO_URL;
import static org.pms.silverocean.service.config.ConfigInitService.FLUTTER_WAVE_MAX_RETRIES;
import static org.pms.silverocean.service.config.ConfigInitService.FLUTTER_WAVE_MAX_VERIFY_RETRIES;
import static org.pms.silverocean.service.config.ConfigInitService.FLUTTER_WAVE_PAYMENT_URL;
import static org.pms.silverocean.service.config.ConfigInitService.FLUTTER_WAVE_PUBLIC_KEY;
import static org.pms.silverocean.service.config.ConfigInitService.FLUTTER_WAVE_REDIRECT_URL;
import static org.pms.silverocean.service.config.ConfigInitService.FLUTTER_WAVE_SECRET_KEY;
import static org.pms.silverocean.service.config.ConfigInitService.FLUTTER_WAVE_SESSION_DURATION_SECONDS;
import static org.pms.silverocean.service.config.ConfigInitService.INVITE_LINK_EXPIRY_PERIOD_DAYS;
import static org.pms.silverocean.service.config.ConfigInitService.JWT_VALIDITY_IN_SECONDS;
import static org.pms.silverocean.service.config.ConfigInitService.LEASE_PAYMENT_RECORDS_BATCH_SIZE;
import static org.pms.silverocean.service.config.ConfigInitService.LEASE_TEMPLATE;
import static org.pms.silverocean.service.config.ConfigInitService.MPESA_CONFIRMATION_CALLBACK_URL;
import static org.pms.silverocean.service.config.ConfigInitService.MPESA_SEND_STK_URL;
import static org.pms.silverocean.service.config.ConfigInitService.MPESA_STK_AUTHENTICATION_URL;
import static org.pms.silverocean.service.config.ConfigInitService.MPESA_STK_CALLBACK_URL;
import static org.pms.silverocean.service.config.ConfigInitService.MPESA_VALIDATION_CALLBACK_URL;
import static org.pms.silverocean.service.config.ConfigInitService.OTP_MAX_ATTEMPTS;
import static org.pms.silverocean.service.config.ConfigInitService.PMS_MPESA_CONSUMER_KEY;
import static org.pms.silverocean.service.config.ConfigInitService.PMS_MPESA_CONSUMER_SECRET;
import static org.pms.silverocean.service.config.ConfigInitService.PMS_MPESA_PAYBILL;
import static org.pms.silverocean.service.config.ConfigInitService.PMS_MPESA_STK_PASSKEY;
import static org.pms.silverocean.service.config.ConfigInitService.ROLES_INITIALIZED_CONFIG;
import static org.pms.silverocean.service.config.ConfigInitService.SMS_MAXIMUM_RETRIES;
import static org.pms.silverocean.service.config.ConfigInitService.SMS_NEXT_RETRY_IN_SECONDS;
import static org.pms.silverocean.service.config.ConfigInitService.SMS_RETRY_QUEUE_CAPACITY;
import static org.pms.silverocean.service.config.ConfigInitService.SMS_RETRY_THREAD_POOL;
import static org.pms.silverocean.service.config.ConfigInitService.SMS_SENDER_NAME;
import static org.pms.silverocean.service.config.ConfigInitService.SP_FRAUD_FAILURE_LIMIT;
import static org.pms.silverocean.service.config.ConfigInitService.SP_LOW_RATING_THRESHOLD;
import static org.pms.silverocean.service.config.ConfigInitService.SP_TRUSTED_MIN_STARS;
import static org.pms.silverocean.service.config.ConfigInitService.SP_TRUSTED_THRESHOLD;

@Getter
public enum PMSConfigs {//TODO mark this config for update in documentation tickets
    ROLES_CONFIG(ROLES_INITIALIZED_CONFIG, "false"),
    MPESA_STK_AUTH_URL(MPESA_STK_AUTHENTICATION_URL, "https://sandbox.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials"),
    MPESA_STK_INIT_URL(MPESA_SEND_STK_URL, "https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest"),
    MPESA_ICON(ConfigInitService.MPESA_ICON, "mpesa_icon.png"),
    MPESA_STK_CALLBACK_BASE_URL(MPESA_STK_CALLBACK_URL, "http://127.0.0.1:8989/callback/stk"),
    MPESA_VALIDATION_BASE_URL(MPESA_VALIDATION_CALLBACK_URL, "http://127.0.0.1:8989/callback/validation"),
    MPESA_CONFIRMATION_BASE_URL(MPESA_CONFIRMATION_CALLBACK_URL, "http://127.0.0.1:8989/callback/confirmation"),
    GLOBAL_MPESA_PAYBILL(PMS_MPESA_PAYBILL, "174379"),
    GLOBAL_MPESA_STK_PASSKEY(PMS_MPESA_STK_PASSKEY, "PassKey Placeholder", true),
    GLOBAL_MPESA_CONSUMER_KEY(PMS_MPESA_CONSUMER_KEY, "Consumer Key Placeholder", true),
    GLOBAL_MPESA_CONSUMER_SECRET(PMS_MPESA_CONSUMER_SECRET, "Consumer Secret Placeholder", true),
    OTP_VALIDITY_SECONDS(EMAIL_SMS_OTP_VALIDITY_SECONDS, 300),
    JWT_VALIDITY_SECONDS(JWT_VALIDITY_IN_SECONDS,  3600),
    AFRICAS_TALKING_SMS_USERNAME(AFRICAS_TALKING_USERNAME, "sandbox"),
    AFRICAS_TALKING_SMS_PASSWORD(AFRICAS_TALKING_PASSWORD, "API Key placeholder", true),
    SMS_SENDERNAME(SMS_SENDER_NAME, "silverocean"),
    AFRICAS_TALKING_SMS_CALLBACK_URL(AFRICAS_TALKING_URL, "http://127.0.0.1:8989/callback/sms"),
    SMS_THREAD_POOL_SIZE(SMS_RETRY_THREAD_POOL, 6),
    EMAIL_THREAD_POOL_SIZE(EMAIL_RETRY_THREAD_POOL, 2),
    SMS_RETRY_QUEUE_SIZE(SMS_RETRY_QUEUE_CAPACITY, 500),
    EMAIL_RETRY_QUEUE_SIZE(EMAIL_RETRY_QUEUE_CAPACITY, 1000),
    SMS_RETRY_DELAY_IN_SECONDS(SMS_NEXT_RETRY_IN_SECONDS, 900),
    EMAIL_RETRY_DELAY_IN_SECONDS(EMAIL_NEXT_RETRY_IN_SECONDS, 300),
    SMS_MAX_RETRIES(SMS_MAXIMUM_RETRIES, 10),
    EMAIL_MAX_RETRIES(EMAIL_MAXIMUM_RETRIES, 10),
    FW_CARD_PAYMENT_URL(FLUTTER_WAVE_PAYMENT_URL, "https://api.flutterwave.com/v3"),
    FW_REDIRECT_URL(FLUTTER_WAVE_REDIRECT_URL, "https://pms-vercel.com/callback/fw/payments"),
    FW_CALLBACK_URL(FLUTTER_WAVE_CALLBACK_URL, "https://316eec3ad4a7.ngrok-free.app/callback/fw/payments"),
    FW_LOGO_URL(FLUTTER_WAVE_LOGO_URL, "https://pms-lovat.vercel.app/_next/image?url=%2F_next%2Fstatic%2Fmedia%2FICON.c937a785.png&w=256&q=75"),
    FW_PUBLIC_KEY(FLUTTER_WAVE_PUBLIC_KEY, "Public Key Placeholder", true),
    FW_SECRET_KEY(FLUTTER_WAVE_SECRET_KEY, "Secret Key Placeholder", true),
    FW_ENCRYPTION_KEY(FLUTTER_WAVE_ENCRYPTION_KEY, "Encryption Key Placeholder", true),
    FW_ICON(ConfigInitService.FLUTTER_ICON, "card_icon.png"),
    FW_SESSION_DURATION(FLUTTER_WAVE_SESSION_DURATION_SECONDS, 60),
    FW_MAX_RETRIES(FLUTTER_WAVE_MAX_RETRIES, 3),
    FW_MAX_VERIFY_RETRIES(FLUTTER_WAVE_MAX_VERIFY_RETRIES, 4),
    MAX_UNIT_DUPLICATE_COUNT(DUPLICATE_UNITS_MAX_LIMIT, 50),
    INVITE_LINK_EXPIRY_DAYS(INVITE_LINK_EXPIRY_PERIOD_DAYS, 3),
    INVITE_LINK_URL(ConfigInitService.INVITE_LINK_URL, "http://127.0.0.1:3000/lease/onboard"),
    REGISTRATION_PAGE_URL(ConfigInitService.REGISTRATION_LINK_URL, "https://pms-lovat.vercel.app/register"),
    HOME_PAGE_URL(ConfigInitService.HOME_PAGE_LINK_URL, "https://pms-lovat.vercel.app/home"),
    ROLE_ASSIGN_PAGE_URL(ConfigInitService.ROLE_ASSIGN_LINK_URL, "https://pms-lovat.vercel.app/role"),
    LEASE_TEMPLATE_FILE(LEASE_TEMPLATE, "lease"),
    MAX_OTP_ATTEMPTS(OTP_MAX_ATTEMPTS, 3),
    LEASE_PAYMENT_RECORDS_PAGE_SIZE(LEASE_PAYMENT_RECORDS_BATCH_SIZE, 100),
    TEXT_SMS_URL(ConfigInitService.TEXT_SMS_URL, "https://sms.textsms.co.ke/api/services/sendsms/"),
    TEXT_DLR_URL(ConfigInitService.TEXT_DLR_URL, "https://sms.textsms.co.ke/api/services/getdlr/"),
    TEXT_SMS_PARTNER_ID(ConfigInitService.TEXT_SMS_PARTNER_ID, "placeholder", true),
    TEXT_SMS_API_KEY(ConfigInitService.TEXT_SMS_API_KEY, "placeholder", true),
    TEXT_SMS_COST(ConfigInitService.TEXT_SMS_COST_CENTS, 30),
    ACTIVE_SMS_PROVIDER(ConfigInitService.ACTIVE_SMS_PROVIDER, "TextSMS"),
    WHATSAPP_URL(ConfigInitService.WHATSAPP_URL, "https://graph.facebook.com/v22.0/%s/messages"),
    WHATSAPP_BUSINESS_PHONENUMBER_ID(ConfigInitService.WHATSAPP_BUSINESS_PHONENUMBER_ID, "placeholder", true),
    WHATSAPP_ACCESS_TOKEN(ConfigInitService.WHATSAPP_ACCESS_TOKEN, "placeholder", true),
    CURRENCY_EXCHANGE_URL(ConfigInitService.CURRENCY_EXCHANGE_URL, "https://v6.exchangerate-api.com/v6/"),
    CURRENCY_EXCHANGE_API_KEY(ConfigInitService.CURRENCY_EXCHANGE_API_KEY, "placeholder", true),
    PESA_LINK_AUTH_URL(ConfigInitService.PESA_LINK_AUTH_URL, "https://api.pesalink.ke/api/v1/oauth/token?grant_type=client_credentials"),
    PESA_LINK_VALIDATION_URL(ConfigInitService.PESA_LINK_VALIDATION_URL, "https://api.pesalink.ke/payments/sandbox/v1/account-validation"),
    PESA_LINK_DISBURSEMENT_URL(ConfigInitService.PESA_LINK_DISBURSEMENT_URL, "https://api.pesalink.ke/payments/sandbox/v1/disbursements"),
    PESA_LINK_ICON(ConfigInitService.PESA_LINK_ICON, "pesalink_icon.png"),
    PESA_LINK_CLIENT_ID(ConfigInitService.PESA_LINK_CLIENT_ID, "id placeholder", true),
    PESA_LINK_CLIENT_SECRET(ConfigInitService.PESA_LINK_CLIENT_SECRET, "key placeholder", true),
    PESA_LINK_IPN_PASSWORD(ConfigInitService.PESA_LINK_IPN_PASSWORD, "key placeholder", true),
    PAYMENT_PESA_LINK_ENABLED(ConfigInitService.PAYMENT_PESA_LINK_ENABLED, "TRUE"),
    PAYMENT_MPESA_ENABLED(ConfigInitService.PAYMENT_MPESA_ENABLED, "TRUE"),
    PAYMENT_FW_ENABLED(ConfigInitService.PAYMENT_FW_ENABLED, "TRUE"),
    VISITOR_NOTIFICATION_CHANNEL(ConfigInitService.VISITOR_NOTIFICATION_CHANNEL, "EMAIL"),
    VISITOR_NOTIFICATION_ENABLED(ConfigInitService.VISITOR_NOTIFICATION_ENABLED, "TRUE"),
    VISITOR_EXPIRY_BATCH_SIZE(ConfigInitService.VISITOR_EXPIRY_BATCH_SIZE, 100),
    VISITOR_EXPIRY_DAYS(ConfigInitService.VISITOR_EXPIRY_DAYS, 7),
    SP_TRUSTED_THRESHOLD_COUNT(SP_TRUSTED_THRESHOLD, 10),
    SP_TRUSTED_MIN_STARS_COUNT(SP_TRUSTED_MIN_STARS, 4),
    SP_LOW_RATING_THRESHOLD_VALUE(SP_LOW_RATING_THRESHOLD, 3),
    SP_FRAUD_FAILURE_LIMIT_VALUE(SP_FRAUD_FAILURE_LIMIT, 3),
    ;
    private final String name;
    private final String stringValue;
    private final int intValue;
    private final boolean encrypted;

    PMSConfigs(String name, String stringValue, boolean encrypted) {
        this.name = name;
        this.stringValue = stringValue;
        this.encrypted = encrypted;
        this.intValue = 0;
    }

    PMSConfigs(String name, String stringValue) {
        this.name = name;
        this.stringValue = stringValue;
        this.encrypted = false;
        this.intValue = 0;
    }

    PMSConfigs(String name, int intValue) {
        this.name = name;
        this.encrypted = false;
        this.stringValue = "";
        this.intValue = intValue;
    }
}
