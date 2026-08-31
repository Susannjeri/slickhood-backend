package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import org.pms.silverocean.service.visitor.enums.VisitorStatus;
import org.pms.silverocean.service.visitor.enums.VisitType;
import org.pms.silverocean.service.visitor.projections.PropertyIdUnitRefPropertyNameProjection;
import org.pms.silverocean.service.visitor.wrappers.CreateVisitorRequest;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.pms.silverocean.common.PMSUtils.maskValue;

@Entity
@Table(name = "pms_visitor", indexes = {
        @Index(name = "idx_visitor_createdBy", columnList = "createdBy"),
        @Index(name = "idx_visitor_unitId", columnList = "unitId"),
        @Index(name = "idx_visitor_propertyId", columnList = "propertyId"),
        @Index(name = "idx_visitor_status", columnList = "status"),
        @Index(name = "idx_visitor_active", columnList = "active"),
        @Index(name = "idx_visitor_active_createdBy", columnList = "active, createdBy"),
        @Index(name = "idx_visitor_active_status_createdBy", columnList = "active, status, createdBy"),
        @Index(name = "idx_visitor_expectedArrivalTime", columnList = "expectedArrivalTime"),
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Visitor extends BaseCreatorEntity implements Auditable {
    private long unitId;
    private String unitRef;
    private long propertyId;
    private String propertyName;
    private String visitorName;
    private String phoneNumber;
    private String vehiclePlate;
    private ZonedDateTime expectedArrivalTime;
    private String parkingLot;
    private boolean chargeable;
    private String status;
    private String category;
    private String checkInGuardName;
    private String checkOutGuardName;
    private String visitType;
    private String purpose;
    private String companyName;
    private String trackingNumber;
    private String credentialHash;
    private String credentialHint;
    private ZonedDateTime validFrom;
    private ZonedDateTime validUntil;
    private ZonedDateTime approvedAt;
    private Long approvedBy;
    private String decisionReason;
    private Long hostUserId;
    private ZonedDateTime checkedInAt;
    private ZonedDateTime checkedOutAt;
    private int entryCount;
    private int maxEntries = 1;
    private boolean requiresApproval;

    public static Visitor getNewVisitorInstance(CreateVisitorRequest createVisitorRequest, Users tenant, String visitorPhoneNumber, PropertyIdUnitRefPropertyNameProjection propertyDetails) {
        Visitor visitor = new Visitor();
        visitor.setUnitId(createVisitorRequest.unitId());
        visitor.setUnitRef(propertyDetails.getUnitRef());
        visitor.setPropertyId(propertyDetails.getPropertyId());
        visitor.setPropertyName(propertyDetails.getPropertyName());
        visitor.setVisitorName(createVisitorRequest.visitorName());
        visitor.setVehiclePlate(createVisitorRequest.vehiclePlate());
        visitor.setPhoneNumber(visitorPhoneNumber);
        visitor.setExpectedArrivalTime(createVisitorRequest.expectedArrivalTime().atZone(ZoneId.of("Africa/Nairobi")).withZoneSameInstant(ZoneId.of("UTC")));
        visitor.setParkingLot(createVisitorRequest.parkingLot());
        visitor.setChargeable(createVisitorRequest.chargeable());
        visitor.setStatus(VisitorStatus.PENDING.name());
        visitor.setActive(true);
        visitor.setCreatedBy(tenant.getId());
        visitor.setCategory(createVisitorRequest.visitorCategory().name());
        visitor.setVisitType(createVisitorRequest.visitorCategory() == org.pms.silverocean.service.visitor.enums.VisitorCategory.DELIVERY
                ? VisitType.DELIVERY.name()
                : (createVisitorRequest.vehiclePlate() == null || createVisitorRequest.vehiclePlate().isBlank()
                ? VisitType.WALK_IN.name() : VisitType.DRIVE_IN.name()));
        visitor.setValidFrom(visitor.getExpectedArrivalTime().minusHours(2));
        visitor.setValidUntil(visitor.getExpectedArrivalTime().plusHours(8));
        visitor.setHostUserId(tenant.getId());
        visitor.setMaxEntries(1);

        return visitor;
    }

    @Override
    public String toAuditJSON() {
        return "{\n" +
                "  \"createdBy\": " + getCreatedBy() + ",\n" +
                "  \"unitId\": " + unitId + ",\n" +
                "  \"unitRef\": " + unitRef + ",\n" +
                "  \"propertyId\": " + propertyId + ",\n" +
                "  \"propertyName\": " + propertyName + ",\n" +
                "  \"visitorName\": \"" + maskValue(visitorName) + "\",\n" +
                "  \"visitorPhoneNumber\": \"" + maskValue(phoneNumber) + "\",\n" +
                "  \"vehiclePlate\": \"" + maskValue(vehiclePlate) + "\",\n" +
                "  \"expectedArrivalTime\": \"" + (expectedArrivalTime != null ? expectedArrivalTime.toString() : "") + "\",\n" +
                "  \"parkingLot\": \"" + parkingLot + "\",\n" +
                "  \"chargeable\": " + chargeable + ",\n" +
                "  \"status\": \"" + status + "\",\n" +
                "  \"decisionReason\": \"" + (decisionReason == null ? "" : decisionReason.replace("\"", "'")) + "\",\n" +
                "  \"checkInGuardName\": \"" + checkInGuardName + "\",\n" +
                "  \"checkOutGuardName\": \"" + checkOutGuardName + "\",\n" +
                "  \"category\": \"" + category + "\"\n" +
                "}";
    }
}
