package org.pms.silverocean.database.pms.entities.base;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public class BaseActiveEntity extends BaseIDEntity {
    private boolean active;

}
