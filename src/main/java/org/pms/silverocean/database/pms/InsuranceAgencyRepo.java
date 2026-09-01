package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.InsuranceAgency;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface InsuranceAgencyRepo extends JpaRepository<InsuranceAgency,Long>{Optional<InsuranceAgency> findByCodeAndActiveTrue(String code);}
