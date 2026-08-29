package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
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
    private String address;
    private String mapLocation;
    private String currency;
    private String ref;
    private String imagePath;
    private String thumbnail;
    private boolean hasUnits = false;


    public Property(PropertyDTO dto) {
        this.name = dto.name();
        this.type = dto.type().name();
        this.address = dto.address();
        this.mapLocation = dto.mapLocation();
        this.currency = StringUtils.isNotBlank(dto.currency()) ? dto.currency() : PMSUtils.getDefaultCurrency().getCurrencyCode();
    }

    public void updateFromDto(PropertyDTO dto) {
        this.name = dto.name();
        this.type = dto.type().name();
        this.address = dto.address();
        this.mapLocation = dto.mapLocation();
        this.currency = StringUtils.isNotBlank(dto.currency()) ? dto.currency() : PMSUtils.getDefaultCurrency().getCurrencyCode();
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
