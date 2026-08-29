package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ParamRepo extends JpaRepository<Param, Long>, JpaSpecificationExecutor<Param> {
    Set<Param> findAllByCreatedByAndActiveTrue(Long userId);
    Optional<Param> findByCreatedByAndIdAndActiveTrue(long createdBy, long paramId);
    List<Param> findByNameAndActiveTrueAndCreatedBy(String paramName, long createdBy);

    @Query("SELECT DISTINCT(p.name) FROM Param p JOIN Users u ON p.createdBy=u.id WHERE p.active")
    Page<String> getUniqueParamName(Pageable pageable);

    @Query("SELECT DISTINCT(p.name) FROM Param p JOIN Users u ON p.createdBy=u.id WHERE p.active AND p.name like %:paramName% OR u.email like %:createdByEmail%")
    Page<String> getUniqueParamNameByNameOrEmail(Pageable pageable, String paramName, String createdByEmail);
    List<Param> findByNameAndActiveTrue(String paramName);
}
