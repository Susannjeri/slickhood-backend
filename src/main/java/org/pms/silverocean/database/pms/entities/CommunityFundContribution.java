package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Getter @Setter
@Table(name="pms_community_fund_contribution",uniqueConstraints=@UniqueConstraint(name="uk_fund_contributor_unit",columnNames={"fund_id","contributor_user_id","unit_id"}),indexes={
        @Index(name="idx_fund_contribution_user",columnList="contributorUserId,active"),
        @Index(name="idx_fund_contribution_invoice",columnList="invoiceId")})
public class CommunityFundContribution extends BaseCreatorEntity {
    @Column(nullable=false) private Long fundId;
    @Column(nullable=false) private Long contributorUserId;
    @Column(nullable=false) private Long unitId;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal assessedAmount;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal paidAmount=BigDecimal.ZERO;
    private Long invoiceId;
    @Column(nullable=false,length=24) private String status;
    private LocalDateTime paidAt;
    @Column(length=120) private String paymentReference;
}
