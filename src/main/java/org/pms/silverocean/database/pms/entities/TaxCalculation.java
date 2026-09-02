package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_tax_calculation")
@Getter @Setter @NoArgsConstructor
public class TaxCalculation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private long ownerUserId;
    @Column(nullable = false, length = 30) private String calculationType;
    @Column(nullable = false) private long ruleVersionId;
    @Column(nullable = false, length = 20) private String taxPeriod;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal grossAmount;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal taxableAmount;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal estimatedTax;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal creditAmount;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal estimatedPayable;
    @Column(nullable = false, length = 30) private String outcome;
    @Column(nullable = false, length = 1500) private String explanation;
    @Column(nullable = false, columnDefinition = "json") private String inputSnapshot;
    @Column(nullable = false, columnDefinition = "json") private String ruleSnapshot;
    private LocalDate dueDate;
    @Column(nullable = false, insertable = false, updatable = false) private ZonedDateTime createdOn;
}
