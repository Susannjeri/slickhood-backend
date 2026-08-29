package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseActiveEntity;
import org.pms.silverocean.service.property.PMSPropertyType;
import org.pms.silverocean.service.property.PMSUnitTypes;


@Table(name = "pms_unit_type_mapping", indexes = {
        @Index(name = "idx_unit_type_property_type_unique", columnList = "propertyType, unitType", unique = true)
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnitTypeToPropertyTypeMapping  extends BaseActiveEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PMSUnitTypes unitType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PMSPropertyType propertyType;
}
