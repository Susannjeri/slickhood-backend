package org.pms.silverocean.service.estate;

import java.time.LocalDate;

public record OwnershipView(long id, long propertyId, String propertyName, Long unitId, String unitRef,
                            long homeownerUserId, String homeownerName, String homeownerEmail,
                            LocalDate ownershipStart, LocalDate ownershipEnd, String source,
                            boolean active, String terminationReason) {}
