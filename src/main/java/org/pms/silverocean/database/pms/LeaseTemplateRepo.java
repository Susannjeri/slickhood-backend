package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.LeaseTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface LeaseTemplateRepo extends JpaRepository<LeaseTemplate, Long> {
    @Query("SELECT lt FROM LeaseTemplate lt WHERE lt.active AND (lt.createdBy=:createdBy OR lt.name=:rentLeaseName OR lt.name=:saleLeaseName)")
    Page<LeaseTemplate> findLeaseTemplateByCreatedByAndActiveTrue(Pageable pageable, long createdBy, String rentLeaseName, String saleLeaseName);
    @Query("SELECT lt FROM LeaseTemplate lt WHERE lt.active AND lt.leaseMode=:leaseMode AND (lt.createdBy=:createdBy OR lt.name=:rentLeaseName OR lt.name=:saleLeaseName)")
    Page<LeaseTemplate> findLeaseTemplateByCreatedByAndActiveTrueAndLeaseMode(Pageable pageable, long createdBy, String rentLeaseName, String saleLeaseName, String leaseMode);
    Optional<LeaseTemplate> findByNameAndActiveTrue(String name);
    Optional<LeaseTemplate> findByIdAndActiveTrue(long id);
    Optional<LeaseTemplate> findByNameAndCreatedByAndActiveTrue(String name, long userId);
    Optional<LeaseTemplate> findByIdAndCreatedByAndActiveTrue(long id, long createdBy);

    @Query("SELECT lt FROM LeaseTemplate lt WHERE lt.active AND lt.id=:templateId AND (lt.createdBy=:userId OR EXISTS (SELECT 1 FROM PropertyManager pm JOIN Unit u ON pm.propertyId=u.propertyId WHERE pm.userId=:userId AND pm.active AND u.templateId=lt.id))")
    Optional<LeaseTemplate>  findByIdAndStaffOrOwner(Long templateId, long userId);
}
