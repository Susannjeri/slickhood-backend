package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.pms.silverocean.database.pms.entities.base.BaseIDEntity;
import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_financial_journal", uniqueConstraints = @UniqueConstraint(name = "uk_financial_journal_event_key", columnNames = "eventKey"),
        indexes = {@Index(name = "idx_financial_journal_source", columnList = "sourceType,sourceId"), @Index(name = "idx_financial_journal_occurred", columnList = "occurredAt")})
@Getter @NoArgsConstructor
public class FinancialJournal extends BaseIDEntity {
    @Column(nullable=false,updatable=false,length=190) private String eventKey;
    @Column(nullable=false,updatable=false,length=50) private String eventType;
    @Column(nullable=false,updatable=false,length=50) private String sourceType;
    @Column(nullable=false,updatable=false,length=120) private String sourceId;
    @Column(updatable=false,length=120) private String providerReference;
    @Column(nullable=false,updatable=false) private ZonedDateTime occurredAt;
    public FinancialJournal(String eventKey,String eventType,String sourceType,String sourceId,String providerReference,ZonedDateTime occurredAt){
        this.eventKey=eventKey;this.eventType=eventType;this.sourceType=sourceType;this.sourceId=sourceId;this.providerReference=providerReference;this.occurredAt=occurredAt;
    }
}
