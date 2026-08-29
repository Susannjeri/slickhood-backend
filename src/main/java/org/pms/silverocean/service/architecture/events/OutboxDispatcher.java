package org.pms.silverocean.service.architecture.events;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.database.pms.DomainEventOutboxRepo;
import org.pms.silverocean.database.pms.entities.DomainEventOutbox;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import io.micrometer.core.instrument.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component @Slf4j
public class OutboxDispatcher {
    private final DomainEventOutboxRepo repo;private final OutboxClaimService claims;private final Map<String,DomainEventHandler> handlers;private final Counter processed;private final Counter failed;
    public OutboxDispatcher(DomainEventOutboxRepo repo,OutboxClaimService claims,List<DomainEventHandler> handlers,MeterRegistry meters){this.repo=repo;this.claims=claims;this.handlers=handlers.stream().collect(Collectors.toUnmodifiableMap(DomainEventHandler::eventType,Function.identity()));this.processed=meters.counter("slickhood.outbox.processed");this.failed=meters.counter("slickhood.outbox.failed");Gauge.builder("slickhood.outbox.backlog",repo,r->r.countByStatusInAndActiveTrue(List.of("PENDING","FAILED","PROCESSING"))).register(meters);}
    @PostConstruct void recover(){int recovered=claims.recoverStale();if(recovered>0)log.warn("Recovered {} stale outbox claims",recovered);}
    @Async @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT,fallbackExecution=true)
    public void afterCommit(OutboxDispatchSignal signal){dispatch(signal.outboxId());}
    @Scheduled(fixedDelayString="${architecture.outbox.poll-delay-ms:2000}")
    public void retryPending(){repo.findDispatchCandidates(LocalDateTime.now(),PageRequest.of(0,100)).forEach(this::dispatch);}
    public void dispatch(long id){Optional<DomainEventOutbox> claimed=claims.claim(id);if(claimed.isEmpty())return;DomainEventOutbox event=claimed.get();String previous=MDC.get("correlationId");try{if(event.getCorrelationId()!=null)MDC.put("correlationId",event.getCorrelationId());DomainEventHandler handler=handlers.get(event.getEventType());if(handler==null)throw new IllegalStateException("No handler registered for "+event.getEventType());handler.handle(event);claims.processed(id);processed.increment();}catch(Exception e){failed.increment();log.error("Outbox event {} ({}) failed",event.getEventId(),event.getEventType(),e);claims.failed(id,e);}finally{if(previous==null)MDC.remove("correlationId");else MDC.put("correlationId",previous);}}
}
