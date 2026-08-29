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

    @Query("SELECT v FROM Visitor v WHERE v.id = :visitorId AND v.createdBy = :createdBy AND v.active = true")
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
            SELECT v.* FROM pms_visitor v
                           JOIN (
                                                 SELECT v1.id FROM pms_visitor v1 JOIN pms_property p1 ON v1.property_id = p1.id
                                                 WHERE v1.created_by = :userId
                                                 UNION
                                                 SELECT v2.id FROM pms_visitor v2 JOIN pms_property p2 ON v2.property_id = p2.id
                                                 WHERE p2.created_by = :userId
                                                 UNION
                                                 SELECT v3.id FROM pms_visitor v3 JOIN pms_property p3 ON v3.property_id = p3.id
                                                 WHERE EXISTS (
                                                     SELECT 1 FROM pms_property_manager pm WHERE pm.property_id = p3.id AND pm.user_id = :userId)
                                             ) AS allowed_ids ON v.id = allowed_ids.id
             WHERE v.active = 1
             ORDER BY id DESC
             LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Visitor> findByTenantOrLandlordOrGuardOrPropertyManager(@Param("limit") int limit, @Param("offset") long offset, long userId);

    @Query(value = """
            SELECT v.* FROM pms_visitor v
                       JOIN (
                                                 SELECT v1.id FROM pms_visitor v1 JOIN pms_property p1 ON v1.property_id = p1.id
                                                 WHERE v1.created_by = :userId
                                                 UNION
                                                 SELECT v2.id FROM pms_visitor v2 JOIN pms_property p2 ON v2.property_id = p2.id
                                                 WHERE p2.created_by = :userId
                                                 UNION
                                                 SELECT v3.id FROM pms_visitor v3 JOIN pms_property p3 ON v3.property_id = p3.id
                                                 WHERE EXISTS (
                                                     SELECT 1 FROM pms_property_manager pm WHERE pm.property_id = p3.id AND pm.user_id = :userId)
                                             ) AS allowed_ids ON v.id = allowed_ids.id
            WHERE v.active = 1 AND v.phone_number like CONCAT('%', :phoneNumber, '%')
            ORDER BY id DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Visitor> findByTenantOrLandlordOrGuardOrPropertyManagerAndPhoneNumber(@Param("limit") int limit, @Param("offset") long offset, long userId, String phoneNumber);

    @Query("SELECT v FROM Visitor v WHERE v.id=:visitorId AND EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId = v.propertyId AND pm.userId = :guardUserId AND pm.roleName=:guardRoleName)")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Visitor> findByIdAndGuard(long visitorId, long guardUserId, String guardRoleName);

    @Query("SELECT v FROM Visitor v WHERE v.status = 'PENDING' AND v.expectedArrivalTime < :cutoff AND v.active = true ORDER BY v.expectedArrivalTime ASC")
    List<Visitor> findExpiredPendingVisitors(@Param("cutoff") ZonedDateTime cutoff, Pageable pageable);

    @Query("SELECT COALESCE(COUNT(v), 0) FROM Visitor v JOIN PropertyManager pm ON v.propertyId=pm.propertyId" +
            " WHERE v.expectedArrivalTime > :start AND v.expectedArrivalTime < :end AND  v.category=:visitorCategory AND v.active AND pm.userId=:userId AND pm.roleName='GUARD' AND pm.active")
    int countVisitorExpectedWithinDateRangeByGuardAndVisitorCategory(ZonedDateTime start, ZonedDateTime end, long userId, String visitorCategory);

    @Query("SELECT COALESCE(COUNT(v), 0)  FROM Visitor v JOIN PropertyManager pm ON v.propertyId=pm.propertyId " +
            " WHERE v.status='CHECKED_IN' AND  v.active AND pm.userId=:userId AND pm.roleName='GUARD' AND pm.active")
    int countVisitorInsidePropertyByGuard(long userId);
}
