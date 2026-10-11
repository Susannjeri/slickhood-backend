package org.pms.silverocean.service.sp.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.pms.silverocean.database.pms.entities.ComplaintCase;

import java.time.ZonedDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComplaintCaseDTO(
    long id, long serviceId, long bookingId, Long filedByUserId, String description,
    String status, String adminNotes, String resolution, String publicResolution, Long assignedAdminId, ZonedDateTime createdOn,
    String serviceName, String complaintCreatorName, String serviceCreatorName
) {
    public ComplaintCaseDTO(ComplaintCase c) {
        this(c.getId(), c.getServiceId(), c.getBookingId(), c.getFiledByUserId(), c.getDescription(),
             c.getStatus(), c.getAdminNotes(), c.getResolution(), c.getPublicResolution(), c.getAssignedAdminId(), c.getCreatedOn(),
             null, null, null);
    }

    public ComplaintCaseDTO(ComplaintCase c, String serviceName, String complaintCreatorName, String serviceCreatorName) {
        this(c.getId(), c.getServiceId(), c.getBookingId(), c.getFiledByUserId(), c.getDescription(),
             c.getStatus(), c.getAdminNotes(), c.getResolution(), c.getPublicResolution(), c.getAssignedAdminId(), c.getCreatedOn(),
             serviceName, complaintCreatorName, serviceCreatorName);
    }

    public ComplaintCaseDTO withoutInternalReviewDetails() {
        return new ComplaintCaseDTO(id, serviceId, bookingId, null, description,
                status, null, resolution, publicResolution, null, createdOn,
                serviceName, null, serviceCreatorName);
    }
}
