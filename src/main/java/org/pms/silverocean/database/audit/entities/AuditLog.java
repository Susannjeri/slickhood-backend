package org.pms.silverocean.database.audit.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseIDEntity;

@Table(name = "pms_audit_log", indexes = {
        @Index(name ="idx_entity_name", columnList = "entityName"),
        @Index(name ="idx_action", columnList = "action"),
        @Index(name ="idx_user_name", columnList = "username")
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog extends BaseIDEntity {
    private String entityName;
    private Long entityId;
    private String action;
    @Column(columnDefinition = "text")
    private String description;
    @Column(columnDefinition = "longtext")
    private String value;
    private String username;
    private boolean success;

}
