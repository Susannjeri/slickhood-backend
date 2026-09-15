package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.ProviderProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProviderProfileRepo extends JpaRepository<ProviderProfile, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProviderProfile p WHERE p.id=:id AND p.active=true")
    Optional<ProviderProfile> lockActiveProfile(long id);
    @Query("SELECT p FROM ProviderProfile p WHERE p.userId = :userId AND p.active = true AND p.status=:status")
    Optional<ProviderProfile> findByUserIdAndStatus(long userId, String status);

    @Query("SELECT p FROM ProviderProfile p WHERE p.userId = :userId AND p.active = true")
    Optional<ProviderProfile> findByUserId(long userId);

    @Query("SELECT COUNT(p) > 0 FROM ProviderProfile p WHERE p.userId = :userId AND p.active = true")
    boolean existsByUserId(long userId);
}
