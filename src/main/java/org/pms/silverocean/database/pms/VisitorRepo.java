package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.Visitor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface VisitorRepo extends JpaRepository<Visitor, Long> {
    @Query("SELECT DISTINCT v FROM Visitor v JOIN Property p ON p.id=v.propertyId WHERE v.active AND v.expectedArrivalTime >= :start AND v.expectedArrivalTime < :end AND " +
            "(:privileged=true OR v.createdBy=:userId OR v.hostUserId=:userId OR p.createdBy=:userId " +
            "OR EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=v.propertyId AND pm.userId=:userId AND pm.active) " +
            "OR EXISTS (SELECT 1 FROM PropertyOwnership po WHERE po.propertyId=v.propertyId AND po.homeownerUserId=:userId AND po.active)) " +
            "ORDER BY v.expectedArrivalTime DESC")
    List<Visitor> findForReport(long userId, boolean privileged, ZonedDateTime start, ZonedDateTime end, Pageable pageable);

    @Query("SELECT v FROM Visitor v WHERE v.unitId = :unitId AND v.active = true")
    Page<Visitor> findByUnitId(Pageable pageable, long unitId);

    @Query("SELECT v FROM Visitor v WHERE v.unitId = :unitId AND v.status = :status AND v.active = true")
    Page<Visitor> findByUnitIdAndStatus(Pageable pageable, long unitId, String status);

    @Query("SELECT v FROM Visitor v WHERE v.id=:visitorId AND (v.createdBy=:createdBy OR v.hostUserId=:createdBy) AND v.active=true")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Visitor> findByIdAndCreatedBy(long visitorId, long createdBy);

    @Query("SELECT v FROM Visitor v WHERE v.id=:visitorId AND v.hostUserId=:hostUserId AND v.active=true")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Visitor> findByIdAndHostUserId(long visitorId, long hostUserId);

    @Query("SELECT v FROM Visitor v WHERE v.credentialHash=:credentialHash AND v.active=true")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Visitor> findByCredentialHashForUpdate(String credentialHash);

    @Query("SELECT v FROM Visitor v WHERE v.unitId = :unitId AND v.phoneNumber=:visitorPhoneNumber AND v.expectedArrivalTime=:expectedArrivalTime")
    Optional<Visitor> findByUnitIdAndVisitorPhoneNumberAndExpectedArrivalTime(long unitId, String visitorPhoneNumber, ZonedDateTime expectedArrivalTime);


    @Query(value = """
            SELECT v.* FROM pms_visitor v JOIN pms_property p ON p.id=v.property_id
             WHERE v.active=1 AND p.active=1 AND (v.created_by=:userId OR v.host_user_id=:userId OR p.created_by=:userId
                OR EXISTS (SELECT 1 FROM pms_property_manager pm WHERE pm.property_id=p.id AND pm.user_id=:userId AND pm.active=1)
                OR EXISTS (SELECT 1 FROM pms_property_ownership po WHERE po.property_id=p.id AND po.homeowner_user_id=:userId
                    AND po.active=1 AND (po.unit_id IS NULL OR po.unit_id=v.unit_id)))
             ORDER BY v.id DESC
             LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Visitor> findByTenantOrLandlordOrGuardOrPropertyManager(@Param("limit") int limit, @Param("offset") long offset, long userId);

    @Query(value = """
            SELECT v.* FROM pms_visitor v JOIN pms_property p ON p.id=v.property_id
            WHERE v.active=1 AND p.active=1 AND v.phone_number=:phoneNumber
              AND (v.created_by=:userId OR v.host_user_id=:userId OR p.created_by=:userId
                OR EXISTS (SELECT 1 FROM pms_property_manager pm WHERE pm.property_id=p.id AND pm.user_id=:userId AND pm.active=1)
                OR EXISTS (SELECT 1 FROM pms_property_ownership po WHERE po.property_id=p.id AND po.homeowner_user_id=:userId
                    AND po.active=1 AND (po.unit_id IS NULL OR po.unit_id=v.unit_id)))
            ORDER BY v.id DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Visitor> findByTenantOrLandlordOrGuardOrPropertyManagerAndPhoneNumber(@Param("limit") int limit, @Param("offset") long offset, long userId, String phoneNumber);

    @Query("SELECT v FROM Visitor v WHERE v.id=:visitorId AND v.active=true AND EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId = v.propertyId AND pm.userId = :guardUserId AND pm.roleName=:guardRoleName AND pm.active=true)")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Visitor> findByIdAndGuard(long visitorId, long guardUserId, String guardRoleName);

    @Query("SELECT v FROM Visitor v WHERE v.status IN ('PENDING','PENDING_APPROVAL','APPROVED','ARRIVED') AND v.active=true " +
            "AND ((v.validUntil IS NOT NULL AND v.validUntil < :cutoff) OR (v.validUntil IS NULL AND v.expectedArrivalTime < :cutoff)) ORDER BY v.expectedArrivalTime ASC")
    List<Visitor> findExpiredPendingVisitors(@Param("cutoff") ZonedDateTime cutoff, Pageable pageable);

    @Query("SELECT COALESCE(COUNT(v), 0) FROM Visitor v JOIN PropertyManager pm ON v.propertyId=pm.propertyId" +
            " WHERE v.expectedArrivalTime > :start AND v.expectedArrivalTime < :end AND  v.category=:visitorCategory AND v.active AND pm.userId=:userId AND pm.roleName='GUARD' AND pm.active")
    int countVisitorExpectedWithinDateRangeByGuardAndVisitorCategory(ZonedDateTime start, ZonedDateTime end, long userId, String visitorCategory);

    @Query("SELECT COALESCE(COUNT(v), 0)  FROM Visitor v JOIN PropertyManager pm ON v.propertyId=pm.propertyId " +
            " WHERE v.status='CHECKED_IN' AND  v.active AND pm.userId=:userId AND pm.roleName='GUARD' AND pm.active")
    int countVisitorInsidePropertyByGuard(long userId);
}
