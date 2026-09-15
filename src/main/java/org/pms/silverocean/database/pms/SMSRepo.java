package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.SMS;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Set;

public interface SMSRepo extends JpaRepository<SMS, Long> {
    @org.springframework.data.jpa.repository.Query("SELECT s FROM SMS s WHERE s.active=true AND s.channel='TEXTSMS' AND s.thirdPartyId IS NOT NULL AND s.thirdPartyId<>'' AND s.nextReceiptCheckAt<=:now ORDER BY s.nextReceiptCheckAt,s.id")
    java.util.List<SMS> findDueReceiptChecks(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now,Pageable pageable);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE SMS s SET s.receiptCheckAttempts=s.receiptCheckAttempts+1,s.nextReceiptCheckAt=:leaseUntil WHERE s.id=:id AND s.active=true AND s.channel='TEXTSMS' AND s.nextReceiptCheckAt<=:now AND s.receiptCheckAttempts<:maxAttempts")
    int claimReceiptCheck(@org.springframework.data.repository.query.Param("id") long id,@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now,@org.springframework.data.repository.query.Param("leaseUntil") java.time.LocalDateTime leaseUntil,@org.springframework.data.repository.query.Param("maxAttempts") int maxAttempts);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT s FROM SMS s WHERE s.id=:id AND s.active=true AND s.channel='TEXTSMS'")
    java.util.Optional<SMS> lockTextReceipt(@org.springframework.data.repository.query.Param("id") long id);
    Page<SMS> findByNotificationId(Pageable pageable, Long notificationId);
    Set<SMS> findByThirdPartyId(String thirdPartyId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT s FROM SMS s WHERE s.thirdPartyId=:id AND s.channel=:channel AND s.active=true")
    Set<SMS> lockProviderMessage(@org.springframework.data.repository.query.Param("id") String id,@org.springframework.data.repository.query.Param("channel") String channel);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT s FROM SMS s WHERE s.thirdPartyId=:id AND s.channel='WHATS_APP' AND s.active=true")
    Set<SMS> lockWhatsAppMessage(@org.springframework.data.repository.query.Param("id") String id);
}
