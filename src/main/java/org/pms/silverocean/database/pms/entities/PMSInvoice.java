package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseActiveEntity;
import java.time.LocalDate;

@Table(name = "pms_invoice", indexes = {
        @Index(name = "idx_invoice_unit_id", columnList = "unitId"),
        @Index(name = "idx_invoice_property_id", columnList = "propertyId"),
        @Index(name = "idx_invoice_ref", columnList = "ref", unique = true),
        @Index(name = "idx_invoice_billed_user_id", columnList = "billedUserId"),
        @Index(name = "idx_invoice_pay_to_user_id", columnList = "payToUserId"),
        @Index(name = "idx_filter", columnList = "ref, billedUserId, payToUserId, propertyId")
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PMSInvoice extends BaseActiveEntity {
    private long unitId;
    private long propertyId;
    private Long paymentAccountId;
    private String ref;
    private double amount;
    private String currency;
    @Lob
    private byte[] description;
    @Lob
    private byte[] htmlDescription;
    private long billedUserId;
    private long payToUserId;
    private boolean paid = false;
    private double pendingAmount;
    private String customerPhoneNumber;
    private String customerEmail;
    private boolean transactionInProgress = false;

    /** When set, this invoice is for a paid subscription plan checkout; activation runs when fully paid. */
    private String subscriptionPlanCode;
    /** RENTAL, SERVICE_CHARGE, SALE or SUBSCRIPTION. Drives presentation only; payment routing remains account based. */
    private String billingType;
    private LocalDate dueDate;

}
