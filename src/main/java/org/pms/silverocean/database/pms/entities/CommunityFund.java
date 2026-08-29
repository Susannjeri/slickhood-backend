package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Getter @Setter
@Table(name="pms_community_fund",indexes={
        @Index(name="idx_community_fund_property",columnList="propertyId,active,status"),
        @Index(name="idx_community_fund_creator",columnList="createdBy,active")})
public class CommunityFund extends BaseCreatorEntity {
    @Column(nullable=false) private Long propertyId;
    @Column(nullable=false,length=180) private String name;
    @Column(nullable=false,length=30) private String fundType;
    @Column(nullable=false,length=30) private String contributorScope;
    @Column(nullable=false,length=2000) private String description;
    @Column(nullable=false,length=3) private String currency;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal targetAmount;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal defaultContribution;
    @Column(nullable=false) private LocalDate opensOn;
    @Column(nullable=false) private LocalDate dueDate;
    private LocalDate closesOn;
    @Column(nullable=false,length=24) private String status;
    @Column(nullable=false) private Long paymentAccountId;
    @Column(nullable=false) private Long custodianUserId;
    @Column(nullable=false) private boolean dualApprovalRequired=true;
}
