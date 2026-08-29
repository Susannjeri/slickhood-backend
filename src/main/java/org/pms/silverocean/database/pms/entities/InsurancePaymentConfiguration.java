package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;

import java.time.LocalDate;

@Entity
@Table(name = "pms_insurance_payment_configuration", indexes = {
        @Index(name = "idx_insurance_payment_company", columnList = "companyId, active"),
        @Index(name = "idx_insurance_payment_account", columnList = "paymentAccountId")
})
@Getter
@Setter
public class InsurancePaymentConfiguration extends BaseCreatorEntity {
    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private Long paymentAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentChannel paymentChannel;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(nullable = false, length = 1500)
    private String instructions;

    @Column(length = 240)
    private String referenceTemplate;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;
}
