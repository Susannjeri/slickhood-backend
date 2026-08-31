package org.pms.silverocean.service.property.wrappers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.pms.silverocean.config.ValidCurrency;
import org.pms.silverocean.service.lease.wrappers.PMSLeaseMode;
import org.pms.silverocean.service.property.MeasurementUnitsDTO;
import org.pms.silverocean.service.property.PMSPropertyType;
import org.pms.silverocean.service.property.PMSUnitTypes;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record UnitDTO(
        @NotNull @Positive Long propertyId,
        @NotBlank @Size(max = 120) String ref,
        @NotNull PMSUnitTypes unitType,
        PMSPropertyType propertyType,
        @NotNull @Positive Double size,
        @NotNull MeasurementUnitsDTO measurementUnits,
        @NotEmpty Set<UtilitiesDTO> utilities,
        @NotNull PMSLeaseMode leaseMode,
        @NotNull @Positive Double price,
        @ValidCurrency(message = "Invalid 3 letter currency code")
        String currency,
        Boolean occupied,
        boolean advertise,
        String thumbnail,
        List<String> images,
        Long unitId,
        Long templateId,
        Long leaseId,
        Boolean tenantSigned,
        Boolean ownerSigned
        ) {
    public UnitDTO(DbUnitDTO dbUnitDTO, String thumbNail,  Set<UtilitiesDTO> utilities, List<String> images, MeasurementUnitsDTO measurementUnits, Long leaseId, Boolean tenantSigned, Boolean ownerSigned) {
        this(dbUnitDTO.propertyId(), dbUnitDTO.ref(), dbUnitDTO.unitType(), dbUnitDTO.propertyType(), dbUnitDTO.size(),
                measurementUnits, utilities, PMSLeaseMode.valueOf(dbUnitDTO.leaseMode()), dbUnitDTO.price(), dbUnitDTO.currency(),
                dbUnitDTO.occupied(), dbUnitDTO.advertise(), thumbNail, images, dbUnitDTO.unitId(), dbUnitDTO.templateId(), leaseId, tenantSigned, ownerSigned);
    }

    public UnitDTO(Long propertyId,
                   String ref,
                   PMSUnitTypes unitType,
                   Double size,
                   MeasurementUnitsDTO measurementUnits,
                   Set<Long> utilities,
                   PMSLeaseMode leaseMode,
                   Double price,
                   String currency,
                   Long templateId) {
        this(propertyId, ref, unitType, null, size,
                measurementUnits, utilities.stream().map(UtilitiesDTO::new).collect(Collectors.toSet()), leaseMode,
                price, currency, false, false, null, null, null, templateId, null, null, null);
    }
}
