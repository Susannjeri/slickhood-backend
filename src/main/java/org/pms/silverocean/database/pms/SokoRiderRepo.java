package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.SokoRider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SokoRiderRepo extends JpaRepository<SokoRider,Long> {
    List<SokoRider> findAllByStoreIdAndActiveTrueOrderByDisplayName(long storeId);
    Optional<SokoRider> findByIdAndStoreIdAndActiveTrue(long id,long storeId);
    boolean existsByStoreIdAndPhoneNumberAndActiveTrue(long storeId,String phoneNumber);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SokoRider r where r.id=:id and r.storeId=:storeId and r.active=true")
    Optional<SokoRider> findForUpdate(long id,long storeId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SokoRider r where r.id=:id and r.active=true")
    Optional<SokoRider> findByIdForUpdate(long id);
    Page<SokoRider> findAllByActiveTrue(Pageable pageable);
    List<SokoRider> findAllByUserIdAndActiveTrue(long userId);
    List<SokoRider> findAllByEmailIgnoreCaseAndActiveTrue(String email);
}
