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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Table(name = "pms_lease", indexes = {
        @Index(name = "idx_lease_tenantId", columnList = "tenantId"),
        @Index(name = "idx_lease_active", columnList = "active"),
        @Index(name = "idx_lease_signedByManagerId", columnList = "signedByManagerId"),
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Lease extends BaseCreatorEntity implements Auditable {
    private long tenantId;

    private LocalDate leaseDate;
    private LocalDate moveInDate;
    private LocalDate moveOutDate;

    private double price;
    private String currency;

    private boolean charges;
    private boolean signed;

    private String name;
    private String leaseMode;

    private boolean selfRenew;
    private Integer leaseDurationInMonths;
    private Integer noticePeriodInMonths;
    private Integer depositReturnDays;
    private Integer rentDueDayOfMonth;
    private Double repairThreshold;

    private Integer entryNoticeDays;
    private LocalDateTime tenantSignedDate;
    private LocalDateTime managerSignedDate;
    private Long signedByManagerId;
    @Lob
    private byte[] petsPolicy;

    private boolean paymentDue;
    private LocalDate nextPaymentDate;
    private String lifecycleStatus = "DRAFT";
    private LocalDate terminationEffectiveDate;
    private String terminationReason;
    private Long terminationRequestedBy;
    private LocalDateTime terminationRequestedAt;
    private boolean governedDocumentRequired;

    @Override
    public String toAuditJSON() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"leaseDate\":\"" + leaseDate + "\"," +
                "\"moveInDate\":\"" + moveInDate + "\"," +
                "\"moveOutDate\":\"" + moveOutDate + "\"," +
                "\"price\":" + price + "," +
                "\"charges\":" + charges + "," +
                "\"signed\":" + signed + "," +
                "\"name\":" + name + "," +
                "\"leaseMode\":\"" + leaseMode + "\"," +
                "\"selfRenew\":" + selfRenew + "," +
                "\"leaseDurationInMonths\":" + leaseDurationInMonths + "," +
                "\"noticePeriodInMonths\":" + noticePeriodInMonths + "," +
                "\"depositReturnDays\":" + depositReturnDays + "," +
                "\"rentDueDayOfMonth\":" + rentDueDayOfMonth + "," +
                "\"repairThreshold\":" + repairThreshold + "," +
                "\"entryNoticeDays\":" + entryNoticeDays + "," +
                "\"petsPolicy\":\"" + new String(petsPolicy) + "\"," +
                "\"tenantId\":" + tenantId + "," +
                "\"active\":" + isActive() + "," +
                "\"createdOn\":\"" + getCreatedOn() + "\"," +
                "\"createdBy\":" + getCreatedBy() + "," +
                "\"signedByManagerId\":\"" + signedByManagerId + "\"," +
                "\"tenantSignedDate\":\"" + tenantSignedDate + "\"," +
                "\"managerSignedDate\":\"" + managerSignedDate + "\"," +
                "\"updatedOn\":\"" + getLastModifiedDate() + "\"," +
                "\"nextPaymentDate\":\"" +  nextPaymentDate + "\"," +
                "\"paymentDue\":" + paymentDue +
                "}";
    }

}
