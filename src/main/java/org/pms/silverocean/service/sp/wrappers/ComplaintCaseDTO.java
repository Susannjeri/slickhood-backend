package org.pms.silverocean.service.sp.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.pms.silverocean.database.pms.entities.ComplaintCase;

import java.time.ZonedDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComplaintCaseDTO(
    long id, long serviceId, long bookingId, long filedByUserId, String description,
    String status, String adminNotes, String resolution, Long assignedAdminId, ZonedDateTime createdOn,
    String serviceName, String complaintCreatorName, String serviceCreatorName
) {
    public ComplaintCaseDTO(ComplaintCase c) {
        this(c.getId(), c.getServiceId(), c.getBookingId(), c.getFiledByUserId(), c.getDescription(),
             c.getStatus(), c.getAdminNotes(), c.getResolution(), c.getAssignedAdminId(), c.getCreatedOn(),
             null, null, null);
    }

    public ComplaintCaseDTO(ComplaintCase c, String serviceName, String complaintCreatorName, String serviceCreatorName) {
        this(c.getId(), c.getServiceId(), c.getBookingId(), c.getFiledByUserId(), c.getDescription(),
             c.getStatus(), c.getAdminNotes(), c.getResolution(), c.getAssignedAdminId(), c.getCreatedOn(),
             serviceName, complaintCreatorName, serviceCreatorName);
    }
}
