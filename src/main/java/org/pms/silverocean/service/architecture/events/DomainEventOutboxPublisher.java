package org.pms.silverocean.service.architecture.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.database.pms.DomainEventOutboxRepo;
import org.pms.silverocean.database.pms.entities.DomainEventOutbox;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class DomainEventOutboxPublisher {
    private final DomainEventOutboxRepo repo;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void publish(String eventType,String aggregateType,String aggregateId,String dedupeKey,Object payload){
        if(repo.existsByDedupeKey(dedupeKey))return;
        DomainEventOutbox event=new DomainEventOutbox();event.setEventId(UUID.randomUUID().toString());event.setEventType(eventType);
        event.setAggregateType(aggregateType);event.setAggregateId(aggregateId);event.setDedupeKey(dedupeKey);
        try{event.setPayload(objectMapper.writeValueAsString(payload));}catch(JsonProcessingException e){throw new IllegalArgumentException("Domain event payload cannot be serialized",e);}
        event.setStatus("PENDING");event.setAttempts(0);event.setNextAttemptAt(LocalDateTime.now());event.setCorrelationId(MDC.get("correlationId"));event.setActive(true);
        event=repo.save(event);applicationEventPublisher.publishEvent(new OutboxDispatchSignal(event.getId()));
    }
}
