package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.EstateBudget;
import org.springframework.data.domain.Page;import org.springframework.data.domain.Pageable;import org.springframework.data.jpa.repository.JpaRepository;
public interface EstateBudgetRepo extends JpaRepository<EstateBudget,Long>{Page<EstateBudget>findAllByPropertyIdAndActiveTrue(long propertyId,Pageable pageable);}
