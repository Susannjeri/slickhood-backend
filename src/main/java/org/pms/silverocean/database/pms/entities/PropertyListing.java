package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_property_listing", indexes = {
        @Index(name = "idx_listing_public", columnList = "status,listingType,publishedAt"),
        @Index(name = "idx_listing_publisher", columnList = "publisherUserId,status")
})
@Getter @Setter
public class PropertyListing extends BaseCreatorEntity {
    @Column(nullable = false, unique = true)
    private long unitId;
    @Column(nullable = false, unique = true, length = 180)
    private String publicSlug;
    @Column(nullable = false, length = 12)
    private String listingType;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false, length = 180)
    private String headline;
    @Column(length = 2000)
    private String description;
    @Lob
    @Column(columnDefinition = "TEXT")
    private String imageManifest;
    @Column(nullable = false)
    private long publisherUserId;
    private ZonedDateTime publishedAt;
    private ZonedDateTime unpublishedAt;
    private ZonedDateTime expiresAt;
    private ZonedDateTime suspendedAt;
    private Long suspendedBy;
    @Column(length = 500)
    private String suspensionReason;
    @Version
    private long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", insertable = false, updatable = false)
    private Unit unit;
}
