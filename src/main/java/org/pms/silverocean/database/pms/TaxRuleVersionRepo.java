package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.TaxRuleVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TaxRuleVersionRepo extends JpaRepository<TaxRuleVersion, Long> {
    @Query("select r from TaxRuleVersion r where r.ruleCode=:code and r.active=true and r.effectiveFrom<=:onDate and (r.effectiveTo is null or r.effectiveTo>=:onDate) order by r.effectiveFrom desc, r.version desc")
    List<TaxRuleVersion> effectiveCandidates(@Param("code") String code, @Param("onDate") LocalDate onDate, Pageable pageable);
    List<TaxRuleVersion> findAllByOrderByRuleCodeAscEffectiveFromDesc();
    boolean existsByRuleCodeAndVersion(String ruleCode, int version);
}
