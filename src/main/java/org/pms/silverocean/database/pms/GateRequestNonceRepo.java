package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.GateRequestNonce;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

public interface GateRequestNonceRepo extends JpaRepository<GateRequestNonce, Long> {
    @Modifying
    @Transactional
    @Query("DELETE FROM GateRequestNonce n WHERE n.expiresAt < :cutoff")
    int deleteExpired(ZonedDateTime cutoff);
}
