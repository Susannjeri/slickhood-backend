package org.pms.silverocean.service.property.wrappers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.pms.silverocean.config.ValidCoordinates;
import org.pms.silverocean.config.ValidCurrency;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.service.property.PMSPropertyCategory;
import org.pms.silverocean.service.property.PMSPropertyManagementMode;
import org.pms.silverocean.service.property.PMSPropertyType;

@JsonInclude(JsonInclude.Include.NON_NULL) @JsonIgnoreProperties(ignoreUnknown = true)
public record PropertyDTO(
        @NotBlank(message = "Field is required")
        @Size(max = 160, message = "Property name must not exceed 160 characters")
        String name,
        @NotBlank(message = "Field is required")
        @jakarta.validation.constraints.Pattern(regexp="[A-Z][A-Z0-9_]{0,63}")
        String type,
        PMSPropertyCategory category,
        PMSPropertyManagementMode managementMode,
        @NotBlank(message = "Field is required")
        @Size(max = 500, message = "Address must not exceed 500 characters")
        String address,
        @NotBlank(message = "Map Coordinates are required")
        @ValidCoordinates
        String mapLocation,
        @ValidCurrency(message = "Invalid 3 letter currency code")
        String currency,
        Long id,
        String thumbNail, String userRoleInProperty) {
    public PropertyDTO(String name,PMSPropertyType type,PMSPropertyCategory category,PMSPropertyManagementMode managementMode,String address,String mapLocation,String currency,Long id,String thumbNail,String userRoleInProperty){this(name,type==null?null:type.name(),category,managementMode,address,mapLocation,currency,id,thumbNail,userRoleInProperty);}
    public PropertyDTO(Property property, String userRoleInProperty, String thumbNail) {
        this(property.getName(), property.getType(),
                property.getTypeCategoryResolved(),
                property.getManagementMode(),
                property.getAddress(),
                property.getMapLocation(), property.getCurrency(), property.getId(), thumbNail, userRoleInProperty);
    }
}
