package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.time.LocalDate;

@Entity @Table(name="pms_wealth_vault_document",indexes={@Index(name="idx_wealth_vault_asset",columnList="assetId,active"),@Index(name="idx_wealth_vault_owner",columnList="ownerUserId,active,createdOn")})
@Getter @Setter @NoArgsConstructor
public class WealthVaultDocument extends BaseCreatorEntity {
    private Long assetId;
    @Column(nullable=false) private long ownerUserId;
    @Column(nullable=false,length=50) private String category;
    @Column(nullable=false,length=255) private String displayName;
    @Column(nullable=false,length=800) private String fileRef;
    @Column(nullable=false,length=120) private String contentType;
    @Column(nullable=false) private long fileSize;
    @Column(nullable=false,length=64) private String checksumSha256;
    private LocalDate documentDate;
    private LocalDate expiryDate;
    @Column(length=500) private String notes;
}
