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
@Table(name="pms_community_fund_expenditure",indexes={
        @Index(name="idx_fund_expenditure_fund",columnList="fundId,status,createdOn"),
        @Index(name="idx_fund_expenditure_beneficiary",columnList="beneficiaryUserId")})
public class CommunityFundExpenditure extends BaseCreatorEntity {
    @Column(nullable=false) private Long fundId;
    @Column(nullable=false,length=1000) private String purpose;
    @Column(nullable=false,length=40) private String category;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal amount;
    @Column(nullable=false,length=40) private String beneficiaryType;
    private Long beneficiaryUserId;
    @Column(nullable=false,length=200) private String beneficiaryName;
    @Column(length=120) private String beneficiaryReference;
    @Column(nullable=false,length=24) private String status;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private Long paidBy;
    private LocalDateTime paidAt;
    @Column(length=120) private String paymentReference;
    @Column(length=800) private String evidenceFileRef;
    @Column(length=1000) private String rejectionReason;
}
