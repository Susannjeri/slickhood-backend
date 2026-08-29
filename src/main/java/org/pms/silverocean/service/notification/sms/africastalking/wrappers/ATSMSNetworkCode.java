package org.pms.silverocean.service.notification.sms.africastalking.wrappers;

import lombok.Getter;

@Getter
public enum ATSMSNetworkCode {
    // 🇳🇬 Nigeria
    AIRTEL_NIGERIA(62120, "Airtel Nigeria"),
    MTN_NIGERIA(62130, "MTN Nigeria"),
    GLO_NIGERIA(62150, "Glo Nigeria"),
    ETISALAT_NIGERIA(62160, "Etisalat Nigeria"),

    // 🇷🇼 Rwanda
    MTN_RWANDA(63510, "MTN Rwanda"),
    TIGO_RWANDA(63513, "Tigo Rwanda"),
    AIRTEL_RWANDA(63514, "Airtel Rwanda"),

    // 🇰🇪 Kenya
    SAFARICOM_KENYA(63902, "Safaricom"),
    AIRTEL_KENYA(63903, "Airtel Kenya"),
    ORANGE_KENYA(63907, "Orange Kenya"),
    EQUITEL_KENYA(63999, "Equitel Kenya"),

    // 🇹🇿 Tanzania
    TIGO_TANZANIA(64002, "Tigo Tanzania"),
    ZANTEL_TANZANIA(64003, "Zantel Tanzania"),
    VODACOM_TANZANIA(64004, "Vodacom Tanzania"),
    AIRTEL_TANZANIA(64005, "Airtel Tanzania"),
    TTCL_TANZANIA(64007, "TTCL Tanzania"),
    HALOTEL_TANZANIA(64009, "Halotel Tanzania"),

    // 🇺🇬 Uganda
    AIRTEL_UGANDA(64101, "Airtel Uganda"),
    MTN_UGANDA(64110, "MTN Uganda"),
    UTL_UGANDA(64111, "UTL Uganda"),
    AFRICELL_UGANDA(64114, "Africell Uganda"),

    // 🇲🇼 Malawi
    TNM_MALAWI(65001, "TNM Malawi"),
    AIRTEL_MALAWI(65010, "Airtel Malawi"),

    // 🏛️ Generic / Special
    ATHENA(99999, "Athena");

    private final int code;
    private final String operatorName;



    ATSMSNetworkCode(int code, String operatorName) {
        this.code = code;
        this.operatorName = operatorName;
    }
}
