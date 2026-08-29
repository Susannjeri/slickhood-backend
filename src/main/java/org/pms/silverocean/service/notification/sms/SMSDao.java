package org.pms.silverocean.service.notification.sms;

import org.pms.silverocean.database.pms.SMSRepo;
import org.pms.silverocean.database.pms.entities.SMS;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

@Service
public class SMSDao {
    private final SMSRepo smsRepo;


    public SMSDao(SMSRepo smsRepo) {
        this.smsRepo = smsRepo;
    }

    public void saveSMS(SMS sms) {
        smsRepo.save(sms);
    }

    public Page<SMS> findAllByNotificationId(Pageable pageable, long notificationId) {
        return smsRepo.findByNotificationId(pageable, notificationId);
    }

    public Optional<SMS> findByThirdPartyId(String thirdPartyId) {
        Set<SMS> byThirdPartyId = smsRepo.findByThirdPartyId(thirdPartyId);
        if (byThirdPartyId.size() != 1) {
            return Optional.empty();
        }
        return byThirdPartyId.stream().findFirst();
    }

    public Page<SMS> findAll(Pageable pageable) {
        return smsRepo.findAll(pageable);
    }
}
