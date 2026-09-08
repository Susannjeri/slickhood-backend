package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Table(name = "pms_invite", indexes = {
        @Index(name = "idx_invite_token", columnList = "token"),
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Invite extends BaseCreatorEntity implements Auditable {
    @Column(columnDefinition = "BIGINT DEFAULT NULL")
    private Long entityId;
    private String type;
    @Column(columnDefinition = "BIGINT DEFAULT NULL")
    private Long roleId;
    @Column(length = 254)
    private String recipient;
    private String token;
    private LocalDateTime expiryDate;
    private LocalDate leaseStartDate;
    private LocalDate leaseEndDate;
    private Long agreementTemplateId;
    private int visits = 0;

    @Override
    public String toAuditJSON() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"expiryDate\":\"" + expiryDate + "\"," +
                "\"visits\":" + visits + "," +
                "\"entityId\":" + getEntityId() + "," +
                "\"type\":\"" + type + "\"," +
                "\"invitedRoleId\":\"" + roleId + "\"," +
                "\"leaseStartDate\":\"" + leaseStartDate + "\"," +
                "\"leaseEndDate\":\"" + leaseEndDate + "\"," +
                "\"agreementTemplateId\":\"" + agreementTemplateId + "\"," +
                "\"createdOn\":\"" + getCreatedOn() + "\"," +
                "\"createdBy\":" + getCreatedBy() + "," +
                "\"updatedOn\":\"" + getLastModifiedDate() + "\"" +
                "}";
    }
}
