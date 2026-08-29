package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.PrivacyRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Optional;

public interface PrivacyRequestRepo extends JpaRepository<PrivacyRequest, Long> {
    boolean existsByUserIdAndRequestTypeAndStatusInAndActiveTrue(long userId, String requestType, Collection<String> statuses);
    Page<PrivacyRequest> findByUserIdAndActiveTrueOrderByCreatedOnDesc(long userId, Pageable pageable);
    Page<PrivacyRequest> findByActiveTrueOrderByCreatedOnDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PrivacyRequest r WHERE r.id=:id AND r.active=true")
    Optional<PrivacyRequest> findByIdForUpdate(long id);
}
