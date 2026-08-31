package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_estate_budget",
        uniqueConstraints = @UniqueConstraint(name = "uk_estate_budget_property_year_name", columnNames = {"propertyId", "budgetYear", "name"}),
        indexes = @Index(name = "idx_estate_budget_property_year_active", columnList = "propertyId, budgetYear, active"))
@Getter
@Setter
public class EstateBudget extends BaseCreatorEntity {
    private long propertyId;
    private int budgetYear;
    private String name;
    private String currency;
    private String status;
    private ZonedDateTime approvedAt;
    private Long approvedBy;
}
