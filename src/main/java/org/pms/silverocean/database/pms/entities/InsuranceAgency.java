package org.pms.silverocean.database.pms.entities;
import jakarta.persistence.*;
import lombok.*;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
@Entity @Table(name="pms_insurance_agency") @Getter @Setter @NoArgsConstructor
public class InsuranceAgency extends BaseCreatorEntity {
 @Column(nullable=false,unique=true,length=40) private String code;
 @Column(nullable=false,length=160) private String name;
 @Column(length=254) private String supportEmail;
 @Column(length=40) private String supportPhone;
 @Column(length=800) private String logoUrl;
}
