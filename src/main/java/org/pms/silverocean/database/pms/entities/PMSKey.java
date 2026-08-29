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

@Table(name = "pms_key", indexes = {
        @Index(name = "idx_key_active", columnList = "active")
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PMSKey extends BaseCreatorEntity {
    @Lob
    byte[] value;
}
