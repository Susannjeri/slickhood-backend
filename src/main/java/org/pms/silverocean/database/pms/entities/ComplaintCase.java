package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

@Entity
@Table(name = "pms_sp_complaint", indexes = {
        @Index(name = "idx_sp_complaint_serviceId", columnList = "serviceId"),
        @Index(name = "idx_sp_complaint_bookingId", columnList = "bookingId"),
        @Index(name = "idx_sp_complaint_status", columnList = "status"),
        @Index(name = "idx_sp_complaint_assignedAdminId", columnList = "assignedAdminId")
}, uniqueConstraints = @UniqueConstraint(name = "ux_sp_complaint_booking", columnNames = "booking_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ComplaintCase extends BaseCreatorEntity implements Auditable {
    private long serviceId;
    private long bookingId;
    private long filedByUserId;
    @jakarta.persistence.Column(length = 2000)
    private String description;
    private String status;
    @jakarta.persistence.Column(length = 2000)
    private String adminNotes;
    @jakarta.persistence.Column(length = 2000)
    private String resolution;
    @jakarta.persistence.Column(length = 2000)
    private String publicResolution;
    private Long assignedAdminId;

    @Override
    public String toAuditJSON() {
        var json = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        json.put("id", getId());
        json.put("serviceId", serviceId);
        json.put("bookingId", bookingId);
        json.put("filedByUserId", filedByUserId);
        json.put("status", status);
        json.put("resolution", resolution);
        json.put("publicResolution", publicResolution);
        return json.toString();
    }
}
