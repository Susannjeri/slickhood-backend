package org.pms.silverocean.service.architecture.events;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.DomainEventOutboxRepo;
import org.pms.silverocean.database.pms.entities.DomainEventOutbox;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxClaimServiceTest {
 @Mock DomainEventOutboxRepo repo;
 @Test void onlyAClaimWinnerCanDispatch(){OutboxClaimService service=new OutboxClaimService(repo);when(repo.claim(eq(7L),any())).thenReturn(0);assertTrue(service.claim(7).isEmpty());verify(repo,never()).findById(7L);}
 @Test void repeatedFailuresMoveEventToDeadLetter(){OutboxClaimService service=new OutboxClaimService(repo);DomainEventOutbox event=new DomainEventOutbox();event.setAttempts(11);event.setStatus("PROCESSING");when(repo.findById(7L)).thenReturn(Optional.of(event));service.failed(7,new IllegalStateException("boom"));assertEquals("DEAD",event.getStatus());assertEquals(12,event.getAttempts());verify(repo).save(event);}
}
