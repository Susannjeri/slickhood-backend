package org.pms.silverocean.service.visitor.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.pms.silverocean.database.pms.entities.Visitor;
import org.pms.silverocean.service.visitor.enums.VisitorCategory;

import java.time.ZonedDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VisitorDTO(
        long id,
        String visitorName,
        String phoneNumber,
        String vehiclePlate,
        ZonedDateTime expectedArrivalTime,
        String parkingLot,
        boolean chargeable,
        String status,
        long unitId,
        long propertyId,
        String unitRef,
        String propertyName,
        String checkInGuardName,
        String checkOutGuardName,
        ZonedDateTime createdOn,
        VisitorCategory visitorCategory,
        String visitType,
        String purpose,
        String companyName,
        String trackingNumber,
        String credentialHint,
        ZonedDateTime validFrom,
        ZonedDateTime validUntil,
        ZonedDateTime approvedAt,
        String decisionReason,
        Long hostUserId,
        ZonedDateTime checkedInAt,
        ZonedDateTime checkedOutAt,
        int entryCount,
        int maxEntries,
        boolean requiresApproval
) {
    public VisitorDTO(Visitor visitor) {
        this(
                visitor.getId(),
                visitor.getVisitorName(),
                visitor.getPhoneNumber(),
                visitor.getVehiclePlate(),
                visitor.getExpectedArrivalTime(),
                visitor.getParkingLot(),
                visitor.isChargeable(),
                visitor.getStatus(),
                visitor.getUnitId(),
                visitor.getPropertyId(),
                visitor.getUnitRef(),
                visitor.getPropertyName(),
                visitor.getCheckInGuardName(),
                visitor.getCheckOutGuardName(),
                visitor.getCreatedOn(),
                VisitorCategory.valueOf(visitor.getCategory()),
                visitor.getVisitType(),
                visitor.getPurpose(),
                visitor.getCompanyName(),
                visitor.getTrackingNumber(),
                visitor.getCredentialHint(),
                visitor.getValidFrom(),
                visitor.getValidUntil(),
                visitor.getApprovedAt(),
                visitor.getDecisionReason(),
                visitor.getHostUserId(),
                visitor.getCheckedInAt(),
                visitor.getCheckedOutAt(),
                visitor.getEntryCount(),
                visitor.getMaxEntries(),
                visitor.isRequiresApproval()
        );
    }
}
