package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "pms_insurance_email_exchange", indexes = {
        @Index(name = "idx_insurance_email_case", columnList = "caseReference, createdOn"),
        @Index(name = "idx_insurance_email_correlation", columnList = "correlationId"),
        @Index(name = "idx_insurance_email_status", columnList = "status, createdOn")
})
@Getter
@Setter
public class InsuranceEmailExchange extends BaseCreatorEntity {
    @Column(nullable = false)
    private Long companyId;
    @Column(nullable = false, length = 80)
    private String caseReference;
    @Column(nullable = false, unique = true, length = 36)
    private String correlationId;
    @Column(nullable = false, length = 30)
    private String messageType;
    @Column(nullable = false, length = 12)
    private String direction;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(nullable = false, length = 254)
    private String senderAddress;
    @Column(nullable = false, length = 254)
    private String recipientAddress;
    @Column(nullable = false, length = 400)
    private String subject;
    @Lob @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] encryptedBody;
    @Column(nullable = false, length = 64)
    private String bodyHash;
    @Column(length = 500)
    private String externalMessageId;
    @Column(length = 500)
    private String inReplyTo;
    private LocalDateTime sentAt;
    private LocalDateTime receivedAt;
    @Column(length = 1000)
    private String lastError;
}
