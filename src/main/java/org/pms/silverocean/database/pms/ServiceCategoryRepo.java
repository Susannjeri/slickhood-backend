package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.ServiceCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ServiceCategoryRepo extends JpaRepository<ServiceCategory, Long> {
    @Query("SELECT c FROM ServiceCategory c WHERE c.active = true ORDER BY c.name")
    Page<ServiceCategory> findAllActive(Pageable pageable);

    Optional<ServiceCategory> findByNameAndActive(String name, boolean active);
}
