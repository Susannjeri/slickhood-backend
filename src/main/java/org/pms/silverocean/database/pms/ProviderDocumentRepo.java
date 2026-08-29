package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.ProviderDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Set;

public interface ProviderDocumentRepo extends JpaRepository<ProviderDocument, Long> {
    @Query("SELECT d FROM ProviderDocument d WHERE d.serviceId = :serviceId AND d.active = true ORDER BY d.createdOn DESC")
    Page<ProviderDocument> findByServiceId(long serviceId, Pageable pageable);

    @Query("SELECT d FROM ProviderDocument d WHERE d.id = :id AND d.serviceId = :serviceId AND d.active = true")
    Optional<ProviderDocument> findByIdAndServiceId(long id, long serviceId);

    @Query("SELECT d.documentType FROM ProviderDocument d WHERE d.serviceId = :serviceId AND d.active = true")
    Set<String> findUploadedDocumentTypesByServiceId(long serviceId);

    @Query("SELECT d.documentType FROM ProviderDocument d WHERE d.serviceId = :serviceId AND d.verificationStatus = 'VERIFIED' AND d.active = true")
    Set<String> findVerifiedDocumentTypesByServiceId(long serviceId);
}
