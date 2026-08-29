package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.Referee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RefereeRepo extends JpaRepository<Referee, Long> {
    @Query("SELECT r FROM Referee r WHERE r.profileId = :profileId AND r.active = true ORDER BY r.createdOn DESC")
    Page<Referee> findByProfileId(long profileId, Pageable pageable);

    @Query("SELECT r FROM Referee r WHERE r.id = :id AND r.profileId = :profileId AND r.active = true")
    Optional<Referee> findByIdAndProfileId(long id, long profileId);

    @Query("SELECT COUNT(r) FROM Referee r WHERE r.profileId = :profileId AND r.active = true")
    int countByProfileId(long profileId);

    @Query("SELECT COUNT(r) FROM Referee r WHERE r.profileId = :profileId AND r.verificationStatus = 'CONFIRMED' AND r.active = true")
    int countVerifiedByProfileId(long profileId);
}
