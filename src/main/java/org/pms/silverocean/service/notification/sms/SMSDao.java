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

    public Optional<SMS> lockWhatsAppMessage(String id) {
        Set<SMS> matches = smsRepo.lockWhatsAppMessage(id);
        return matches.size() == 1 ? matches.stream().findFirst() : Optional.empty();
    }

    public java.util.List<SMS> dueReceiptChecks(java.time.LocalDateTime now) {
        return smsRepo.findDueReceiptChecks(now,org.springframework.data.domain.PageRequest.of(0,10));
    }
    @org.springframework.transaction.annotation.Transactional("pmsDBTransactionManager")
    public boolean claimReceiptCheck(long id,java.time.LocalDateTime now,int maxAttempts){
        return smsRepo.claimReceiptCheck(id,now,now.plusMinutes(5),maxAttempts)==1;
    }
    @org.springframework.transaction.annotation.Transactional("pmsDBTransactionManager")
    public void exhaustReceiptChecks(long id,int maxAttempts){
        smsRepo.lockTextReceipt(id).filter(s->s.getReceiptCheckAttempts()>=maxAttempts).ifPresent(s->{
            s.setNextReceiptCheckAt(null);
            if(!"DeliveredToTerminal".equals(s.getDescription()))s.setDescription("Delivery receipt unavailable; manual reconciliation required");
            s.setUpdatedOn(java.time.LocalDateTime.now());smsRepo.save(s);
        });
    }

    public Optional<SMS> lockProviderMessage(String id,String channel) {
        Set<SMS> matches=smsRepo.lockProviderMessage(id,channel);
        return matches.size()==1?matches.stream().findFirst():Optional.empty();
    }

    @org.springframework.transaction.annotation.Transactional("pmsDBTransactionManager")
    public Optional<Long> recordProviderReceipt(String channel,String id,String status,String description,String network,String ip) {
        if(id==null||id.isBlank()||status==null||description==null)return Optional.empty();
        if(!"TEXTSMS".equals(channel)&&!"Africastalking".equals(channel))return Optional.empty();
        var receipt=lockProviderMessage(id,channel).orElse(null);
        if(receipt==null)return Optional.empty();
        boolean alreadyDelivered="TEXTSMS".equals(channel)?"DeliveredToTerminal".equals(receipt.getDescription()):"Success".equals(receipt.getStatus());
        if(alreadyDelivered)return Optional.of(receipt.getNotificationId());
        receipt.setStatus(status);receipt.setDescription(description);receipt.setNetwork(network);
        if("TEXTSMS".equals(channel)&&!"SentToNetwork".equals(description))receipt.setNextReceiptCheckAt(null);
        if(ip!=null)receipt.setCallBackIP(ip);
        receipt.setUpdatedOn(java.time.LocalDateTime.now());smsRepo.save(receipt);
        boolean delivered="TEXTSMS".equals(channel)?"DeliveredToTerminal".equals(description):"Success".equals(status);
        return delivered?Optional.of(receipt.getNotificationId()):Optional.empty();
    }

    public Page<SMS> findAll(Pageable pageable) {
        return smsRepo.findAll(pageable);
    }
}
