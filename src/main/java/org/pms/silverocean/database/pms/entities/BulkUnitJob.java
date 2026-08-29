package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;


@Table(name = "pms_bulk_unit_job")
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BulkUnitJob extends BaseCreatorEntity {
    private long unitId;
    private int count;
    private boolean completed;
    private String description;
    private String email;
}
