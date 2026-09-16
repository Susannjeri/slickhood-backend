package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.service.property.PMSPropertyCategory;
import org.pms.silverocean.service.property.PMSUnitTypes;
import java.util.HashSet;
import java.util.Set;

/** Additive catalogue: existing enum identifiers and mappings are never rewritten. */
@Entity @Table(name="pms_custom_property_type") @Getter @Setter
public class CustomPropertyType extends org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity implements Auditable {
    @Column(length=64,updatable=false,nullable=false,unique=true) private String code;
    @Column(nullable=false,length=160) private String name;
    @Column(length=1000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32,updatable=false) private PMSPropertyCategory category;
    @Version private long version;
    @ElementCollection(fetch=FetchType.EAGER)
    @CollectionTable(name="pms_custom_property_unit_type",joinColumns=@JoinColumn(name="property_type_id"))
    @Enumerated(EnumType.STRING) @Column(name="unit_type",nullable=false,length=64)
    private Set<PMSUnitTypes> unitTypes=new HashSet<>();
    @Override public String toAuditJSON(){var n=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();n.put("code",code);n.put("name",name);n.put("description",description);n.put("category",category.name());n.put("active",isActive());n.put("version",version);var a=n.putArray("unitTypes");unitTypes.stream().map(Enum::name).sorted().forEach(a::add);return n.toString();}
}
