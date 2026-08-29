package org.pms.silverocean.database.pms.entities.base;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass
public class BaseCreatorEntity extends BaseActiveEntity {
    private Long createdBy;

    @LastModifiedDate
    private LocalDateTime lastModifiedDate;

    @PrePersist
    public void onCreate() {
        if (lastModifiedDate == null) {
            lastModifiedDate = LocalDateTime.now();
        }
    }

}
