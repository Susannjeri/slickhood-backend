package org.pms.silverocean.service;

import lombok.Getter;
import org.pms.silverocean.common.ResponseCode;

@Getter
public class PMSCustomException extends RuntimeException {
    private final ResponseCode responseCode;
    private final Object data;

    public PMSCustomException(ResponseCode responseCode) {
        super(responseCode.getDescription());
        this.responseCode = responseCode;
        this.data = null;
    }

    public PMSCustomException(ResponseCode responseCode, Object data) {
        super(responseCode.getDescription());
        this.responseCode = responseCode;
        this.data = data;
    }

    public PMSCustomException(ResponseCode responseCode, Throwable cause) {
        super(responseCode.getDescription(), cause);
        this.responseCode = responseCode;
        this.data = null;
    }

}
