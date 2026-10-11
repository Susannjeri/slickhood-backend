package org.pms.silverocean.service.sp.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.pms.silverocean.database.pms.entities.ServiceRating;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceRatingDTO(long id, long serviceId, int stars, String comment, java.time.ZonedDateTime createdOn) {
    public ServiceRatingDTO(ServiceRating r) {
        this(r.getId(), r.getServiceId(), r.getStars(), r.getComment(), r.getCreatedOn());
    }
}
