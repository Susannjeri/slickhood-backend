package org.pms.silverocean.service.notification.sms.textsms.wrappers;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter @AllArgsConstructor
public enum TestSMSStatusCode {
    SUCCESS(200, "Successful Request Call", false),
    INVALID_SENDER_ID(1001, "Invalid sender id", true),
    NETWORK_NOT_ALLOWED(1002, "Network not allowed", false),
    INVALID_MOBILE_NUMBER(1003, "Invalid mobile number", false),
    LOW_BULK_CREDITS(1004, "Low bulk credits", true),
    SYSTEM_ERROR_1005(1005, "Failed. System error", true),
    INVALID_CREDENTIALS(1006, "Invalid credentials", false),
    SYSTEM_ERROR_1007(1007, "Failed. System error", true),
    NO_DELIVERY_REPORT(1008, "No Delivery Report", false),
    UNSUPPORTED_DATA_TYPE(1009, "Unsupported data type", false),
    UNSUPPORTED_REQUEST_TYPE(1010, "Unsupported request type", false),
    INTERNAL_ERROR_RETRY(4090, "Internal Error. Try again after 5 minutes", true),
    NO_PARTNER_ID(4091, "No Partner ID is Set", false),
    NO_API_KEY(4092, "No API KEY Provided", false),
    DETAILS_NOT_FOUND(4093, "Details Not Found", true),
    UNKNOWN(-1, "Unknown Error Code", false);

    private final int code;
    private final String description;
    private final boolean retryIfError;

    private static final Map<Integer, TestSMSStatusCode> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toMap(TestSMSStatusCode::getCode, Function.identity()));

    public static TestSMSStatusCode fromCode(int code) {
        return LOOKUP.getOrDefault(code, UNKNOWN);
    }
}
