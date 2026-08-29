package org.pms.silverocean.database.pms.entities.base;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@MappedSuperclass
public class BaseIDEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, unique = true)
    private final UUID uuid = UUID.randomUUID();

    @CreationTimestamp
    private ZonedDateTime createdOn;

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseIDEntity that = (BaseIDEntity) o;
        return uuid.equals(that.uuid);
    }

}
