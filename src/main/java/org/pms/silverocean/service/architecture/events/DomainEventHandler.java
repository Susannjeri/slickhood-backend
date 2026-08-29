package org.pms.silverocean.service.architecture.events;

import org.pms.silverocean.database.pms.entities.DomainEventOutbox;

public interface DomainEventHandler {
    String eventType();
    void handle(DomainEventOutbox event) throws Exception;
}
