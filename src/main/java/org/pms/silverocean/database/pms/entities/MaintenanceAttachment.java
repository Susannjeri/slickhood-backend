package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

@Entity
@Table(name = "pms_maintenance_attachment", indexes = @Index(
        name = "idx_maintenance_attachment_order", columnList = "workOrderId,active,createdOn"))
@Getter @Setter @NoArgsConstructor
public class MaintenanceAttachment extends BaseCreatorEntity {
    @Column(nullable = false) private long workOrderId;
    @Column(nullable = false, length = 30) private String category;
    @Column(nullable = false, length = 255) private String displayName;
    @Column(nullable = false, length = 800) private String fileRef;
    @Column(nullable = false, length = 120) private String contentType;
    @Column(nullable = false) private long fileSize;
    @Column(nullable = false, length = 64) private String checksumSha256;
    @Column(nullable = false) private long uploadedByUserId;
}
