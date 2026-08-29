package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ComplaintCase extends BaseCreatorEntity implements Auditable {
    private long serviceId;
    private long bookingId;
    private long filedByUserId;
    private String description;
    private String status;
    private String adminNotes;
    private String resolution;
    private Long assignedAdminId;

    @Override
    public String toAuditJSON() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"serviceId\":" + serviceId + "," +
                "\"bookingId\":" + bookingId + "," +
                "\"filedByUserId\":" + filedByUserId + "," +
                "\"status\":\"" + status + "\"," +
                "\"resolution\":\"" + resolution + "\"" +
                "}";
    }
}
