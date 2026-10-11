package org.pms.silverocean.service.sp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.ServiceFinanceOperationRepo;
import org.pms.silverocean.database.pms.entities.ServiceBooking;
import org.pms.silverocean.database.pms.entities.ServiceFinanceOperation;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.sp.wrappers.MarketplaceFinanceRequest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceFinanceOperationServiceTest {
    @Mock private ServiceFinanceOperationRepo operations;
    private ServiceFinanceOperationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ServiceFinanceOperationService(operations);
    }

    @Test
    void appendsAnImmutableConfirmedOperation() {
        ServiceBooking booking = booking(41L);
        when(operations.findByProviderUserIdAndPaymentChannelAndOperationTypeAndProviderReference(
                99L, "MPESA", "REFUND", "RF-41")).thenReturn(Optional.empty());

        boolean replay = service.recordConfirmed(booking, 99L,
                MarketplaceFinanceRequest.FinanceType.REFUND,
                new BigDecimal("1000"), " RF-41 ", 7L);

        assertFalse(replay);
        ArgumentCaptor<ServiceFinanceOperation> saved = ArgumentCaptor.forClass(ServiceFinanceOperation.class);
        verify(operations).saveAndFlush(saved.capture());
        assertEquals(41L, saved.getValue().getBookingId());
        assertEquals(99L, saved.getValue().getProviderUserId());
        assertEquals("MPESA", saved.getValue().getPaymentChannel());
        assertEquals("REFUND", saved.getValue().getOperationType());
        assertEquals("CONFIRMED", saved.getValue().getOperationStatus());
        assertEquals("RF-41", saved.getValue().getProviderReference());
        assertEquals(new BigDecimal("1000.00"), saved.getValue().getAmount());
        assertEquals("KES", saved.getValue().getCurrency());
        assertEquals(7L, saved.getValue().getInitiatedBy());
    }

    @Test
    void exactRetryReturnsTheExistingOperationWithoutAppending() {
        ServiceBooking booking = booking(41L);
        ServiceFinanceOperation existing = new ServiceFinanceOperation(41L, 99L, "MPESA",
                "REFUND", "RF-41", new BigDecimal("1000.00"), "KES", 7L);
        when(operations.findByProviderUserIdAndPaymentChannelAndOperationTypeAndProviderReference(
                99L, "MPESA", "REFUND", "RF-41")).thenReturn(Optional.of(existing));

        boolean replay = service.recordConfirmed(booking, 99L,
                MarketplaceFinanceRequest.FinanceType.REFUND,
                new BigDecimal("1000.0"), "RF-41", 18L);

        assertTrue(replay);
        verify(operations, never()).saveAndFlush(any());
    }

    @Test
    void referenceCannotBeReusedAcrossBookings() {
        ServiceBooking booking = booking(42L);
        ServiceFinanceOperation existing = new ServiceFinanceOperation(41L, 99L, "MPESA",
                "REFUND", "RF-SHARED", new BigDecimal("1000.00"), "KES", 7L);
        when(operations.findByProviderUserIdAndPaymentChannelAndOperationTypeAndProviderReference(
                99L, "MPESA", "REFUND", "RF-SHARED")).thenReturn(Optional.of(existing));

        PMSCustomException error = assertThrows(PMSCustomException.class, () -> service.recordConfirmed(
                booking, 99L, MarketplaceFinanceRequest.FinanceType.REFUND,
                new BigDecimal("1000.00"), "RF-SHARED", 8L));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, error.getResponseCode());
        verify(operations, never()).saveAndFlush(any());
    }

    @Test
    void retryMustMatchTheOriginalAmountExactly() {
        ServiceBooking booking = booking(41L);
        ServiceFinanceOperation existing = new ServiceFinanceOperation(41L, 99L, "MPESA",
                "SETTLEMENT", "ST-41", new BigDecimal("1000.00"), "KES", 7L);
        when(operations.findByProviderUserIdAndPaymentChannelAndOperationTypeAndProviderReference(
                99L, "MPESA", "SETTLEMENT", "ST-41")).thenReturn(Optional.of(existing));

        assertThrows(PMSCustomException.class, () -> service.recordConfirmed(booking, 99L,
                MarketplaceFinanceRequest.FinanceType.SETTLEMENT,
                new BigDecimal("999.99"), "ST-41", 7L));

        verify(operations, never()).saveAndFlush(any());
    }

    private ServiceBooking booking(long id) {
        ServiceBooking booking = new ServiceBooking();
        booking.setId(id);
        booking.setPaymentChannel("mpesa");
        booking.setCurrency("kes");
        return booking;
    }
}
