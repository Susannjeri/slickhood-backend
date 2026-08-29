package org.pms.silverocean.service.visitor.wrappers;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.pms.silverocean.service.visitor.enums.VisitorCategory;

import java.time.LocalDateTime;

public record CreateVisitorRequest(

        @NotBlank(message = "Visitor name is required")
        @Size(max = 150, message = "Visitor name must not exceed 150 characters")
        String visitorName,

        @Size(max = 20, message = "Vehicle plate must not exceed 20 characters")
        String vehiclePlate,

        @NotNull(message = "Expected arrival time is required")
        @Future(message = "Expected arrival time must be in the future")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime expectedArrivalTime,

        @Size(max = 100, message = "Parking lot must not exceed 100 characters")
        String parkingLot,

        boolean chargeable,

        @NotNull(message = "Unit ID is required")
        @Positive(message = "Unit ID must be a positive number")
        Long unitId,
        @NotNull(message = "PhoneNumber is required for Booking")
        String visitorPhoneNumber,

        VisitorCategory visitorCategory
) {
}
