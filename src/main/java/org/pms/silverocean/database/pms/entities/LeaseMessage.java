package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

@Table(name = "pms_lease_message", indexes = {
        @Index(name = "idx_lease_message_leaseId", columnList = "leaseId")
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LeaseMessage extends BaseCreatorEntity implements Auditable {
    @Lob
    private byte[] message;
    private long leaseId;


    @Override
    public String toAuditJSON() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"message\":\"******\"," +
                "\"leaseId\":" + leaseId + "," +
                "\"createdBy\":" + getCreatedBy() + "," +
                "\"createdOn\":" + getCreatedOn() + "," +
                "\"active\":" + isActive() +
                "}";
    }
}
