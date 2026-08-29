package org.pms.silverocean.service.sp.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.pms.silverocean.database.pms.entities.ServiceRating;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceRatingDTO(long id, long serviceId, long bookingId, int stars, String comment, long ratedByUserId) {
    public ServiceRatingDTO(ServiceRating r) {
        this(r.getId(), r.getServiceId(), r.getBookingId(), r.getStars(), r.getComment(), r.getRatedByUserId());
    }
}
