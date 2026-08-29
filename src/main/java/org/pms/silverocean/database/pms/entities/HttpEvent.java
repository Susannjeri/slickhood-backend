package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseIDEntity;

@Table(name = "pms_events", indexes = {
        @Index(name ="idx_event_type", columnList = "eventType"),
        @Index(name ="idx_event_type_date", columnList = "eventType, createdOn"),
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HttpEvent extends BaseIDEntity {
    private String eventType;
    @Lob
    private byte[] event;
    private Long tId;
    private int httpStatusCode;
}
