package org.pms.silverocean.service.estate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.EstateServiceChargeRepo;
import org.pms.silverocean.database.pms.entities.EstateServiceCharge;
import org.pms.silverocean.service.architecture.events.DomainEventOutboxPublisher;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceChargeReminderServiceTest {
    @Mock EstateServiceChargeRepo charges;
    @Mock DomainEventOutboxPublisher events;

    @Test
    void queuesEachReminderPhaseWithDurableDistinctDedupeKeys() {
        LocalDate today = LocalDate.of(2026, 8, 31);
        EstateServiceCharge preDue = charge(1L);
        EstateServiceCharge overdue = charge(2L);
        when(charges.lockPreDueReminderCandidates(eq(today), eq(today.plusDays(3)), any(Pageable.class)))
                .thenReturn(List.of(preDue));
        when(charges.lockOverdueNoticeCandidates(eq(today), any(Pageable.class))).thenReturn(List.of(overdue));

        var result = new ServiceChargeReminderService(charges, events).queueBatch(today, 100);

        assertThat(result.preDue()).isEqualTo(1);
        assertThat(result.overdue()).isEqualTo(1);
        assertThat(preDue.getPreDueReminderQueuedAt()).isNotNull();
        assertThat(overdue.getOverdueNoticeQueuedAt()).isNotNull();
        verify(events).publish(eq(ServiceChargeReminderEvent.TYPE), eq("ESTATE_SERVICE_CHARGE"), eq("1"),
                eq("SERVICE_CHARGE_PRE_DUE:1"), any(ServiceChargeReminderEvent.class));
        verify(events).publish(eq(ServiceChargeReminderEvent.TYPE), eq("ESTATE_SERVICE_CHARGE"), eq("2"),
                eq("SERVICE_CHARGE_OVERDUE:2"), any(ServiceChargeReminderEvent.class));
        verify(charges).saveAll(List.of(preDue));
        verify(charges).saveAll(List.of(overdue));
    }

    private EstateServiceCharge charge(long id) {
        EstateServiceCharge charge = new EstateServiceCharge();
        charge.setId(id);
        charge.setActive(true);
        return charge;
    }
}
