package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.InsuranceCompany;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InsuranceCompanyRepo extends JpaRepository<InsuranceCompany, Long> {
    List<InsuranceCompany> findByActiveTrueOrderByNameAsc();
    Optional<InsuranceCompany> findByCodeIgnoreCaseAndActiveTrue(String code);
}
