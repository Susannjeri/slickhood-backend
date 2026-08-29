package org.pms.silverocean.service.visitor.wrappers;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.pms.silverocean.service.visitor.enums.VisitType;
import org.pms.silverocean.service.visitor.enums.VisitorCategory;

import java.time.LocalDateTime;

public record RegisterVisitRequest(
        @NotBlank @Size(max = 150) String visitorName,
        @NotBlank @Size(max = 30) String visitorPhoneNumber,
        @NotNull VisitType visitType,
        VisitorCategory visitorCategory,
        @NotNull @Positive Long unitId,
        @Positive Long hostUserId,
        @NotNull @Future @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime expectedArrivalTime,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime validUntil,
        @Size(max = 250) String purpose,
        @Size(max = 20) String vehiclePlate,
        @Size(max = 100) String parkingLot,
        @Size(max = 150) String companyName,
        @Size(max = 120) String trackingNumber,
        @Positive Integer maxEntries,
        boolean chargeable
) {}
