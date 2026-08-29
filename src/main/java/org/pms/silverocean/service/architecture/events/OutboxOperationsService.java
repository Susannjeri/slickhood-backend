package org.pms.silverocean.service.architecture.events;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.database.pms.DomainEventOutboxRepo;
import org.pms.silverocean.database.pms.entities.DomainEventOutbox;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxOperationsService {
    private final DomainEventOutboxRepo repo;

    public Summary summary() {
        return new Summary(repo.countByStatusAndActiveTrue("PENDING"),
                repo.countByStatusAndActiveTrue("PROCESSING"),
                repo.countByStatusAndActiveTrue("FAILED"),
                repo.countByStatusAndActiveTrue("DEAD"));
    }

    public Page<Failure> failures(Pageable pageable) {
        return repo.findByStatusInAndActiveTrueOrderByCreatedOnDesc(List.of("FAILED", "DEAD"), pageable)
                .map(Failure::new);
    }

    public record Summary(long pending, long processing, long failed, long dead) {}

    public record Failure(long id, String eventId, String eventType, String aggregateType,
                          String aggregateId, String status, int attempts, LocalDateTime nextAttemptAt,
                          LocalDateTime processingStartedAt, String correlationId, String lastError) {
        Failure(DomainEventOutbox event) {
            this(event.getId(), event.getEventId(), event.getEventType(), event.getAggregateType(),
                    event.getAggregateId(), event.getStatus(), event.getAttempts(), event.getNextAttemptAt(),
                    event.getProcessingStartedAt(), event.getCorrelationId(), event.getLastError());
        }
    }
}
