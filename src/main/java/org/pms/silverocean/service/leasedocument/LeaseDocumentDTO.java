package org.pms.silverocean.service.leasedocument;

import org.pms.silverocean.database.pms.entities.LeaseDocument;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LeaseDocumentDTO(Long id, Long leaseId, Long saleId, Long propertyId, Long unitId,
        LeaseDocumentType documentType, LeaseDocumentStatus status, String name, int templateVersion,
        long issuerUserId, long recipientUserId, LocalDate effectiveDate, LocalDate responseDueDate,
        BigDecimal amount, String currency, String reason, String deliveryChannel, boolean legalReviewRequired,
        LocalDateTime issuedAt, LocalDateTime acknowledgedAt, LocalDateTime issuerSignedAt, LocalDateTime recipientSignedAt) {
    public LeaseDocumentDTO(LeaseDocument d) {
        this(d.getId(), d.getLeaseId(), d.getSaleId(), d.getPropertyId(), d.getUnitId(), d.getDocumentType(), d.getStatus(),
                d.getName(), d.getTemplateVersion(), d.getIssuerUserId(), d.getRecipientUserId(), d.getEffectiveDate(),
                d.getResponseDueDate(), d.getAmount(), d.getCurrency(), d.getReason(), d.getDeliveryChannel(),
                d.isLegalReviewRequired(), d.getIssuedAt(), d.getAcknowledgedAt(), d.getIssuerSignedAt(), d.getRecipientSignedAt());
    }
}
