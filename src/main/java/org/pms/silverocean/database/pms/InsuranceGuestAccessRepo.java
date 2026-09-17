package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.InsuranceGuestAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface InsuranceGuestAccessRepo extends JpaRepository<InsuranceGuestAccess, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from InsuranceGuestAccess g where g.challengeId=:challengeId and g.active=true")
    Optional<InsuranceGuestAccess> findChallengeForUpdate(String challengeId);

    Optional<InsuranceGuestAccess> findByAccessTokenHashAndActiveTrue(String accessTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from InsuranceGuestAccess g where g.accessTokenHash=:accessTokenHash and g.active=true")
    Optional<InsuranceGuestAccess> findAccessForUpdate(String accessTokenHash);

    Optional<InsuranceGuestAccess> findByIdAndActiveTrue(long id);

    Optional<InsuranceGuestAccess> findByChallengeIdAndActiveTrue(String challengeId);
}
