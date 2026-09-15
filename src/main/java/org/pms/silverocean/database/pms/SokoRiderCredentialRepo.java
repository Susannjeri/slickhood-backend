package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.SokoRiderCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface SokoRiderCredentialRepo extends JpaRepository<SokoRiderCredential,Long> {
    List<SokoRiderCredential> findAllByUserIdAndActiveTrueOrderByCreatedOnDesc(long userId);
    List<SokoRiderCredential> findAllByUserIdAndStatusAndActiveTrue(long userId,String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SokoRiderCredential c where c.id=:id and c.active=true")
    Optional<SokoRiderCredential> findByIdForUpdate(long id);
}
