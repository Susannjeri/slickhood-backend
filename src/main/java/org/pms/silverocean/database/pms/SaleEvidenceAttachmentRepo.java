package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.SaleEvidenceAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;import java.util.Optional;
public interface SaleEvidenceAttachmentRepo extends JpaRepository<SaleEvidenceAttachment,Long>{
 List<SaleEvidenceAttachment> findAllBySaleIdAndActiveTrueOrderByCreatedOnAsc(long saleId);
 Optional<SaleEvidenceAttachment> findByIdAndSaleIdAndActiveTrue(long id,long saleId);
 long countBySaleIdAndActiveTrue(long saleId);
}
