package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import org.pms.silverocean.service.property.wrappers.UnitDTO;
import org.pms.silverocean.service.property.wrappers.UtilitiesDTO;

import java.util.Objects;
import java.util.stream.Collectors;

@Table(name = "pms_unit", indexes = {
        @Index(name = "idx_unit_created_by", columnList = "createdBy, active"),
        @Index(name = "idx_unit_property_id", columnList = "propertyId"),
        @Index(name = "idx_unit_property_active", columnList = "propertyId, active")
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Unit extends BaseCreatorEntity implements Auditable {
    @Column(name = "property_id", nullable = false)
    private long propertyId;
    private String ref;
    private String unitType;
    private double size;
    private int measurementUnits;
    private String utilities;
    private String leaseMode;
    private double price;
    private String currency;
    private boolean occupied;
    private boolean advertise;
    private String imagePath;
    private String thumbnail;
    private Long templateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false, insertable=false, updatable=false)
    private Property property;

    public Unit(UnitDTO unitDTO) {
        this.propertyId = unitDTO.propertyId();
        this.ref = unitDTO.ref().trim();
        this.unitType = unitDTO.unitType().name();
        this.size = unitDTO.size();
        this.utilities = unitDTO.utilities().stream().map(UtilitiesDTO::id)
                .map(Objects::toString)
                .collect(Collectors.joining(", "));
        this.measurementUnits = unitDTO.measurementUnits().id();
        this.leaseMode = unitDTO.leaseMode().name();
        this.price = unitDTO.price();
        this.currency = unitDTO.currency();
        this.occupied = unitDTO.occupied();
        this.advertise = unitDTO.advertise();
        this.setActive(true);
        this.templateId = unitDTO.templateId();
    }

    public void updateFromDto(UnitDTO unitDTO) {
        this.propertyId = unitDTO.propertyId();
        this.ref = unitDTO.ref().trim();
        this.unitType = unitDTO.unitType().name();
        this.size = unitDTO.size();
        this.utilities = unitDTO.utilities().stream().map(UtilitiesDTO::id)
                .map(Objects::toString)
                .collect(Collectors.joining(","));
        this.measurementUnits = unitDTO.measurementUnits().id();
        this.leaseMode = unitDTO.leaseMode().name();
        this.price = unitDTO.price();
        if (StringUtils.isNotBlank(unitDTO.currency())) {
            this.currency = unitDTO.currency();
        }
        this.advertise = unitDTO.advertise();
        this.templateId = unitDTO.templateId();
    }

    @Override
    public String toAuditJSON() {
        return getString();
    }

    @NonNull
    private String getString() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"propertyId\":" + propertyId + "," +
                "\"ref\":\"" + ref + "\"," +
                "\"unitType\":" + unitType + "," +
                "\"size\":\"" + size + "\"," +
                "\"measurementUnits\":" + measurementUnits + "," +
                "\"utilities\":\"" + utilities + "\"," +
                "\"leaseMode\":\"" + leaseMode + "\"," +
                "\"price\":" + price + "," +
                "\"currency\":\"" + currency + "\"," +
                "\"occupied\":" + occupied + "," +
                "\"advertise\":" + advertise + "," +
                "\"thumbnail\":\"" + thumbnail + "\"," +
                "\"imagePath\":\"" + imagePath + "\"," +
                "\"templateId\":" + templateId + "," +
                "\"active\":" + isActive() + "," +
                "\"createdBy\":" + getCreatedBy() + "," +
                "\"createdOn\":\"" + getCreatedOn() + "\"" +
                "}";
    }

    @Override
    public String toString() {
        return getString();
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = StringUtils.isNotBlank(thumbnail) ? thumbnail.replaceAll("\\s+", "_") : thumbnail;
    }
}
