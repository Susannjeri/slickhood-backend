package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.LeaseDocumentTemplate;
import org.pms.silverocean.service.leasedocument.LeaseDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LeaseDocumentTemplateRepo extends JpaRepository<LeaseDocumentTemplate, Long> {
    Optional<LeaseDocumentTemplate> findFirstByDocumentTypeAndActiveTrueOrderByVersionDesc(LeaseDocumentType documentType);
    List<LeaseDocumentTemplate> findAllByActiveTrueOrderByDocumentTypeAscVersionDesc();
    boolean existsByDocumentTypeAndActiveTrue(LeaseDocumentType documentType);
}
