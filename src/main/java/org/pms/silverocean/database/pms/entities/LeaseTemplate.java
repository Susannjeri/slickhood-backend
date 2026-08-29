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

@Table(name = "pms_lease_template", indexes = {
        @Index(name = "idx_lease_template_userid", columnList = "createdBy")
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LeaseTemplate extends BaseCreatorEntity implements Auditable {
    private String name;
    private String leaseMode;

    private boolean selfRenew;
    private Integer leaseDurationInMonths;
    private Integer noticePeriodInMonths;
    private Integer depositReturnDays;
    private Integer rentDueDayOfMonth;
    private Double repairThreshold;

    private Integer entryNoticeDays;

    @Lob
    private byte[] petsPolicy;

    @Override
    public String toAuditJSON() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"name\":\"" + name + "\"," +
                "\"leaseMode\":\"" + leaseMode + "\"," +
                "\"selfRenew\":" + selfRenew + "," +
                "\"leaseDurationInMonths\":" + leaseDurationInMonths + "," +
                "\"noticePeriodInMonths\":" + noticePeriodInMonths + "," +
                "\"depositReturnDays\":" + depositReturnDays + "," +
                "\"rentDueDayOfMonth\":" + rentDueDayOfMonth + "," +
                "\"repairThreshold\":" + repairThreshold + "," +
                "\"entryNoticeDays\":" + entryNoticeDays + "," +
                "\"petsPolicy\":\"" + new String(petsPolicy) + "\"," +
                "\"active\":" + isActive() + "," +
                "\"createdBy\":" + getCreatedBy() + "," +
                "\"createdOn\":\"" + getCreatedOn() + "\"" +
                "}";
    }

    public Lease initLeaseFromTemplate() {
        Lease lease = new Lease();
        lease.setName(this.name);
        lease.setLeaseMode(this.leaseMode);
        lease.setSelfRenew(this.selfRenew);
        lease.setLeaseDurationInMonths(this.leaseDurationInMonths);
        lease.setNoticePeriodInMonths(this.noticePeriodInMonths);
        lease.setDepositReturnDays(this.depositReturnDays);
        lease.setRentDueDayOfMonth(this.rentDueDayOfMonth);
        lease.setRepairThreshold(this.repairThreshold);
        lease.setEntryNoticeDays(this.entryNoticeDays);
        lease.setPetsPolicy(this.getPetsPolicy());
        return lease;
    }
}
