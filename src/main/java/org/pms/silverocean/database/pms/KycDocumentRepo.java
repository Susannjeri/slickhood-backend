package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.ZonedDateTime;

public interface KycDocumentRepo extends JpaRepository<KycDocument, Long> {
    List<KycDocument> findByCaseIdAndActiveTrueOrderByCreatedOnDesc(long caseId);
    Optional<KycDocument> findByIdAndUserId(long id, long userId);
    boolean existsByUserIdAndSha256AndActiveTrue(long userId, String sha256);
    List<KycDocument> findByActiveTrueAndReverificationDueAtLessThanEqualAndStatusNot(
            ZonedDateTime dueAt, String status);
}
