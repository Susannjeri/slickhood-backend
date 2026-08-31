package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.InsuranceDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface InsuranceDocumentRepo extends JpaRepository<InsuranceDocument,Long>{
 List<InsuranceDocument> findAllByCustomerUserIdAndActiveTrueOrderByCreatedOnDesc(long userId);
 List<InsuranceDocument> findAllByCaseIdAndActiveTrueOrderByCreatedOnDesc(long caseId);
 List<InsuranceDocument> findAllByPolicyIdAndActiveTrueOrderByCreatedOnDesc(long policyId);
 List<InsuranceDocument> findAllByClaimIdAndActiveTrueOrderByCreatedOnDesc(long claimId);
 Optional<InsuranceDocument> findByIdAndActiveTrue(long id);
 long countByCaseIdAndCategoryAndActiveTrue(long caseId,String category);
}
