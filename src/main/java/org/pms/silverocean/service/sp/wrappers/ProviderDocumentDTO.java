package org.pms.silverocean.service.sp.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.pms.silverocean.database.pms.entities.ProviderDocument;

import java.time.ZonedDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderDocumentDTO(long id, long serviceId, String documentType,
                                  String downloadUrl, ZonedDateTime expiryDate, String verificationStatus) {
    public ProviderDocumentDTO(ProviderDocument d, String downloadUrl) {
        this(d.getId(), d.getServiceId(), d.getDocumentType(), downloadUrl, d.getExpiryDate(), d.getVerificationStatus());
    }
}
