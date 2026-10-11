package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.ComplaintCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.List;

public interface ComplaintCaseRepo extends JpaRepository<ComplaintCase, Long> {
    @Query("SELECT c FROM ComplaintCase c WHERE c.serviceId = :serviceId AND c.active = true")
    Page<ComplaintCase> findByServiceId(Pageable pageable, long serviceId);

    @Query("SELECT c FROM ComplaintCase c JOIN ServiceBooking sb ON c.bookingId=sb.id JOIN ProviderService ps ON c.serviceId=ps.id WHERE c.serviceId = :serviceId AND c.active = true" +
            " AND (c.createdBy=:userId OR sb.createdBy=:userId OR ps.createdBy=:userId OR" +
            " EXISTS(SELECT 1 FROM UserRole ur JOIN Role r ON ur.roleId=r.id WHERE ur.userId=:userId AND r.name=:roleName) )")
    Page<ComplaintCase> findByServiceIdAndCreatedByOrServiceOwnerOrSuperAdmin(Pageable pageable, long serviceId, long userId, String roleName);

    @Query("SELECT c FROM ComplaintCase c WHERE c.status IN ('OPEN','UNDER_REVIEW','ESCALATED') AND c.active = true")
    Page<ComplaintCase> findOpenAndUnderReview(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ComplaintCase c WHERE c.id = :id AND c.active = true")
    Optional<ComplaintCase> findByIdForUpdate(long id);

    @Query("SELECT COUNT(DISTINCT c.bookingId) FROM ComplaintCase c WHERE c.serviceId IN (SELECT s.id FROM ProviderService s WHERE s.profileId = :profileId) AND c.status = 'RESOLVED' AND c.resolution = 'UPHELD' AND c.active = true")
    int countUpheldComplaintsForProfile(long profileId);

    @Query("SELECT COUNT(c) > 0 FROM ComplaintCase c WHERE c.bookingId = :bookingId AND c.active = true")
    boolean existsComplaintForBooking(long bookingId);

    @Query("SELECT COUNT(c) > 0 FROM ComplaintCase c WHERE c.bookingId = :bookingId AND c.status IN ('OPEN','UNDER_REVIEW','ESCALATED') AND c.active = true")
    boolean existsOpenComplaintForBooking(long bookingId);

    @Query("SELECT c FROM ComplaintCase c WHERE c.bookingId = :bookingId AND c.active = true")
    Optional<ComplaintCase> findActiveByBookingId(long bookingId);

    @Query("SELECT c FROM ComplaintCase c WHERE c.bookingId IN :bookingIds AND c.active = true")
    List<ComplaintCase> findActiveByBookingIds(List<Long> bookingIds);
}
