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
@Table(name = "pms_sp_rating", indexes = {
        @Index(name = "idx_sp_rating_serviceId", columnList = "serviceId"),
        @Index(name = "idx_sp_rating_bookingId", columnList = "bookingId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ServiceRating extends BaseCreatorEntity implements Auditable {
    private long serviceId;
    private long bookingId;
    private long ratedByUserId;
    private int stars;
    private String comment;

    @Override
    public String toAuditJSON() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"serviceId\":" + serviceId + "," +
                "\"bookingId\":" + bookingId + "," +
                "\"ratedByUserId\":" + ratedByUserId + "," +
                "\"stars\":" + stars +
                "}";
    }
}
