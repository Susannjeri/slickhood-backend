package org.pms.silverocean.database.pms.entities;


import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseActiveEntity;

@Table(name = "pms_charge_type")
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChargeType extends BaseActiveEntity {
    private String name;
}
