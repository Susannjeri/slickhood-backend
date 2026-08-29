package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RoleRepo extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);
    @Query("SELECT r FROM Role r WHERE r.active")
    Page<Role> findAllActive(Pageable pageable);
    @Query("SELECT r FROM Role r WHERE r.active AND r.id=:roleId")
    Optional<Role> findByIdAndActive(long roleId);
}
