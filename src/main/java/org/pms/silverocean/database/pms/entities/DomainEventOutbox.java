package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseActiveEntity;

import java.time.LocalDateTime;

@Entity
@Table(name="pms_domain_event_outbox", uniqueConstraints={
        @UniqueConstraint(name="uk_outbox_event_id",columnNames="eventId"),
        @UniqueConstraint(name="uk_outbox_dedupe_key",columnNames="dedupeKey")}, indexes={
        @Index(name="idx_outbox_dispatch",columnList="status,nextAttemptAt"),
        @Index(name="idx_outbox_aggregate",columnList="aggregateType,aggregateId")})
@Getter @Setter @NoArgsConstructor
public class DomainEventOutbox extends BaseActiveEntity {
    @Column(nullable=false,updatable=false,length=36) private String eventId;
    @Column(nullable=false,updatable=false,length=180) private String dedupeKey;
    @Column(nullable=false,updatable=false,length=80) private String eventType;
    @Column(nullable=false,updatable=false,length=80) private String aggregateType;
    @Column(nullable=false,updatable=false,length=120) private String aggregateId;
    @Lob @Column(nullable=false,updatable=false,columnDefinition="LONGTEXT") private String payload;
    @Column(nullable=false,length=20) private String status;
    @Column(nullable=false) private int attempts;
    @Column(nullable=false) private LocalDateTime nextAttemptAt;
    private LocalDateTime processingStartedAt;
    private LocalDateTime processedAt;
    @Column(length=36) private String correlationId;
    @Column(length=1000) private String lastError;
}
