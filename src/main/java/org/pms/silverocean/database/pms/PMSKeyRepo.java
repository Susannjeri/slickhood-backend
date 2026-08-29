package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.PMSKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Set;

public interface PMSKeyRepo extends JpaRepository<PMSKey, Long> {
    Optional<PMSKey> findByActiveTrue();

    @Query("SELECT pk.value FROM PMSKey pk WHERE pk.active=false")
    Set<byte[]> getAllInactiveKeys();

}
