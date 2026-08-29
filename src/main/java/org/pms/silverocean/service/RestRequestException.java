package org.pms.silverocean.service;

import lombok.Getter;

@Getter
public class RestRequestException extends RuntimeException {
    private final int httpStatusCode;
    private final String responseBody;
    public RestRequestException(String message, int httpStatusCode, String responseBody) {
        super(message);
        this.httpStatusCode = httpStatusCode;
        this.responseBody = responseBody;
    }
}
