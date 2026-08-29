package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.LeaseDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import org.pms.silverocean.service.leasedocument.LeaseDocumentStatus;

public interface LeaseDocumentRepo extends JpaRepository<LeaseDocument, Long> {
    @Query("SELECT d FROM LeaseDocument d WHERE d.id=:id AND d.active AND (d.issuerUserId=:userId OR d.recipientUserId=:userId)")
    Optional<LeaseDocument> findAccessible(long id, long userId);
    @Query("SELECT d FROM LeaseDocument d WHERE d.active AND (d.issuerUserId=:userId OR d.recipientUserId=:userId) ORDER BY d.createdOn DESC")
    List<LeaseDocument> findAllAccessible(long userId);
    long countByRecipientUserIdAndStatusAndActiveTrue(long userId, LeaseDocumentStatus status);
}
