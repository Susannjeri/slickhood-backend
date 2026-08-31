package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.DocumentBranding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DocumentBrandingRepo extends JpaRepository<DocumentBranding, Long> {
    Optional<DocumentBranding> findByOwnerUserIdAndActiveTrue(long ownerUserId);
}
