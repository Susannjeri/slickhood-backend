package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Getter @Setter
@Table(name="pms_community_fund_transaction",indexes={
        @Index(name="idx_fund_transaction_fund",columnList="fundId,occurredAt"),
        @Index(name="idx_fund_transaction_event",columnList="eventKey",unique=true)})
public class CommunityFundTransaction extends BaseCreatorEntity {
    @Column(nullable=false) private Long fundId;
    @Column(nullable=false,length=190,unique=true) private String eventKey;
    @Column(nullable=false,length=30) private String transactionType;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal amount;
    @Column(nullable=false,length=3) private String currency;
    @Column(nullable=false,length=1000) private String description;
    @Column(length=40) private String sourceType;
    private Long sourceId;
    private Long contributorUserId;
    private Long beneficiaryUserId;
    @Column(length=200) private String beneficiaryName;
    @Column(length=120) private String externalReference;
    @Column(nullable=false) private LocalDateTime occurredAt;
}
