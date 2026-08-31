package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

@Entity
@Table(name = "pms_document_branding", uniqueConstraints =
        @UniqueConstraint(name = "uk_document_branding_owner", columnNames = "ownerUserId"))
@Getter @Setter @NoArgsConstructor
public class DocumentBranding extends BaseCreatorEntity {
    @Column(nullable = false) private long ownerUserId;
    @Column(nullable = false, length = 40) private String logoMimeType;
    @Column(nullable = false, length = 64) private String logoSha256;
    @Lob @Column(nullable = false, columnDefinition = "MEDIUMBLOB") private byte[] logoContent;
}
