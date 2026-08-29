package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.SMS;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Set;

public interface SMSRepo extends JpaRepository<SMS, Long> {
    Page<SMS> findByNotificationId(Pageable pageable, Long notificationId);
    Set<SMS> findByThirdPartyId(String thirdPartyId);
}
