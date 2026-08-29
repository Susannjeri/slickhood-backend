package org.pms.silverocean.service.architecture.events;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.database.pms.DomainEventOutboxRepo;
import org.pms.silverocean.database.pms.entities.DomainEventOutbox;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;

@Service @RequiredArgsConstructor
public class OutboxClaimService {
    private final DomainEventOutboxRepo repo;
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Optional<DomainEventOutbox> claim(long id){LocalDateTime now=LocalDateTime.now();return repo.claim(id,now)==1?repo.findById(id):Optional.empty();}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void processed(long id){repo.findById(id).ifPresent(e->{e.setStatus("PROCESSED");e.setProcessedAt(LocalDateTime.now());e.setProcessingStartedAt(null);e.setLastError(null);repo.save(e);});}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void failed(long id,Exception failure){repo.findById(id).ifPresent(e->{int attempts=e.getAttempts()+1;e.setAttempts(attempts);e.setProcessingStartedAt(null);e.setLastError(abbreviate(failure));if(attempts>=12){e.setStatus("DEAD");}else{e.setStatus("FAILED");long delaySeconds=Math.min(3600L,5L*(1L<<Math.min(attempts-1,10)));e.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));}repo.save(e);});}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public int recoverStale(){return repo.recoverStale(LocalDateTime.now().minusMinutes(10),LocalDateTime.now());}
    private String abbreviate(Exception e){String message=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());return message.length()>1000?message.substring(0,1000):message;}
}
