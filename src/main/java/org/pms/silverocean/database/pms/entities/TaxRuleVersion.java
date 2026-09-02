package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_tax_rule_version")
@Getter @Setter @NoArgsConstructor
public class TaxRuleVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 40) private String ruleCode;
    @Column(nullable = false) private int version;
    @Column(nullable = false) private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    @Column(nullable = false, precision = 12, scale = 8) private BigDecimal rate;
    @Column(precision = 19, scale = 2) private BigDecimal lowerThreshold;
    @Column(precision = 19, scale = 2) private BigDecimal upperThreshold;
    @Column(nullable = false, length = 3) private String currency = "KES";
    @Column(nullable = false, length = 500) private String sourceUrl;
    @Column(nullable = false, length = 1000) private String sourceNote;
    private Long approvedBy;
    private ZonedDateTime approvedAt;
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false, insertable = false, updatable = false) private ZonedDateTime createdOn;
    @Column(nullable = false, insertable = false, updatable = false) private ZonedDateTime updatedOn;
}
