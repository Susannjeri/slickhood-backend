package org.pms.silverocean.service.lease.wrappers;

import java.time.ZonedDateTime;

public record LeaseMessageDTO(long id, String message, ZonedDateTime createdOn, String sentBy, String senderRole) {
    public LeaseMessageDTO(long id, byte[] message, ZonedDateTime createdOn, String sentBy, String senderRole) {
        this(id, new String(message), createdOn, sentBy, senderRole);
    }

    public LeaseMessageDTO withDecryptedMessage(String message) {
        return new LeaseMessageDTO(id, message, createdOn, sentBy, senderRole);
    }
}
