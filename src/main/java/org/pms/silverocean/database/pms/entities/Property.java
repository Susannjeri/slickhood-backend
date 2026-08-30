package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import org.pms.silverocean.service.property.wrappers.PropertyDTO;
import org.pms.silverocean.service.property.PMSPropertyManagementMode;

@Table(name = "pms_property", indexes = {
        @Index(name = "idx_created_by", columnList = "createdBy"),
        @Index(name = "idx_created_by_id", columnList = "createdBy, id"),
        @Index(name = "idx_has_units_and_active", columnList = "hasUnits, active, createdOn"),
})
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Property extends BaseCreatorEntity implements Auditable {
    private String name;
    private String type;
    @Column(length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PMSPropertyManagementMode managementMode = PMSPropertyManagementMode.RENTAL;
    private String address;
    private String mapLocation;
    private String currency;
    private String ref;
    private String imagePath;
    private String thumbnail;
    @Builder.Default
    private boolean hasUnits = false;


    public Property(PropertyDTO dto) {
        this.name = dto.name().trim();
        this.type = dto.type().name();
        this.managementMode = dto.managementMode() == null
                ? PMSPropertyManagementMode.RENTAL
                : dto.managementMode();
        this.address = dto.address().trim();
        this.mapLocation = dto.mapLocation().replaceAll("\\s+", "");
        this.currency = StringUtils.isNotBlank(dto.currency()) ? dto.currency().trim().toUpperCase() : PMSUtils.getDefaultCurrency().getCurrencyCode();
    }

    public void updateFromDto(PropertyDTO dto) {
        this.name = dto.name().trim();
        this.type = dto.type().name();
        if (dto.managementMode() != null) {
            this.managementMode = dto.managementMode();
        }
        this.address = dto.address().trim();
        this.mapLocation = dto.mapLocation().replaceAll("\\s+", "");
        this.currency = StringUtils.isNotBlank(dto.currency()) ? dto.currency().trim().toUpperCase() : PMSUtils.getDefaultCurrency().getCurrencyCode();
    }

    @Override
    public String toAuditJSON() {
        return getString();
    }

    @Override
    public String toString() {
        return getString();
    }

    @NonNull
    private String getString() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"name\":\"" + name + "\"," +
                "\"type\":\"" + type + "\"," +
                "\"managementMode\":\"" + managementMode + "\"," +
                "\"address\":\"" + address + "\"," +
                "\"mapLocation\":\"" + mapLocation + "\"," +
                "\"currency\":\"" + currency + "\"," +
                "\"ref\":\"" + ref + "\"," +
                "\"thumbnail\":\"" + thumbnail + "\"," +
                "\"imagePath\":\"" + imagePath + "\"," +
                "\"active\":" + isActive() + "," +
                "\"createdBy\":" + getCreatedBy() + "," +
                "\"createdOn\":\"" + getCreatedOn() + "\"" +
                "}";
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = StringUtils.isNotBlank(thumbnail) ? thumbnail.replaceAll("\\s+", "_") : thumbnail;
    }
}
