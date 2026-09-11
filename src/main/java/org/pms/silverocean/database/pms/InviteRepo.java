package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.Invite;
import org.pms.silverocean.database.pms.entities.Lease;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.service.lease.LeaseInviteProjection;
import org.pms.silverocean.service.invites.PendingTenantInviteProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

public interface InviteRepo extends JpaRepository<Invite, Long> {
    Optional<Invite> findByTokenAndActive(String token, boolean active);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invite i WHERE i.token=:token AND i.active")
    Optional<Invite> findActiveTokenForUpdate(String token);
    Optional<Invite> findFirstByRecipientIgnoreCaseAndActiveTrueAndExpiryDateAfterAndRoleIdIsNotNullOrderByCreatedOnDesc(
            String recipient, LocalDateTime now);
    Optional<Invite> findFirstByRecipientIgnoreCaseAndTypeAndActiveTrueAndExpiryDateAfterOrderByCreatedOnDesc(
            String recipient, String type, LocalDateTime now);
    @Query("SELECT i.id as inviteId, i.token as token, i.entityId as unitId, u.ref as unitRef, " +
            "p.name as propertyName, i.leaseStartDate as leaseStartDate, i.leaseEndDate as leaseEndDate, " +
            "i.expiryDate as expiryDate FROM Invite i JOIN Unit u ON i.entityId=u.id " +
            "JOIN Property p ON u.propertyId=p.id WHERE i.recipient=:recipient AND i.type='TENANT' " +
            "AND i.active AND i.expiryDate>:now AND u.active AND p.active ORDER BY i.createdOn DESC")
    List<PendingTenantInviteProjection> findPendingTenantInvites(String recipient, LocalDateTime now);

    @Query("SELECT COUNT(DISTINCT i.entityId) FROM Invite i WHERE i.recipient=:recipient " +
            "AND i.type='TENANT' AND i.active AND i.expiryDate>:now")
    int countPendingTenantInvites(String recipient, LocalDateTime now);
    Optional<Invite> findByIdAndActiveTrueAndCreatedBy(long inviteId, long createdBy);
    Optional<Invite> findByIdAndActiveTrue(long inviteId);
    @Query("SELECT li.id as inviteId, li.entityId as entityId, u.ref as unitRef, p.name as propertyName," +
            " li.lastModifiedDate as lastAccessed, li.expiryDate as expiryDate, li.visits as visits, li.token as token" +
            " FROM Invite li JOIN Unit u ON li.entityId=u.id JOIN Property p ON u.propertyId=p.id WHERE li.createdBy=:createdBy AND li.active AND li.type=:type")
    Page<LeaseInviteProjection> findByCreator(Pageable pageable, long createdBy, String type);

    @Query("SELECT i FROM Invite i WHERE i.createdBy=:createdBy AND i.active")
    Page<Invite> findByCreator(Pageable pageable, long createdBy);

    @Query("SELECT i FROM Invite i WHERE i.createdBy=:createdBy AND i.active AND i.type=:inviteType AND i.entityId=:unitId")
    Page<Invite> findByUnitAndCreatorAndType(Pageable pageable, long createdBy, long unitId, String inviteType);

    @Query("SELECT u FROM Invite i JOIN Unit u ON i.entityId=u.id WHERE i.token=:token AND i.type=:inviteType AND u.active AND i.active")
    Optional<Unit> findUnitFromToken(String token, String inviteType);

//    @Query("SELECT l FROM Invite i JOIN UnitTenant ut ON i.id=ut.inviteId JOIN Lease l ON ut.id=l.tenantId WHERE i.token=:token AND ut.userId=:userId AND ut.active AND l.active")
//    Optional<Lease> findByInviteTokenAndActiveAndUserId(String token, long userId);

    @Query("SELECT l FROM UnitTenant ut JOIN Lease l ON ut.id=l.tenantId WHERE ut.unitId=:unitId AND ut.userId=:userId AND ut.active AND l.active")
    Optional<Lease> findByUnitIdAndTenant(long unitId, long userId);

    @Modifying
    @Query("UPDATE Invite i SET i.active=false WHERE i.entityId=:unitId AND i.type=:inviteType")
    void deactivateInviteByUnitId(long unitId, String inviteType);
}
