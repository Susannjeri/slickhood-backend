package org.pms.silverocean.service.payment.platforms.pesalink.wrappers;

import lombok.Getter;

@Getter
public enum PesalinkStatus {
    VALID("valid", ""),
    SUCCESS("SUCCESS"),
    INVALID_AMOUNT("ERROR", "Invalid Amount"),
    INVALID_BILL_REF("ERROR", "Invalid Bill Ref Number"),
    INSECURE("INSECURE");

    private final String status;

    private final String description;

    PesalinkStatus(String status, String description) {
        this.status = status;
        this.description = description;
    }

    PesalinkStatus(String status) {
        this.status = status;
        this.description = null;
    }
}
