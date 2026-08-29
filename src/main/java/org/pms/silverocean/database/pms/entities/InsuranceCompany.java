package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

@Entity
@Table(name = "pms_insurance_company")
@Getter
@Setter
public class InsuranceCompany extends BaseCreatorEntity {
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 800)
    private String logoUrl;

    @Column(length = 1000)
    private String description;

    @Column(length = 254)
    private String quotationEmail;

    @Column(length = 254)
    private String claimsEmail;

    @Column(length = 254)
    private String renewalsEmail;
}
