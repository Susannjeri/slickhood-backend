package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

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
    @Column(name = "price_decimal", precision = 19, scale = 2)
    private BigDecimal priceDecimal;
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
    @Column(name = "repair_threshold_decimal", precision = 19, scale = 2)
    private BigDecimal repairThresholdDecimal;

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

    public BigDecimal moneyPrice(){return priceDecimal==null?org.pms.silverocean.service.payment.money.MonetaryPolicy.amount(price):org.pms.silverocean.service.payment.money.MonetaryPolicy.amount(priceDecimal);}
    public BigDecimal moneyRepairThreshold(){return repairThresholdDecimal==null?(repairThreshold==null?null:org.pms.silverocean.service.payment.money.MonetaryPolicy.amount(repairThreshold)):org.pms.silverocean.service.payment.money.MonetaryPolicy.amount(repairThresholdDecimal);}
    public void setMoneyPrice(BigDecimal value){priceDecimal=org.pms.silverocean.service.payment.money.MonetaryPolicy.amount(value);price=priceDecimal.doubleValue();}
    public void setMoneyRepairThreshold(BigDecimal value){repairThresholdDecimal=value==null?null:org.pms.silverocean.service.payment.money.MonetaryPolicy.amount(value);repairThreshold=repairThresholdDecimal==null?null:repairThresholdDecimal.doubleValue();}
    @PostLoad private void readMoneyShadows(){if(priceDecimal!=null)price=priceDecimal.doubleValue();if(repairThresholdDecimal!=null)repairThreshold=repairThresholdDecimal.doubleValue();}
    @PrePersist @PreUpdate private void writeMoneyShadows(){priceDecimal=org.pms.silverocean.service.payment.money.MonetaryPolicy.amount(price);repairThresholdDecimal=repairThreshold==null?null:org.pms.silverocean.service.payment.money.MonetaryPolicy.amount(repairThreshold);}

}
