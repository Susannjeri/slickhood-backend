package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.SMS;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Set;

public interface SMSRepo extends JpaRepository<SMS, Long> {
    Page<SMS> findByNotificationId(Pageable pageable, Long notificationId);
    Set<SMS> findByThirdPartyId(String thirdPartyId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT s FROM SMS s WHERE s.thirdPartyId=:id AND s.channel='WHATS_APP' AND s.active=true")
    Set<SMS> lockWhatsAppMessage(@org.springframework.data.repository.query.Param("id") String id);
}
