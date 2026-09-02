package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseActiveEntity;
import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_property_listing_inquiry", indexes = {
        @Index(name = "idx_listing_inquiry_queue", columnList = "listingId,status,createdOn")
})
@Getter @Setter
public class PropertyListingInquiry extends BaseActiveEntity {
    @Column(name = "listing_id", nullable = false)
    private long listingId;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 180)
    private String email;
    @Column(length = 40)
    private String phone;
    @Column(nullable = false, length = 1000)
    private String message;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false, length = 64)
    private String fingerprintHash;
    @Column(nullable = false, length = 40)
    private String consentVersion;
    @Column(nullable = false)
    private ZonedDateTime consentedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", insertable = false, updatable = false)
    private PropertyListing listing;
}
