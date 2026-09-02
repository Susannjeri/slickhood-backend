package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_tax_connection_request")
@Getter @Setter @NoArgsConstructor
public class TaxConnectionRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private long ownerUserId;
    @Column(nullable = false, length = 30) private String provider;
    @Column(nullable = false, length = 20) private String taxpayerPinMasked;
    @Column(nullable = false, length = 500) private String requestedScopes;
    @Column(nullable = false, length = 40) private String consentVersion;
    @Column(nullable = false) private ZonedDateTime consentedAt;
    @Column(nullable = false, length = 40) private String status;
    @Column(nullable = false, length = 20) private String environment = "SANDBOX";
    private Long reviewedBy;
    private ZonedDateTime reviewedAt;
    @Column(length = 1000) private String reviewNote;
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false, insertable = false, updatable = false) private ZonedDateTime createdOn;
    @Column(nullable = false, insertable = false, updatable = false) private ZonedDateTime updatedOn;
}
