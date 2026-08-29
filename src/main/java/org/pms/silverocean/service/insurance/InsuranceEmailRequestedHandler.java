package org.pms.silverocean.service.insurance;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.database.pms.entities.DomainEventOutbox;
import org.pms.silverocean.service.architecture.events.DomainEventHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InsuranceEmailRequestedHandler implements DomainEventHandler {
    private final ObjectMapper objectMapper;
    private final InsuranceCorrespondenceService correspondenceService;

    @Override public String eventType() { return InsuranceEmailRequestedEvent.TYPE; }
    @Override public void handle(DomainEventOutbox event) throws Exception {
        InsuranceEmailRequestedEvent requested = objectMapper.readValue(event.getPayload(), InsuranceEmailRequestedEvent.class);
        correspondenceService.sendQueued(requested.exchangeId());
    }
}
