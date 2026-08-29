package org.pms.silverocean.service.notification.sms.africastalking.wrappers;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum ATStatusCode {
    PROCESSED(100, "Processed", false),
    SENT(101, "Sent", false),
    QUEUED(102, "Queued", false),

    // 400 series: Client-side or soft rejection/hold statuses
    RISK_HOLD(401, "RiskHold", false),
    INVALID_SENDER_ID(402, "InvalidSenderId", false),
    INVALID_PHONE_NUMBER(403, "InvalidPhoneNumber", false),
    UNSUPPORTED_NUMBER_TYPE(404, "UnsupportedNumberType", false),
    INSUFFICIENT_BALANCE(405, "InsufficientBalance", true),
    USER_IN_BLACKLIST(406, "UserInBlacklist", false),
    COULD_NOT_ROUTE(407, "CouldNotRoute", false),
    DO_NOT_DISTURB_REJECTION(409, "DoNotDisturbRejection", false),

    // 500 series: Server-side or hard failure statuses
    INTERNAL_SERVER_ERROR(500, "InternalServerError", true),
    GATEWAY_ERROR(501, "GatewayError", true),
    REJECTED_BY_GATEWAY(502, "RejectedByGateway", false);

    private final int code;
    private final String description;
    private final boolean retryIfError;

    public static ATStatusCode fromCode(int code) {
        return Arrays.stream(values())
                .filter(cfg -> cfg.getCode() == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid statusCode: " + code));
    }

    /**
     * Private constructor to initialize the status code and description.
     * @param code The integer status code.
     * @param description The string description of the status.
     */
    ATStatusCode(int code, String description, boolean retryIfError) {
        this.code = code;
        this.description = description;
        this.retryIfError = retryIfError;
    }
}
