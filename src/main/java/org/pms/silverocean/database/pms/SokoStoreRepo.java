package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.SokoStore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SokoStoreRepo extends JpaRepository<SokoStore, Long> {
    Optional<SokoStore> findByIdAndActiveTrue(long id);
    Optional<SokoStore> findByIdAndOwnerUserIdAndActiveTrue(long id, long ownerUserId);
    List<SokoStore> findAllByOwnerUserIdAndActiveTrueOrderByName(long ownerUserId);
    Page<SokoStore> findAllByActiveTrue(Pageable pageable);
    Page<SokoStore> findAllByStatusAndActiveTrue(String status,Pageable pageable);
    long countByActiveTrue();
    long countByStatusAndActiveTrue(String status);
}
