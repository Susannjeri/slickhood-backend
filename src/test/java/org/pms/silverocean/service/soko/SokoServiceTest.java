package org.pms.silverocean.service.soko;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.SokoOrderItemRepo;
import org.pms.silverocean.database.pms.SokoOrderRepo;
import org.pms.silverocean.database.pms.SokoFinanceOperationRepo;
import org.pms.silverocean.database.pms.SokoProductRepo;
import org.pms.silverocean.database.pms.SokoProductImageRepo;
import org.pms.silverocean.database.pms.SokoProductVariationRepo;
import org.pms.silverocean.database.pms.SokoRiderRepo;
import org.pms.silverocean.database.pms.SokoStoreRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.entities.SokoOrder;
import org.pms.silverocean.database.pms.entities.SokoOrderItem;
import org.pms.silverocean.database.pms.entities.SokoProduct;
import org.pms.silverocean.database.pms.entities.SokoProductVariation;
import org.pms.silverocean.database.pms.entities.SokoRider;
import org.pms.silverocean.database.pms.entities.SokoStore;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.filestorage.UploadMalwarePolicy;
import org.pms.silverocean.service.security.EncryptionService;
import org.pms.silverocean.service.security.DecryptDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.kyc.MarketplaceKycGate;
import org.pms.silverocean.service.visitor.VisitorService;
import org.pms.silverocean.service.subscription.SubscriptionEntitlementService;
import org.pms.silverocean.service.subscription.enums.SubscriptionProduct;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SokoServiceTest {
    @Test void fullConfirmedManualRefundStopsFulfillmentAndInvalidatesBuyerCodesWithoutResellingStock() {
        SokoOrder order=refundableOrder();
        when(users.hasRole(PMSRole.FINANCE)).thenReturn(true);
        when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);
        when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));
        when(stores.findById(2L)).thenReturn(Optional.of(store));
        service.finance(9L,new SokoRequests.FinanceUpdate(SokoRequests.FinanceType.REFUND,SokoRequests.FinanceStatus.PROCESSING,new BigDecimal("100"),null));
        var request=new SokoRequests.FinanceUpdate(SokoRequests.FinanceType.REFUND,SokoRequests.FinanceStatus.CONFIRMED,new BigDecimal("100"),"RF-TEST-1");
        service.finance(9L,request);
        assertEquals("REFUNDED",order.getPaymentStatus());assertEquals("FINANCE_HOLD_RETURN_REQUIRED",order.getStatus());
        assertNull(order.getEncryptedDeliveryCode());assertNull(order.getDeliveryCode());assertNull(order.getDeliveryRecoveryOtp());
        assertNull(order.getDeliveryRecoveryOtpExpiresAt());assertNull(order.getDeliveryCodeExpiresAt());
        service.finance(9L,request);
        verify(orders,times(2)).save(order);verify(financeOperations).save(any());
        verifyNoInteractions(products,variations);
        verify(riders,never()).save(any());
    }

    @Test void cancelledOrderCanRecordPartialThenCompleteTheRemainingCumulativeRefund(){
        SokoOrder order=refundableOrder();order.setStatus("CONFIRMED");order.setRefundStatus("NOT_REQUIRED");order.setRefundedAmount(BigDecimal.ZERO);order.setRiderId(null);
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);
        when(users.getUserId()).thenReturn(8L);when(users.hasRole(PMSRole.FINANCE)).thenReturn(true);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(orders.findByInvoiceRefAndActiveTrue("INV-TEST")).thenReturn(Optional.of(order));when(stores.findById(2L)).thenReturn(Optional.of(store));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());

        var invoice=new org.pms.silverocean.database.pms.entities.PMSInvoice();invoice.setMoneyAmount(new BigDecimal("100"));invoice.setMoneyPendingAmount(new BigDecimal("75"));
        when(invoices.getInvoiceByRefForUpdate(any())).thenReturn(Optional.of(invoice));
        service.cancel(9L,new SokoRequests.Cancellation("Buyer cancelled before dispatch"));
        assertEquals("CANCELLED",order.getStatus());assertEquals("REQUESTED",order.getRefundStatus());
        service.finance(9L,new SokoRequests.FinanceUpdate(SokoRequests.FinanceType.REFUND,SokoRequests.FinanceStatus.PROCESSING,new BigDecimal("25"),null));
        service.finance(9L,new SokoRequests.FinanceUpdate(SokoRequests.FinanceType.REFUND,SokoRequests.FinanceStatus.CONFIRMED,new BigDecimal("25"),"RF-PART-1"));
        assertEquals("REFUNDED",order.getPaymentStatus());assertEquals(new BigDecimal("25"),order.getRefundedAmount());
        invoice.setMoneyPendingAmount(BigDecimal.ZERO);service.applyInvoicePayment("INV-TEST","late-payment");
        assertEquals("REQUESTED",order.getRefundStatus());assertEquals(new BigDecimal("100.00"),order.getRefundRequestedAmount());
        service.finance(9L,new SokoRequests.FinanceUpdate(SokoRequests.FinanceType.REFUND,SokoRequests.FinanceStatus.PROCESSING,new BigDecimal("100"),null));
        service.finance(9L,new SokoRequests.FinanceUpdate(SokoRequests.FinanceType.REFUND,SokoRequests.FinanceStatus.CONFIRMED,new BigDecimal("100"),"RF-REST-2"));
        assertEquals("REFUNDED",order.getPaymentStatus());assertEquals("REFUNDED",order.getStatus());assertEquals(new BigDecimal("100"),order.getRefundedAmount());assertEquals("RF-REST-2",order.getRefundReference());
    }

    @Test void completedPartiallyRefundedOrderCanSettleOnlyTheRemainingBalance(){
        SokoOrder order=refundableOrder();order.setStatus("COMPLETED");order.setPaymentStatus("PARTIALLY_REFUNDED");order.setRefundStatus("CONFIRMED");order.setRefundedAmount(new BigDecimal("25"));order.setSettlementStatus("PENDING");
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);
        when(users.hasRole(PMSRole.FINANCE)).thenReturn(true);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findById(2L)).thenReturn(Optional.of(store));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());

        service.finance(9L,new SokoRequests.FinanceUpdate(SokoRequests.FinanceType.SETTLEMENT,SokoRequests.FinanceStatus.CONFIRMED,new BigDecimal("75"),"ST-REST-1"));
        assertEquals("CONFIRMED",order.getSettlementStatus());assertEquals(new BigDecimal("75"),order.getSettledAmount());assertEquals("ST-REST-1",order.getSettlementReference());
        assertThrows(PMSCustomException.class,()->service.finance(9L,new SokoRequests.FinanceUpdate(SokoRequests.FinanceType.SETTLEMENT,SokoRequests.FinanceStatus.CONFIRMED,new BigDecimal("76"),"ST-TOO-HIGH")));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"REFUND","REVERSAL","CHARGEBACK"})
    void confirmedFinanceOperationInvalidatesCodesButDoesNotAutomaticallyResellOrReassign(String type) {
        SokoOrder order=refundableOrder();
        when(orders.findByInvoiceRefAndActiveTrue("INV-TEST")).thenReturn(Optional.of(order));
        service.completeFinanceOperation("INV-TEST",type,new BigDecimal("100"),"RF-TEST-2");
        assertNull(order.getEncryptedDeliveryCode());assertNull(order.getDeliveryCode());assertNull(order.getDeliveryRecoveryOtp());
        assertNull(order.getDeliveryCodeExpiresAt());assertNull(order.getDeliveryRecoveryOtpExpiresAt());
        assertEquals("FINANCE_HOLD_RETURN_REQUIRED",order.getStatus());
        assertEquals(5L,order.getRiderId());assertFalse(order.isStockReleased());
        verifyNoInteractions(products,variations);
        // Existing status notifications may look up the assigned rider; no
        // rider availability/custody mutation is allowed by financial finality.
        verify(riders,never()).save(any());
    }

    @Test void manualRefundAndProviderCallbackShareOneReplayLedger() {
        SokoOrder order=refundableOrder();order.setStatus("CANCELLED");order.setRiderId(null);order.setCollectedAt(null);order.setRefundRequestedAmount(new BigDecimal("100"));
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);
        var recorded=new java.util.concurrent.atomic.AtomicReference<org.pms.silverocean.database.pms.entities.SokoFinanceOperation>();
        when(users.hasRole(PMSRole.FINANCE)).thenReturn(true);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(orders.findByInvoiceRefAndActiveTrue("INV-TEST")).thenReturn(Optional.of(order));when(stores.findById(2L)).thenReturn(Optional.of(store));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());
        when(financeOperations.findByOrderIdAndProviderReference(9L,"RF-SHARED")).thenAnswer(call->Optional.ofNullable(recorded.get()));
        when(financeOperations.save(any())).thenAnswer(call->{var value=(org.pms.silverocean.database.pms.entities.SokoFinanceOperation)call.getArgument(0);recorded.set(value);return value;});

        service.finance(9L,new SokoRequests.FinanceUpdate(SokoRequests.FinanceType.REFUND,SokoRequests.FinanceStatus.PROCESSING,new BigDecimal("100"),null));
        service.finance(9L,new SokoRequests.FinanceUpdate(SokoRequests.FinanceType.REFUND,SokoRequests.FinanceStatus.CONFIRMED,new BigDecimal("100"),"RF-SHARED"));
        service.completeFinanceOperation("INV-TEST","REFUND",new BigDecimal("100"),"RF-SHARED");

        assertEquals(new BigDecimal("100"),order.getRefundedAmount());verify(financeOperations,times(1)).save(any());
        assertThrows(PMSCustomException.class,()->service.completeFinanceOperation("INV-TEST","REFUND",new BigDecimal("50"),"RF-SHARED"));
    }

    @Test void refundsReversalsAndChargebacksRemainSeparateAndCannotExceedCollections() {
        SokoOrder order=refundableOrder();order.setStatus("COMPLETED");order.setRiderId(null);order.setCollectedAt(null);
        when(orders.findByInvoiceRefAndActiveTrue("INV-TEST")).thenReturn(Optional.of(order));

        service.completeFinanceOperation("INV-TEST","REFUND",new BigDecimal("20"),"RF-20");
        service.completeFinanceOperation("INV-TEST","REVERSAL",new BigDecimal("30"),"RV-30");
        service.completeFinanceOperation("INV-TEST","CHARGEBACK",new BigDecimal("50"),"CB-50");

        assertEquals(new BigDecimal("20"),order.getRefundedAmount());assertEquals(new BigDecimal("30"),order.getReversedAmount());assertEquals(new BigDecimal("50"),order.getChargedBackAmount());
        assertEquals("PAYMENT_REVERSED",order.getStatus());assertEquals("REVERSED",order.getPaymentStatus());
        assertThrows(PMSCustomException.class,()->service.completeFinanceOperation("INV-TEST","REFUND",BigDecimal.ONE,"RF-OVER"));
    }

    @Test void partialProviderRefundBeforeCollectionHaltsFulfilmentRestoresStockAndRequestsRemainingRefund() {
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(8L);order.setOrderNumber("SOKO-TEST");order.setInvoiceRef("INV-TEST");order.setStatus("CONFIRMED");order.setPaymentStatus("PAID");order.setRefundStatus("NOT_REQUIRED");order.setSettlementStatus("PENDING");order.setTotal(new BigDecimal("100"));order.setCurrency("KES");order.setActive(true);
        SokoOrderItem item=new SokoOrderItem();item.setOrderId(9L);item.setProductId(5L);item.setQuantity(2);item.setActive(true);
        SokoProduct product=new SokoProduct();product.setId(5L);product.setStockQuantity(8);product.setStatus("PUBLISHED");product.setActive(true);
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);
        when(orders.findByInvoiceRefAndActiveTrue("INV-TEST")).thenReturn(Optional.of(order));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of(item));when(products.findByIdForUpdate(5L)).thenReturn(Optional.of(product));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));

        service.completeFinanceOperation("INV-TEST","REFUND",new BigDecimal("20"),"RF-PARTIAL-HOLD");

        assertEquals("FINANCE_HOLD",order.getStatus());assertEquals("PARTIALLY_REFUNDED",order.getPaymentStatus());assertEquals("REQUESTED",order.getRefundStatus());assertEquals(new BigDecimal("100.00"),order.getRefundRequestedAmount());assertEquals("BLOCKED",order.getSettlementStatus());assertTrue(order.isStockReleased());assertEquals(10,product.getStockQuantity());
        when(users.getUserId()).thenReturn(7L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));
        assertThrows(PMSCustomException.class,()->service.transition(9L,"PACKED",null));
    }

    @Test void partialReversalAfterCollectionKeepsGoodsWithRiderUntilExplicitReturn() {
        SokoOrder order=refundableOrder();
        when(orders.findByInvoiceRefAndActiveTrue("INV-TEST")).thenReturn(Optional.of(order));

        service.completeFinanceOperation("INV-TEST","REVERSAL",new BigDecimal("20"),"RV-PARTIAL-HOLD");

        assertEquals("FINANCE_HOLD_RETURN_REQUIRED",order.getStatus());assertEquals("PARTIALLY_REVERSED",order.getPaymentStatus());assertEquals("REQUESTED",order.getRefundStatus());assertEquals(new BigDecimal("80.00"),order.getRefundRequestedAmount());assertFalse(order.isStockReleased());verifyNoInteractions(products,variations);
    }

    @Test void providerCallbackThenManualReplayDoesNotDoubleCountTheRefund() {
        SokoOrder order=refundableOrder();order.setStatus("CANCELLED");order.setRiderId(null);order.setCollectedAt(null);order.setRefundStatus("REQUESTED");order.setRefundRequestedAmount(new BigDecimal("100"));
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);
        var recorded=new java.util.concurrent.atomic.AtomicReference<org.pms.silverocean.database.pms.entities.SokoFinanceOperation>();
        when(orders.findByInvoiceRefAndActiveTrue("INV-TEST")).thenReturn(Optional.of(order));when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(users.hasRole(PMSRole.FINANCE)).thenReturn(true);when(stores.findById(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());
        when(financeOperations.findByOrderIdAndProviderReference(9L,"RF-CALLBACK-FIRST")).thenAnswer(call->Optional.ofNullable(recorded.get()));
        when(financeOperations.save(any())).thenAnswer(call->{var value=(org.pms.silverocean.database.pms.entities.SokoFinanceOperation)call.getArgument(0);recorded.set(value);return value;});

        service.completeFinanceOperation("INV-TEST","REFUND",new BigDecimal("25"),"RF-CALLBACK-FIRST");
        service.finance(9L,new SokoRequests.FinanceUpdate(SokoRequests.FinanceType.REFUND,SokoRequests.FinanceStatus.CONFIRMED,new BigDecimal("25"),"RF-CALLBACK-FIRST"));

        assertEquals(new BigDecimal("25"),order.getRefundedAmount());verify(financeOperations,times(1)).save(any());
    }

    @Test void latePaymentOnFinanceHoldPreservesPartialRefundStateAndExpandsRefundTarget() {
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(8L);order.setOrderNumber("SOKO-TEST");order.setInvoiceRef("INV-TEST");order.setStatus("FINANCE_HOLD");order.setPaymentStatus("PARTIALLY_REFUNDED");order.setRefundStatus("REQUESTED");order.setRefundedAmount(new BigDecimal("25"));order.setRefundRequestedAmount(new BigDecimal("25"));order.setTotal(new BigDecimal("100"));order.setCurrency("KES");order.setActive(true);
        when(orders.findByInvoiceRefAndActiveTrue("INV-TEST")).thenReturn(Optional.of(order));

        service.applyInvoicePayment("INV-TEST","LATE-PAYMENT-100");

        assertEquals("FINANCE_HOLD",order.getStatus());assertEquals("PARTIALLY_REFUNDED",order.getPaymentStatus());assertEquals("REQUESTED",order.getRefundStatus());assertEquals(new BigDecimal("100.00"),order.getRefundRequestedAmount());
    }

    @Test void financeFinalityKeepsRiderInCustodyUntilExplicitReturnAndNeverRestocksSilently() {
        SokoOrder order=refundableOrder();SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);
        SokoRider rider=existingRider();rider.setId(5L);rider.setUserId(8L);rider.setAvailability("BUSY");
        when(orders.findByInvoiceRefAndActiveTrue("INV-TEST")).thenReturn(Optional.of(order));when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(users.getUserId()).thenReturn(8L);when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(riders.findForUpdate(5L,2L)).thenReturn(Optional.of(rider));

        service.completeFinanceOperation("INV-TEST","REVERSAL",new BigDecimal("100"),"RV-HOLD");
        assertEquals("FINANCE_HOLD_RETURN_REQUIRED",order.getStatus());assertEquals("BUSY",rider.getAvailability());assertFalse(order.isStockReleased());
        service.returnAfterFinanceHold(9L,new SokoRequests.DeliveryException("Returned to shop for inspection"));
        service.returnAfterFinanceHold(9L,new SokoRequests.DeliveryException("Lost response retry"));

        assertEquals("FINANCE_HOLD_RETURNED",order.getStatus());assertEquals("AVAILABLE",rider.getAvailability());assertFalse(order.isStockReleased());
        verify(riders,times(1)).save(rider);verifyNoInteractions(products,variations);
    }

    @Test void suspendedAssignedAccountCannotAcknowledgeFinanceHoldReturn() {
        SokoOrder order=refundableOrder();order.setStatus("FINANCE_HOLD_RETURN_REQUIRED");
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);
        SokoRider rider=existingRider();rider.setId(5L);rider.setUserId(8L);rider.setStatus("SUSPENDED");rider.setAvailability("BUSY");
        when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(users.getUserId()).thenReturn(8L);when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(riders.findForUpdate(5L,2L)).thenReturn(Optional.of(rider));

        assertThrows(PMSCustomException.class,()->service.returnAfterFinanceHold(9L,new SokoRequests.DeliveryException("Returned")));

        assertEquals("FINANCE_HOLD_RETURN_REQUIRED",order.getStatus());assertEquals("BUSY",rider.getAvailability());verify(orders,never()).save(any());verify(riders,never()).save(any());
    }

    private SokoOrder refundableOrder() {
        SokoOrder o=new SokoOrder();o.setId(9L);o.setStoreId(2L);o.setCustomerUserId(8L);o.setOrderNumber("SOKO-TEST");
        o.setStatus("DISPATCHED");o.setPaymentStatus("PAID");o.setRefundStatus("REQUESTED");o.setSettlementStatus("PENDING");o.setTotal(new BigDecimal("100"));o.setCurrency("KES");
        o.setDeliveryMethod("DELIVERY");o.setDeliveryCode("123456");o.setEncryptedDeliveryCode(new byte[]{1});o.setDeliveryRecoveryOtp(new byte[]{2});
        o.setDeliveryCodeExpiresAt(ZonedDateTime.now().plusHours(1));o.setDeliveryRecoveryOtpExpiresAt(ZonedDateTime.now().plusMinutes(10));o.setRiderId(5L);o.setCollectedAt(ZonedDateTime.now().minusMinutes(5));o.setInvoiceRef("INV-TEST");
        return o;
    }
    @Mock SokoStoreRepo stores; @Mock SokoProductRepo products; @Mock SokoOrderRepo orders; @Mock SokoFinanceOperationRepo financeOperations;
    @Mock SokoOrderItemRepo items; @Mock InvoiceDao invoices; @Mock AccountDao accounts;
    @Mock SokoProductVariationRepo variations; @Mock SokoRiderRepo riders; @Mock UnitRepo units; @Mock UserDao users; @Mock VisitorService visitors;
    @Mock SokoProductImageRepo productImages; @Mock GarageService garage; @Mock UploadMalwarePolicy malwarePolicy; @Mock EncryptionService encryption; @Mock NotificationService notifications; @Mock I18NService i18n; @Mock MarketplaceKycGate marketplaceKycGate;
    @Mock org.pms.silverocean.service.notification.BusinessNotificationService businessAlerts;
    @Mock SubscriptionEntitlementService subscriptionEntitlements;
    SokoService service;
    @Test void financeAlertsKeepSettlementRecordsProviderOnly(){
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(8L);order.setOrderNumber("SOKO-TEST");order.setRefundStatus("CONFIRMED");order.setRefundedAmount(new BigDecimal("20"));order.setSettlementStatus("CONFIRMED");order.setSettledAmount(new BigDecimal("80"));
        SokoStore store=new SokoStore();store.setOwnerUserId(7L);when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"notifyFinanceRecord",order,true);
        verify(businessAlerts).publish(8L,"soko-finance:9:refund:CONFIRMED:20","SOKO_ORDER_STATUS","The refund record for Soko order SOKO-TEST was updated.","/dashboard/soko");
        verify(businessAlerts).publish(7L,"soko-finance:9:refund:CONFIRMED:20","SOKO_ORDER_STATUS","The refund record for Soko order SOKO-TEST was updated.","/dashboard/soko");
        clearInvocations(businessAlerts);
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"notifyFinanceRecord",order,false);
        verify(businessAlerts).publish(7L,"soko-finance:9:settlement:CONFIRMED:80","SOKO_ORDER_STATUS","The receiving-payment record for Soko order SOKO-TEST was updated.","/dashboard/soko");verifyNoMoreInteractions(businessAlerts);
        verifyNoInteractions(orders,products,invoices,accounts,riders,notifications);
    }

    @Test void deliveryCodeQueueErrorsPropagateToTransactionalCaller(){
        SokoOrder order=new SokoOrder();order.setId(9L);order.setCustomerUserId(8L);order.setOrderNumber("SOKO-TEST");
        var buyer=new org.pms.silverocean.database.pms.entities.Users();buyer.setEmail("buyer@example.test");
        when(users.findById(8L)).thenReturn(Optional.of(buyer));when(i18n.getLocalizedMessage(anyString())).thenReturn("Order %s code %s expires %s");
        when(notifications.queueNotification(any())).thenThrow(new IllegalStateException("fixture queue unavailable"));
        assertThrows(IllegalStateException.class,()->org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"notifyDeliveryCode",order,"123456"));
        verifyNoInteractions(products,variations,orders,items,riders,invoices,accounts,visitors,businessAlerts);
    }

    @Test void paymentConfirmationAlertsBuyerAndMerchantOnlyOnce(){
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(8L);order.setOrderNumber("SOKO-TEST");order.setStatus("PENDING_PAYMENT");order.setPaymentStatus("UNPAID");order.setInvoiceRef("INV-TEST");order.setTotal(new BigDecimal("100"));
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);
        when(orders.findByInvoiceRefAndActiveTrue("INV-TEST")).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));
        service.completePaidInvoice("INV-TEST","provider-reference");
        verify(businessAlerts).publish(eq(8L),anyString(),eq("SOKO_ORDER_STATUS"),eq("Soko order SOKO-TEST: Payment confirmed."),eq("/dashboard/soko"));
        verify(businessAlerts).publish(eq(7L),anyString(),eq("SOKO_ORDER_STATUS"),eq("Soko order SOKO-TEST: Payment confirmed."),eq("/dashboard/soko"));
        verifyNoMoreInteractions(businessAlerts);assertEquals("PAID",order.getPaymentStatus());
    }
    @Test void riderReviewAlertsDoNotExposePrivateReason(){
        when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);SokoRider rider=existingRider();rider.setUserId(8L);
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);
        when(riders.findByIdForUpdate(3L)).thenReturn(Optional.of(rider));when(riders.save(any())).thenAnswer(i->i.getArgument(0));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));
        service.riderDecision(3L,new SokoRequests.RiderDecision("REJECT","Private ID/KYC investigation notes"));
        var message=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(businessAlerts,times(2)).publish(anyLong(),anyString(),eq("SOKO_RIDER_STATUS"),message.capture(),anyString());
        for(String text:message.getAllValues())assertFalse(text.contains("Private ID/KYC"));
        verifyNoInteractions(notifications);
    }

    private SokoRider existingRider(){SokoRider r=new SokoRider();r.setId(3L);r.setStoreId(2L);r.setActive(true);r.setRiderType("INDIVIDUAL");r.setDisplayName("Jane Rider");r.setPhoneNumber("0712345678");r.setNationalIdNumber("12345678");r.setEmail("jane@example.test");r.setVehicleType("Motorbike");r.setVehiclePlate("KDA 123A");r.setPhoneConfirmed(true);r.setPhoneConfirmationStatus("CONFIRMED");r.setVerified(true);r.setStatus("ACTIVE");r.setAvailability("AVAILABLE");return r;}
    private void merchant(){SokoStore s=new SokoStore();s.setId(2L);s.setOwnerUserId(7L);when(users.getUserId()).thenReturn(7L);when(stores.findByIdAndOwnerUserIdAndActiveTrue(2L,7L)).thenReturn(Optional.of(s));}
    private SokoRequests.RiderUpsert riderUpdate(String name){return new SokoRequests.RiderUpsert(2L,"INDIVIDUAL",name,"0712345678","12345678","jane@example.test","Motorbike","KDA 123A","Updated notes");}
    @Test void busyRiderCannotBeEdited(){merchant();SokoRider r=existingRider();r.setAvailability("BUSY");when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(r));assertThrows(PMSCustomException.class,()->service.updateRider(3L,riderUpdate("Changed Name")));assertEquals("BUSY",r.getAvailability());verify(riders,never()).save(any());}
    @Test void activeAssignmentBlocksEditEvenIfLegacyAvailabilityIsWrong(){merchant();SokoRider r=existingRider();when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(r));when(orders.existsByRiderIdAndStatusInAndActiveTrue(eq(3L),anyList())).thenReturn(true);assertThrows(PMSCustomException.class,()->service.updateRider(3L,riderUpdate("Changed Name")));verify(riders,never()).save(any());}
    @Test void notesOnlyEditPreservesVerification(){merchant();SokoRider r=existingRider();when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(r));when(riders.save(any())).thenAnswer(i->i.getArgument(0));var result=service.updateRider(3L,riderUpdate("Jane Rider"));assertTrue(r.isVerified());assertEquals("ACTIVE",r.getStatus());assertEquals("AVAILABLE",r.getAvailability());assertEquals("CONFIRMED",result.confirmationStatus());}
    @Test void identityEditRequiresVerificationAgain(){merchant();SokoRider r=existingRider();when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(r));when(riders.save(any())).thenAnswer(i->i.getArgument(0));when(encryption.encrypt(anyString())).thenReturn(new byte[]{1});when(i18n.getLocalizedMessage(NotificationType.SOKO_RIDER_CONFIRMATION_SMS.getBody())).thenReturn("Code %s expires in %s minutes");var result=service.updateRider(3L,riderUpdate("Changed Name"));assertFalse(r.isVerified());assertFalse(r.isPhoneConfirmed());assertEquals("PENDING_VERIFICATION",r.getStatus());assertEquals("OFFLINE",r.getAvailability());assertEquals("CODE_QUEUED",result.confirmationStatus());verify(notifications).queueNotification(any());}
    @Test void adminCannotResetBusyRiderThroughVerifyOrReject(){when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);SokoRider r=existingRider();r.setAvailability("BUSY");when(riders.findByIdForUpdate(3L)).thenReturn(Optional.of(r));assertThrows(PMSCustomException.class,()->service.riderDecision(3L,new SokoRequests.RiderDecision("VERIFY",null)));assertThrows(PMSCustomException.class,()->service.riderDecision(3L,new SokoRequests.RiderDecision("REJECT","Reason")));assertEquals("BUSY",r.getAvailability());verify(riders,never()).save(any());}
    @Test void verificationLinksAnAccountCreatedAfterMerchantAddedRider(){when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);SokoRider r=existingRider();r.setUserId(null);r.setVerified(false);when(riders.findByIdForUpdate(3L)).thenReturn(Optional.of(r));var u=new org.pms.silverocean.database.pms.entities.Users();u.setId(8L);u.setActive(true);u.setVerified(true);u.setEmailVerified(true);u.setAccountStatus("ACTIVE");when(users.findByPhone("+254712345678")).thenReturn(Optional.of(u));when(users.findById(8L)).thenReturn(Optional.of(u));when(riders.save(any())).thenAnswer(i->i.getArgument(0));service.riderDecision(3L,new SokoRequests.RiderDecision("VERIFY",null));assertEquals(8L,r.getUserId());assertTrue(r.isVerified());verify(users,never()).findByEmail(anyString());verify(marketplaceKycGate).require(eq(8L),eq("PROVIDER_TYPE"),eq("DELIVERY_RIDER"),any());}
    @Test void verificationDoesNotLinkSuspendedOrIncompletePhoneOwner(){when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);SokoRider r=existingRider();r.setUserId(null);r.setVerified(false);when(riders.findByIdForUpdate(3L)).thenReturn(Optional.of(r));var u=new org.pms.silverocean.database.pms.entities.Users();u.setId(8L);u.setActive(true);u.setVerified(true);u.setEmailVerified(true);u.setAccountStatus("SUSPENDED");when(users.findByPhone("+254712345678")).thenReturn(Optional.of(u));when(riders.save(any())).thenAnswer(i->i.getArgument(0));service.riderDecision(3L,new SokoRequests.RiderDecision("VERIFY",null));assertNull(r.getUserId());assertTrue(r.isVerified());verify(marketplaceKycGate,never()).require(anyLong(),anyString(),anyString(),any());}

    @BeforeEach void setup(){
        service=new SokoService(stores,products,productImages,variations,orders,financeOperations,items,riders,units,invoices,accounts,users,visitors,garage,malwarePolicy,encryption,notifications,businessAlerts,i18n,marketplaceKycGate,subscriptionEntitlements);
        lenient().when(stores.findByIdForCheckout(anyLong())).thenAnswer(call->stores.findByIdAndActiveTrue(call.getArgument(0)));
        lenient().when(orders.findPendingCheckoutForUpdate(anyLong(),anyLong(),any())).thenReturn(List.of());
        lenient().when(financeOperations.findByOrderIdAndProviderReference(anyLong(),anyString())).thenReturn(Optional.empty());
        lenient().when(invoices.getInvoiceIdsByRefs(any())).thenReturn(java.util.Map.of());
        var paidInvoice=new org.pms.silverocean.database.pms.entities.PMSInvoice();paidInvoice.setMoneyAmount(new BigDecimal("100"));paidInvoice.setMoneyPendingAmount(BigDecimal.ZERO);
        lenient().when(invoices.getInvoiceByRefForUpdate(any())).thenReturn(Optional.of(paidInvoice));
        lenient().when(invoices.getInvoiceByRef(any())).thenReturn(Optional.of(paidInvoice));
    }

    private SokoDeliveryDestinationProjection destination(long unitId,long propertyId,String unitRef,
                                                           String propertyName,String address,String mapLocation){
        SokoDeliveryDestinationProjection row=mock(SokoDeliveryDestinationProjection.class);
        when(row.getUnitId()).thenReturn(unitId);when(row.getPropertyId()).thenReturn(propertyId);
        when(row.getUnitRef()).thenReturn(unitRef);when(row.getPropertyName()).thenReturn(propertyName);
        when(row.getAddress()).thenReturn(address);when(row.getMapLocation()).thenReturn(mapLocation);
        return row;
    }

    @Test void expiryScanDrainsMoreThanOneHundredReservationsInBoundedBatches() {
        List<SokoOrder> firstBatch=java.util.stream.LongStream.rangeClosed(1,100).mapToObj(id->{
            SokoOrder order=new SokoOrder();order.setId(id);order.setStoreId(2L);order.setCustomerUserId(4L);order.setOrderNumber("SOKO-"+id);order.setInvoiceRef("INV-"+id);order.setStatus("PENDING_PAYMENT");order.setPaymentStatus("UNPAID");order.setRefundStatus("NOT_REQUIRED");order.setTotal(new BigDecimal("100"));order.setActive(true);return order;
        }).toList();
        when(orders.findExpiredReservations(any(),any())).thenReturn(firstBatch,List.of());

        service.expireReservations();

        assertTrue(firstBatch.stream().allMatch(order->"EXPIRED".equals(order.getStatus())&&order.isStockReleased()));
        verify(orders,times(2)).findExpiredReservations(any(),any());verify(orders,times(100)).save(any());
    }

    @Test void publicStoreDetailIncludesCustomerContactButNotOwnershipPaymentOrModerationData() throws Exception {
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(77L);store.setName("Fresh Corner");store.setPhoneNumber("0712345678");store.setAddress("Market Road, Nairobi");store.setPaymentAccountId(33L);store.setStatus("PUBLISHED");store.setActive(true);store.setReviewedByUserId(99L);store.setReviewReason("internal review note");
        SokoProduct product=new SokoProduct();product.setId(5L);product.setStoreId(2L);product.setName("Milk");product.setDescription("Fresh milk");product.setCategory("Dairy & eggs");product.setUnit("litre");product.setPrice(new BigDecimal("120"));product.setCurrency("KES");product.setStockQuantity(4);product.setStatus("PUBLISHED");product.setActive(true);product.setCreatedBy(77L);product.setModeratedByUserId(99L);product.setModerationReason("internal product note");
        when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(products.findAllByStoreIdAndActiveTrueOrderByName(2L)).thenReturn(List.of(product));when(variations.findAllByProductIdInAndActiveTrueOrderByProductIdAscNameAscValueAsc(List.of(5L))).thenReturn(List.of());

        String json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(service.storeDetail(2L));

        assertTrue(json.contains("Market Road, Nairobi"));assertTrue(json.contains("\"storePhoneNumber\":\"0712345678\""));assertFalse(json.contains("\"phoneNumber\""));
        assertFalse(json.contains("ownerUserId"));assertFalse(json.contains("paymentAccountId"));assertFalse(json.contains("reviewedByUserId"));assertFalse(json.contains("reviewReason"));assertFalse(json.contains("createdBy"));assertFalse(json.contains("moderatedByUserId"));assertFalse(json.contains("moderationReason"));
    }

    @Test void riderAssignmentsExposeDeliveryNeedsWithoutFinancialOrRecoverySecrets() throws Exception {
        SokoRider rider=existingRider();rider.setUserId(8L);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setOrderNumber("SOKO-9");order.setStoreId(2L);order.setStoreNameSnapshot("Original Fresh Corner");order.setStoreAddressSnapshot("Original Shop Lane");order.setStorePhoneSnapshot("0711111111");order.setStoreLatitudeSnapshot(-1.28);order.setStoreLongitudeSnapshot(36.82);order.setCustomerUserId(4L);order.setStatus("DISPATCHED");order.setDeliveryMethod("DELIVERY");order.setDeliveryAddress("Market Road");order.setCustomerPhone("0712345678");order.setPaymentAccountId(33L);order.setInvoiceRef("INV-SECRET");order.setRefundStatus("REQUESTED");order.setSettlementStatus("PENDING");order.setDeliveryRecoveryOtp(new byte[]{7});order.setDeliveryRecoverySupportReason("private support note");order.setRiderId(3L);order.setDeliveryExceptionReason("Customer unavailable");order.setActive(true);
        SokoOrderItem item=new SokoOrderItem();item.setId(10L);item.setOrderId(9L);item.setProductId(5L);item.setProductName("Milk");item.setUnit("litre");item.setQuantity(2);item.setUnitPrice(new BigDecimal("120"));item.setLineTotal(new BigDecimal("240"));item.setActive(true);
        SokoStore store=new SokoStore();store.setId(2L);store.setName("Fresh Corner");store.setAddress("Shop Lane");store.setPhoneNumber("0700000000");store.setActive(true);
        var pageable=org.springframework.data.domain.PageRequest.of(0,10);when(users.getUserId()).thenReturn(8L);when(riders.findAllByUserIdAndActiveTrue(8L)).thenReturn(List.of(rider));when(orders.findAllByRiderIdInAndStatusNotInAndActiveTrue(eq(List.of(3L)),eq(List.of("PACKED","FINANCE_HOLD")),any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(order),pageable,1));when(items.findAllByOrderIdInAndActiveTrueOrderByOrderIdAscIdAsc(List.of(9L))).thenReturn(List.of(item));when(stores.findAllById(List.of(2L))).thenReturn(List.of(store));
        String json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(service.riderAssignments(pageable));
        assertTrue(json.contains("Market Road"));assertTrue(json.contains("0712345678"));assertTrue(json.contains("Original Fresh Corner"));assertTrue(json.contains("Original Shop Lane"));assertTrue(json.contains("0711111111"));assertTrue(json.contains("Customer unavailable"));assertTrue(json.contains("Milk"));
        for(String forbidden:List.of("paymentAccountId","paymentChannel","invoiceRef","refundStatus","settlementStatus","deliveryRecovery","unitPrice","lineTotal","customerUserId"))assertFalse(json.contains(forbidden),forbidden);
    }

    @Test void suspendedOrUnverifiedLinkedRiderCannotListCustomerAssignments(){
        SokoRider suspended=existingRider();suspended.setUserId(8L);suspended.setStatus("SUSPENDED");
        SokoRider unverified=existingRider();unverified.setId(4L);unverified.setUserId(8L);unverified.setVerified(false);
        when(users.getUserId()).thenReturn(8L);when(riders.findAllByUserIdAndActiveTrue(8L)).thenReturn(List.of(suspended,unverified));
        var result=service.riderAssignments(org.springframework.data.domain.PageRequest.of(0,10));
        assertTrue(result.isEmpty());verifyNoInteractions(orders,items);verify(stores,never()).findAllById(any());
    }

    @Test void releasedPreCollectionRiderCannotListPackedOrderWithBuyerAddress(){
        SokoRider rider=existingRider();rider.setUserId(8L);
        var pageable=org.springframework.data.domain.PageRequest.of(0,10);
        when(users.getUserId()).thenReturn(8L);
        when(riders.findAllByUserIdAndActiveTrue(8L)).thenReturn(List.of(rider));
        when(orders.findAllByRiderIdInAndStatusNotInAndActiveTrue(eq(List.of(3L)),eq(List.of("PACKED","FINANCE_HOLD")),any()))
                .thenReturn(org.springframework.data.domain.Page.empty(pageable));

        assertTrue(service.riderAssignments(pageable).isEmpty());
        verify(orders).findAllByRiderIdInAndStatusNotInAndActiveTrue(eq(List.of(3L)),eq(List.of("PACKED","FINANCE_HOLD")),any());
        verifyNoInteractions(items);verify(stores,never()).findAllById(any());
    }

    @Test void participantOrderViewDoesNotExposePersistencePaymentSettlementOrRecoveryControlFields() throws Exception {
        SokoOrder order=new SokoOrder();order.setId(9L);order.setOrderNumber("SOKO-9");order.setStoreId(2L);order.setCustomerUserId(4L);order.setStatus("DISPATCHED");order.setPaymentStatus("PAID");order.setInvoiceRef("INV-9");order.setPaymentAccountId(33L);order.setPaymentChannel("MPESA");order.setDeliveryMethod("DELIVERY");order.setCustomerPhone("0712345678");order.setCurrency("KES");order.setSubtotal(BigDecimal.TEN);order.setDeliveryFee(BigDecimal.ONE);order.setTotal(BigDecimal.valueOf(11));order.setDeliveryRecoveryRequestedBy(99L);order.setDeliveryRecoverySupportReason("private support note");order.setDeliveryRecoveryRequestCount(4);order.setSettlementStatus("PENDING");order.setSettledAmount(BigDecimal.ZERO);order.setCheckoutIdempotencyKey("secret-key");order.setActive(true);order.setCreatedBy(4L);
        SokoOrderItem item=new SokoOrderItem();item.setId(10L);item.setOrderId(9L);item.setProductId(5L);item.setProductName("Milk");item.setUnit("litre");item.setUnitPrice(BigDecimal.TEN);item.setQuantity(1);item.setLineTotal(BigDecimal.TEN);item.setCreatedBy(4L);item.setActive(true);
        SokoStore store=new SokoStore();store.setId(2L);store.setName("Fresh Corner");store.setActive(true);
        var pageable=org.springframework.data.domain.PageRequest.of(0,10);when(users.getUserId()).thenReturn(4L);when(orders.findAllByCustomerUserIdAndActiveTrue(eq(4L),any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(order),pageable,1));when(items.findAllByOrderIdInAndActiveTrueOrderByOrderIdAscIdAsc(List.of(9L))).thenReturn(List.of(item));when(stores.findAllById(List.of(2L))).thenReturn(List.of(store));
        when(invoices.getInvoiceIdsByRefs(List.of("INV-9"))).thenReturn(java.util.Map.of("INV-9",71L));

        var detail=service.myOrders(pageable).getContent().getFirst();
        String orderJson=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(detail.order());
        String itemJson=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(detail.items());
        String actionJson=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(SokoModels.OrderAction.from(order));

        assertTrue(orderJson.contains("SOKO-9"));assertTrue(itemJson.contains("Milk"));assertEquals(71L,detail.invoiceId());
        verify(invoices).getInvoiceIdsByRefs(List.of("INV-9"));
        for(String forbidden:List.of("customerUserId","paymentAccountId","paymentChannel","checkoutIdempotencyKey","settlementStatus","settledAmount","deliveryRecoveryRequestedBy","deliveryRecoverySupportReason","deliveryRecoveryRequestCount","createdBy","uuid","active"))assertFalse(orderJson.contains(forbidden),forbidden);
        for(String forbidden:List.of("orderId","createdBy","uuid","active"))assertFalse(itemJson.contains(forbidden),forbidden);
        for(String forbidden:List.of("subtotal","deliveryFee","total","currency","paymentAccountId","paymentChannel","refund","settlement","recovery","customerPhone"))assertFalse(actionJson.toLowerCase(java.util.Locale.ROOT).contains(forbidden.toLowerCase(java.util.Locale.ROOT)),forbidden);
    }

    @Test void orderPageResolvesAllInvoiceIdsWithOneBatchLookup(){
        SokoOrder first=new SokoOrder();first.setId(1L);first.setOrderNumber("SOKO-1");first.setStoreId(2L);first.setCustomerUserId(4L);first.setInvoiceRef("INV-1");first.setActive(true);
        SokoOrder second=new SokoOrder();second.setId(2L);second.setOrderNumber("SOKO-2");second.setStoreId(2L);second.setCustomerUserId(4L);second.setInvoiceRef("INV-2");second.setActive(true);
        SokoStore store=new SokoStore();store.setId(2L);store.setName("Fresh Corner");store.setActive(true);
        var pageable=org.springframework.data.domain.PageRequest.of(0,10);
        when(users.getUserId()).thenReturn(4L);
        when(orders.findAllByCustomerUserIdAndActiveTrue(eq(4L),any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(first,second),pageable,2));
        when(items.findAllByOrderIdInAndActiveTrueOrderByOrderIdAscIdAsc(List.of(1L,2L))).thenReturn(List.of());
        when(stores.findAllById(List.of(2L))).thenReturn(List.of(store));
        when(invoices.getInvoiceIdsByRefs(List.of("INV-1","INV-2"))).thenReturn(java.util.Map.of("INV-1",101L,"INV-2",102L));

        var result=service.myOrders(pageable).getContent();

        assertEquals(List.of(101L,102L),result.stream().map(SokoModels.OrderDetail::invoiceId).toList());
        verify(invoices,times(1)).getInvoiceIdsByRefs(List.of("INV-1","INV-2"));
    }

    @Test void oneOffCourierDispatchIsRejectedBeforeAssignmentOrCodeGeneration(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setStatus("PACKED");order.setDeliveryMethod("DELIVERY");
        when(users.getUserId()).thenReturn(7L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));
        assertThrows(PMSCustomException.class,()->service.transition(9L,"DISPATCHED",new SokoRequests.Dispatch(null,"Unverified courier","0712345678",null,java.time.LocalDateTime.now().plusHours(1))));
        assertEquals("PACKED",order.getStatus());assertNull(order.getDeliveryCode());verifyNoInteractions(encryption,riders,visitors);verify(orders,never()).save(any());
    }

    @Test void ordinaryOrderTransitionIgnoresAnEmptyDispatchObject(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setStatus("PAID");order.setActive(true);
        when(users.getUserId()).thenReturn(7L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));
        when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(stores.findById(2L)).thenReturn(Optional.of(store));
        when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());

        var result=service.transition(9L,"CONFIRMED",new SokoRequests.Dispatch(null,null,null,null,null));

        assertEquals("CONFIRMED",result.order().status());assertNotNull(order.getConfirmedAt());verify(orders).save(order);verifyNoInteractions(riders);
    }

    @Test void dispatchRequiresAnExpectedArrivalTimeBeforeLookingUpTheRider(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setStatus("PACKED");order.setDeliveryMethod("DELIVERY");order.setActive(true);
        when(users.getUserId()).thenReturn(7L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));

        PMSCustomException failure=assertThrows(PMSCustomException.class,
                ()->service.transition(9L,"DISPATCHED",new SokoRequests.Dispatch(3L,null,null,null,null)));

        assertEquals("Enter the rider's expected arrival time before dispatching the order.",failure.getData());
        assertEquals("PACKED",order.getStatus());verifyNoInteractions(riders,visitors,encryption);verify(orders,never()).save(any());
    }

    @Test void merchantCatalogueUsesAuthoritativeVariationStockNotStaleJson(){
        merchant();SokoProduct product=new SokoProduct();product.setId(5L);product.setVariationsJson("[{\"stockQuantity\":99}]");
        SokoProductVariation variant=new SokoProductVariation();variant.setId(6L);variant.setProductId(5L);variant.setName("Size");variant.setValue("1 kg");variant.setStockQuantity(2);
        when(products.findAllByStoreIdAndActiveTrueOrderByName(2L)).thenReturn(List.of(product));when(variations.findAllByProductIdInAndActiveTrueOrderByProductIdAscNameAscValueAsc(List.of(5L))).thenReturn(List.of(variant));
        var result=service.myProducts(2L).getFirst();assertTrue(result.getVariationsJson().contains("\"stockQuantity\":2"));assertFalse(result.getVariationsJson().contains("99"));verify(products,never()).save(any());
    }

    @Test void merchantCannotMakeSuspendedVerifiedRiderAvailable(){
        merchant();var rider=existingRider();rider.setStatus("SUSPENDED");when(riders.findByIdForUpdate(3L)).thenReturn(Optional.of(rider));
        assertThrows(PMSCustomException.class,()->service.setRiderAvailability(3L,"AVAILABLE"));verify(riders,never()).save(any());
    }

    @Test void activeDeliveryPreventsAvailabilityChangeEvenIfBusyFlagIsMissing(){
        merchant();var rider=existingRider();when(riders.findByIdForUpdate(3L)).thenReturn(Optional.of(rider));
        when(orders.existsByRiderIdAndStatusInAndActiveTrue(eq(3L),anyList())).thenReturn(true);
        assertThrows(PMSCustomException.class,()->service.setRiderAvailability(3L,"OFFLINE"));verify(riders,never()).save(any());
    }

    @Test void existingOrderKeepsItsPaymentDestinationAfterShopAccountChanges(){
        SokoOrder order=new SokoOrder();order.setId(41L);order.setCustomerUserId(7L);order.setStoreId(2L);order.setPaymentAccountId(33L);order.setPaymentChannel("MPESA");order.setStoreNameSnapshot("Original Shop");order.setStoreAddressSnapshot("Original Road");order.setStorePhoneSnapshot("0711111111");order.setStoreLatitudeSnapshot(-1.28);order.setStoreLongitudeSnapshot(36.82);
        SokoStore store=new SokoStore();store.setId(2L);store.setName("Renamed Shop");store.setAddress("Moved Road");store.setPhoneNumber("0799999999");store.setLatitude(-2.0);store.setLongitude(37.0);store.setPaymentAccountId(44L);
        when(users.getUserId()).thenReturn(7L);when(orders.findAllByCustomerUserIdAndActiveTrue(eq(7L),any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(order)));when(stores.findAllById(any())).thenReturn(List.of(store));
        var result=service.myOrders(org.springframework.data.domain.PageRequest.of(0,10)).getContent().getFirst();assertEquals(33L,result.paymentAccountId());assertEquals("MPESA",result.paymentChannel());assertEquals("Original Shop",result.storeName());assertEquals("Original Road",result.storeAddress());assertEquals("0711111111",result.storePhoneNumber());assertEquals(-1.28,result.storeLatitude());assertEquals(36.82,result.storeLongitude());verify(accounts,never()).getAccountById(44L);
    }

    @Test void buyerSelectionRepinsUnpaidOrderAndInvoiceToReadySameOwnerAccount(){
        SokoOrder order=new SokoOrder();order.setId(41L);order.setInvoiceRef("INV-41");order.setStoreId(2L);order.setCustomerUserId(7L);order.setStatus("PENDING_PAYMENT");order.setPaymentStatus("UNPAID");order.setPaymentAccountId(12L);order.setPaymentChannel("MPESA_BANK");order.setReservationExpiresAt(ZonedDateTime.now().plusMinutes(10));order.setActive(true);
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(99L);store.setActive(true);
        var invoice=new org.pms.silverocean.database.pms.entities.PMSInvoice();invoice.setRef("INV-41");invoice.setBillingType("SOKO");invoice.setBilledUserId(7L);invoice.setPayToUserId(99L);invoice.setPaymentAccountId(12L);invoice.setMoneyAmount(new BigDecimal("1500"));invoice.setMoneyPendingAmount(new BigDecimal("1500"));invoice.setActive(true);
        var direct=new org.pms.silverocean.database.pms.entities.PaymentAccount();direct.setId(13L);direct.setCreatedBy(99L);direct.setCategory(org.pms.silverocean.service.account.enums.AccountCategory.MERCHANT);direct.setChannel(org.pms.silverocean.service.payment.wrappers.PaymentChannel.MPESA);direct.setActive(true);direct.setVerified(true);
        when(orders.findByInvoiceRefAndActiveTrue("INV-41")).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));

        service.selectPendingPaymentDestination(invoice,direct);

        assertEquals(13L,order.getPaymentAccountId());assertEquals("MPESA",order.getPaymentChannel());assertEquals(13L,invoice.getPaymentAccountId());
        verify(orders).save(order);verify(invoices).saveInvoice(invoice);
    }

    @Test void paymentDestinationSelectionRejectsForeignMerchantAndPartiallyPaidOrder(){
        SokoOrder order=new SokoOrder();order.setId(41L);order.setInvoiceRef("INV-41");order.setStoreId(2L);order.setCustomerUserId(7L);order.setStatus("PENDING_PAYMENT");order.setPaymentStatus("PARTIALLY_PAID");order.setPaymentAccountId(12L);order.setReservationExpiresAt(ZonedDateTime.now().plusMinutes(10));order.setActive(true);
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(99L);store.setActive(true);
        var invoice=new org.pms.silverocean.database.pms.entities.PMSInvoice();invoice.setRef("INV-41");invoice.setBillingType("SOKO");invoice.setBilledUserId(7L);invoice.setPayToUserId(99L);invoice.setPaymentAccountId(12L);invoice.setMoneyAmount(new BigDecimal("1500"));invoice.setMoneyPendingAmount(new BigDecimal("500"));invoice.setActive(true);
        var foreign=new org.pms.silverocean.database.pms.entities.PaymentAccount();foreign.setId(13L);foreign.setCreatedBy(100L);foreign.setCategory(org.pms.silverocean.service.account.enums.AccountCategory.MERCHANT);foreign.setChannel(org.pms.silverocean.service.payment.wrappers.PaymentChannel.MPESA);foreign.setActive(true);foreign.setVerified(true);
        assertThrows(org.pms.silverocean.service.payment.PaymentRequestException.class,()->service.selectPendingPaymentDestination(invoice,foreign));
        foreign.setCreatedBy(99L);when(orders.findByInvoiceRefAndActiveTrue("INV-41")).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));
        assertThrows(org.pms.silverocean.service.payment.PaymentRequestException.class,()->service.selectPendingPaymentDestination(invoice,foreign));
        verify(orders,never()).save(any());verify(invoices,never()).saveInvoice(any());
    }

    @Test void paymentDestinationSelectionRejectsExpiredReservation(){
        SokoOrder order=new SokoOrder();order.setId(41L);order.setInvoiceRef("INV-41");order.setStoreId(2L);order.setCustomerUserId(7L);order.setStatus("PENDING_PAYMENT");order.setPaymentStatus("UNPAID");order.setPaymentAccountId(12L);order.setReservationExpiresAt(ZonedDateTime.now().minusSeconds(1));order.setActive(true);
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(99L);store.setActive(true);
        var invoice=new org.pms.silverocean.database.pms.entities.PMSInvoice();invoice.setRef("INV-41");invoice.setBillingType("SOKO");invoice.setBilledUserId(7L);invoice.setPayToUserId(99L);invoice.setPaymentAccountId(12L);invoice.setMoneyAmount(new BigDecimal("1500"));invoice.setMoneyPendingAmount(new BigDecimal("1500"));invoice.setActive(true);
        var direct=new org.pms.silverocean.database.pms.entities.PaymentAccount();direct.setId(13L);direct.setCreatedBy(99L);direct.setCategory(org.pms.silverocean.service.account.enums.AccountCategory.MERCHANT);direct.setChannel(org.pms.silverocean.service.payment.wrappers.PaymentChannel.MPESA);direct.setActive(true);direct.setVerified(true);
        when(orders.findByInvoiceRefAndActiveTrue("INV-41")).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));

        assertThrows(org.pms.silverocean.service.payment.PaymentRequestException.class,()->service.selectPendingPaymentDestination(invoice,direct));

        verify(orders,never()).save(any());verify(invoices,never()).saveInvoice(any());
    }

    @Test void createRiderRequiresVerificationBeforeAssignments(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);when(users.getUserId()).thenReturn(7L);when(stores.findByIdAndOwnerUserIdAndActiveTrue(2L,7L)).thenReturn(Optional.of(store));when(riders.save(any())).thenAnswer(i->i.getArgument(0));when(encryption.encrypt(anyString())).thenReturn(new byte[]{1,2,3});when(i18n.getLocalizedMessage(NotificationType.SOKO_RIDER_CONFIRMATION_SMS.getBody())).thenReturn("Code %s expires in %s minutes");
        var result=service.createRider(new SokoRequests.RiderUpsert(2L,"individual","Jane Rider","254111379961","4567","untrusted@example.test","Motorbike","KDA 123A",null));
        SokoRider rider=result.rider();
        assertEquals("4567",rider.getNationalIdNumber());assertEquals("+254111379961",rider.getPhoneNumber());
        assertEquals("OFFLINE",rider.getAvailability());assertEquals("PENDING_VERIFICATION",rider.getStatus());assertFalse(rider.isVerified());assertNull(rider.getUserId());assertEquals("INDIVIDUAL",rider.getRiderType());verify(users,never()).findByEmail(anyString());verify(users,never()).findByPhone(anyString());
        assertEquals("CODE_QUEUED",result.confirmationStatus());assertNotNull(rider.getPhoneConfirmationExpiresAt());assertArrayEquals(new byte[]{1,2,3},rider.getPhoneConfirmationOtp());
        var queued=org.mockito.ArgumentCaptor.forClass(org.pms.silverocean.service.notification.NotificationDTO.class);verify(notifications).queueNotification(queued.capture());assertEquals("+254111379961",queued.getValue().recipient());assertEquals(NotificationType.SOKO_RIDER_CONFIRMATION_SMS,queued.getValue().notificationType());assertFalse(result.message().matches(".*\\b\\d{6}\\b.*"));
    }

    @Test void correctRiderPhoneCodeActivatesUnlinkedRiderForMerchantManagedDelivery(){
        merchant();SokoRider rider=existingRider();rider.setUserId(null);rider.setPhoneConfirmed(false);rider.setVerified(false);rider.setStatus("PENDING_VERIFICATION");rider.setAvailability("OFFLINE");rider.setPhoneConfirmationStatus("CODE_QUEUED");rider.setPhoneConfirmationOtp(new byte[]{4,5,6});rider.setPhoneConfirmationExpiresAt(ZonedDateTime.now().plusMinutes(5));
        when(riders.findByIdForUpdate(3L)).thenReturn(Optional.of(rider));when(riders.save(any())).thenAnswer(i->i.getArgument(0));when(encryption.decrypt(rider.getPhoneConfirmationOtp())).thenReturn(new DecryptDTO(false,"123456"));
        var result=service.confirmRiderPhone(3L,new SokoRequests.RiderVerificationConfirm("123456"));
        assertEquals("CONFIRMED",result.confirmationStatus());assertTrue(rider.isPhoneConfirmed());assertTrue(rider.isVerified());assertEquals("VERIFIED",rider.getVerificationStatus());assertEquals("ACTIVE",rider.getStatus());assertEquals("AVAILABLE",rider.getAvailability());assertNull(rider.getUserId());assertNull(rider.getPhoneConfirmationOtp());assertNotNull(rider.getPhoneConfirmationConfirmedAt());verifyNoInteractions(marketplaceKycGate);
    }

    @Test void confirmedPhoneNeverLinksMerchantSuppliedMismatchedEmailAccount(){
        merchant();SokoRider rider=existingRider();rider.setUserId(null);rider.setEmail("unrelated@example.test");rider.setPhoneNumber("+254712345678");rider.setPhoneConfirmed(false);rider.setVerified(false);rider.setStatus("PENDING_VERIFICATION");rider.setAvailability("OFFLINE");rider.setPhoneConfirmationStatus("CODE_QUEUED");rider.setPhoneConfirmationOtp(new byte[]{4,5,6});rider.setPhoneConfirmationExpiresAt(ZonedDateTime.now().plusMinutes(5));
        var phoneOwner=new org.pms.silverocean.database.pms.entities.Users();phoneOwner.setId(9L);phoneOwner.setActive(true);phoneOwner.setVerified(true);phoneOwner.setEmailVerified(true);phoneOwner.setAccountStatus("ACTIVE");var emailOwner=new org.pms.silverocean.database.pms.entities.Users();emailOwner.setId(8L);
        when(riders.findByIdForUpdate(3L)).thenReturn(Optional.of(rider));when(riders.save(any())).thenAnswer(i->i.getArgument(0));when(encryption.decrypt(rider.getPhoneConfirmationOtp())).thenReturn(new DecryptDTO(false,"123456"));when(users.findByPhone("+254712345678")).thenReturn(Optional.of(phoneOwner));lenient().when(users.findByEmail("unrelated@example.test")).thenReturn(Optional.of(emailOwner));
        service.confirmRiderPhone(3L,new SokoRequests.RiderVerificationConfirm("123456"));
        assertEquals(9L,rider.getUserId());verify(users,never()).findByEmail(anyString());verifyNoInteractions(marketplaceKycGate);
    }

    @Test void riderPhoneConfirmationLocksAfterBoundedIncorrectAttempts(){
        merchant();org.springframework.test.util.ReflectionTestUtils.setField(service,"riderPhoneConfirmationMaxAttempts",5);SokoRider rider=existingRider();rider.setPhoneConfirmed(false);rider.setVerified(false);rider.setStatus("PENDING_VERIFICATION");rider.setPhoneConfirmationStatus("CODE_QUEUED");rider.setPhoneConfirmationOtp(new byte[]{4,5,6});rider.setPhoneConfirmationExpiresAt(ZonedDateTime.now().plusMinutes(5));
        when(riders.findByIdForUpdate(3L)).thenReturn(Optional.of(rider));when(riders.save(any())).thenAnswer(i->i.getArgument(0));when(encryption.decrypt(any())).thenReturn(new DecryptDTO(false,"123456"));
        for(int attempt=1;attempt<=5;attempt++)assertThrows(PMSCustomException.class,()->service.confirmRiderPhone(3L,new SokoRequests.RiderVerificationConfirm("000000")));
        assertEquals("LOCKED",rider.getPhoneConfirmationStatus());assertNull(rider.getPhoneConfirmationOtp());assertNull(rider.getPhoneConfirmationExpiresAt());assertFalse(rider.isVerified());
    }

    @Test void riderPhoneConfirmationResendHonorsCooldownWithoutReplacingUsableCode(){
        merchant();SokoRider rider=existingRider();rider.setPhoneConfirmed(false);rider.setVerified(false);rider.setStatus("PENDING_VERIFICATION");rider.setAvailability("OFFLINE");rider.setPhoneConfirmationStatus("CODE_QUEUED");rider.setPhoneConfirmationOtp(new byte[]{4,5,6});rider.setPhoneConfirmationRequestedAt(ZonedDateTime.now());rider.setPhoneConfirmationExpiresAt(ZonedDateTime.now().plusMinutes(5));
        when(riders.findByIdForUpdate(3L)).thenReturn(Optional.of(rider));
        var result=service.requestRiderPhoneConfirmation(3L);
        assertEquals("CODE_ALREADY_QUEUED",result.confirmationStatus());assertArrayEquals(new byte[]{4,5,6},rider.getPhoneConfirmationOtp());verify(notifications,never()).queueNotification(any());verify(encryption,never()).encrypt(anyString());
    }

    @Test void lockedRiderConfirmationCannotBypassResendCooldown(){
        merchant();SokoRider rider=existingRider();rider.setPhoneConfirmed(false);rider.setVerified(false);rider.setStatus("PENDING_VERIFICATION");rider.setAvailability("OFFLINE");rider.setPhoneConfirmationStatus("LOCKED");rider.setPhoneConfirmationOtp(null);rider.setPhoneConfirmationRequestedAt(ZonedDateTime.now());rider.setPhoneConfirmationExpiresAt(null);
        when(riders.findByIdForUpdate(3L)).thenReturn(Optional.of(rider));
        assertThrows(PMSCustomException.class,()->service.requestRiderPhoneConfirmation(3L));verify(notifications,never()).queueNotification(any());verify(encryption,never()).encrypt(anyString());
    }

    @Test void expiredRiderPhoneCodeIsInvalidatedAndMustBeResent(){
        merchant();SokoRider rider=existingRider();rider.setPhoneConfirmed(false);rider.setVerified(false);rider.setStatus("PENDING_VERIFICATION");rider.setPhoneConfirmationStatus("CODE_QUEUED");rider.setPhoneConfirmationOtp(new byte[]{4,5,6});rider.setPhoneConfirmationExpiresAt(ZonedDateTime.now().minusSeconds(1));
        when(riders.findByIdForUpdate(3L)).thenReturn(Optional.of(rider));when(riders.save(any())).thenAnswer(i->i.getArgument(0));
        assertThrows(PMSCustomException.class,()->service.confirmRiderPhone(3L,new SokoRequests.RiderVerificationConfirm("123456")));
        assertEquals("EXPIRED",rider.getPhoneConfirmationStatus());assertNull(rider.getPhoneConfirmationOtp());verify(encryption,never()).decrypt(any());
    }

    @Test void draftShopProductPublishReturnsActionableReason(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setStatus("DRAFT");store.setActive(true);SokoProduct product=new SokoProduct();product.setId(5L);product.setStoreId(2L);product.setActive(true);product.setStatus("DRAFT");
        when(users.getUserId()).thenReturn(7L);when(products.findByIdForUpdate(5L)).thenReturn(Optional.of(product));when(stores.findByIdAndOwnerUserIdAndActiveTrue(2L,7L)).thenReturn(Optional.of(store));
        PMSCustomException failure=assertThrows(PMSCustomException.class,()->service.publishProduct(5L));assertTrue(String.valueOf(failure.getData()).contains("approval"));verify(products,never()).save(any());
    }

    @Test void productPublishNormalizesLegacyGroceryLabelAndWorksForApprovedShop(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setStatus("PUBLISHED");store.setActive(true);SokoProduct product=new SokoProduct();product.setId(5L);product.setStoreId(2L);product.setName("Carrots");product.setCategory("Fresh produce");product.setImageUrl("https://images.example.test/carrots.jpg");product.setStockQuantity(1000);product.setActive(true);product.setStatus("DRAFT");
        when(users.getUserId()).thenReturn(7L);when(products.findByIdForUpdate(5L)).thenReturn(Optional.of(product));when(stores.findByIdAndOwnerUserIdAndActiveTrue(2L,7L)).thenReturn(Optional.of(store));when(products.save(any())).thenAnswer(i->i.getArgument(0));
        SokoProduct published=service.publishProduct(5L);assertEquals("FRESH_PRODUCE",published.getCategory());assertEquals("PUBLISHED",published.getStatus());verify(marketplaceKycGate).require(eq(7L),eq("SOKO_CATEGORY"),eq("FRESH_PRODUCE"),any());
    }

    @Test void legacyNonGroceryDraftCannotBePublished(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setStatus("PUBLISHED");store.setActive(true);SokoProduct product=new SokoProduct();product.setId(5L);product.setStoreId(2L);product.setName("Headphones");product.setCategory("Electronics & accessories");product.setImageUrl("https://images.example.test/headphones.jpg");product.setStockQuantity(2);product.setActive(true);product.setStatus("DRAFT");
        when(users.getUserId()).thenReturn(7L);when(products.findByIdForUpdate(5L)).thenReturn(Optional.of(product));when(stores.findByIdAndOwnerUserIdAndActiveTrue(2L,7L)).thenReturn(Optional.of(store));
        assertThrows(PMSCustomException.class,()->service.publishProduct(5L));verifyNoInteractions(marketplaceKycGate);verify(products,never()).save(any());
    }

    @Test void productImageUploadUsesServerGeneratedStorageKey() throws Exception {
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);
        SokoProduct product=new SokoProduct();product.setId(5L);product.setStoreId(2L);product.setActive(true);
        when(users.getUserId()).thenReturn(7L);when(products.findByIdForUpdate(5L)).thenReturn(Optional.of(product));when(products.findById(5L)).thenReturn(Optional.of(product));
        when(stores.findByIdAndOwnerUserIdAndActiveTrue(2L,7L)).thenReturn(Optional.of(store));
        when(productImages.findAllByProductIdAndActiveTrueOrderByDisplayOrderAsc(5L)).thenReturn(List.of());
        byte[] png={(byte)0x89,'P','N','G',13,10,26,10,0};
        service.replaceProductImages(5L,List.of(new MockMultipartFile("images","unsafe/../name.png","image/png",png)));
        verify(garage).uploadBytes(matches("soko/products/5/[0-9a-f-]+/[0-9a-f-]+\\.png"),eq(png),eq("image/png"));
        verify(productImages).saveAll(argThat(rows->{var iterator=rows.iterator();return iterator.hasNext()&&iterator.next().getDisplayOrder()==0&&!iterator.hasNext();}));
    }

    @Test void productImageUploadRejectsSpoofedContent() {
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);
        SokoProduct product=new SokoProduct();product.setId(5L);product.setStoreId(2L);product.setActive(true);
        when(users.getUserId()).thenReturn(7L);when(products.findByIdForUpdate(5L)).thenReturn(Optional.of(product));
        when(stores.findByIdAndOwnerUserIdAndActiveTrue(2L,7L)).thenReturn(Optional.of(store));
        var file=new MockMultipartFile("images","fake.jpg","image/jpeg","not-an-image".getBytes());
        assertThrows(PMSCustomException.class,()->service.replaceProductImages(5L,List.of(file)));
        verifyNoInteractions(garage);
    }

    @Test void dispatchAssignsOnlyVerifiedRiderAndWaitsForCollectionBeforeIssuingCode(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setStatus("PACKED");order.setDeliveryMethod("DELIVERY");order.setDeliveryFailedAt(ZonedDateTime.now().minusMinutes(2));order.setDeliveryExceptionReason("Previous rider had a private problem");order.setActive(true);SokoRider rider=new SokoRider();rider.setId(3L);rider.setStoreId(2L);rider.setUserId(8L);rider.setPhoneConfirmed(true);rider.setVerified(true);rider.setStatus("ACTIVE");rider.setAvailability("AVAILABLE");rider.setDisplayName("Jane Rider");rider.setPhoneNumber("0712345678");rider.setVehiclePlate("KDA 123A");rider.setActive(true);
        when(users.getUserId()).thenReturn(7L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(rider));when(stores.findById(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());
        var result=service.transition(9L,"DISPATCHED",new SokoRequests.Dispatch(3L,null,null,null,
                java.time.LocalDateTime.now(java.time.ZoneId.of("Africa/Nairobi")).plusHours(1)));
        assertEquals("DELIVERY_ASSIGNED",result.order().status());assertEquals("BUSY",rider.getAvailability());assertEquals(3L,result.order().riderId());assertEquals("Jane Rider",result.order().courierName());assertNull(order.getEncryptedDeliveryCode());assertNull(order.getDeliveryCodeExpiresAt());assertNull(order.getDeliveryFailedAt());assertNull(order.getDeliveryExceptionReason());verifyNoInteractions(marketplaceKycGate);
    }

    @Test void repeatingTheCurrentMerchantStatusIsAnIdempotentRead(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);
        ZonedDateTime confirmedAt=ZonedDateTime.now().minusMinutes(1);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setStatus("CONFIRMED");order.setConfirmedAt(confirmedAt);order.setActive(true);
        when(users.getUserId()).thenReturn(7L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(stores.findById(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());

        var result=service.transition(9L,"CONFIRMED",new SokoRequests.Dispatch(null,null,null,null,null));

        assertEquals("CONFIRMED",result.order().status());assertEquals(confirmedAt,order.getConfirmedAt());verify(orders,never()).save(any());verifyNoInteractions(riders,encryption,notifications,businessAlerts);
    }

    @Test void repeatedDispatchAfterManagedRiderAssignmentReturnsThePriorResultSafely(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setStatus("DELIVERY_ASSIGNED");order.setDeliveryMethod("DELIVERY");order.setRiderId(3L);order.setAssignedAt(ZonedDateTime.now().minusSeconds(5));order.setActive(true);
        when(users.getUserId()).thenReturn(7L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(stores.findById(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());

        var result=service.transition(9L,"DISPATCHED",new SokoRequests.Dispatch(3L,null,null,null,java.time.LocalDateTime.now().plusHours(1)));

        assertEquals("DELIVERY_ASSIGNED",result.order().status());assertNull(order.getDispatchedAt());verify(orders,never()).save(any());verifyNoInteractions(riders,visitors,encryption,notifications,businessAlerts);
    }

    @Test void merchantCanDispatchAndSecurelyCompletePhoneConfirmedUnlinkedRiderWithoutRiderLogin() throws Exception {
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setName("Fresh Corner");store.setActive(true);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setOrderNumber("SOKO-9");order.setStatus("PACKED");order.setDeliveryMethod("DELIVERY");order.setActive(true);
        SokoRider rider=existingRider();rider.setUserId(null);
        java.util.concurrent.atomic.AtomicReference<String> issuedCode=new java.util.concurrent.atomic.AtomicReference<>();
        when(users.getUserId()).thenReturn(7L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(stores.findById(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(rider));when(encryption.encrypt(anyString())).thenAnswer(call->{issuedCode.set(call.getArgument(0));return new byte[]{1,2,3};});
        var dispatched=service.transition(9L,"DISPATCHED",new SokoRequests.Dispatch(3L,null,null,null,java.time.LocalDateTime.now(java.time.ZoneId.of("Africa/Nairobi")).plusHours(1)));
        assertEquals("DISPATCHED",dispatched.order().status());assertNull(order.getAssignmentAcceptedAt());assertNotNull(order.getCollectedAt());assertNotNull(order.getDispatchedAt());assertEquals("BUSY",rider.getAvailability());assertArrayEquals(new byte[]{1,2,3},order.getEncryptedDeliveryCode());assertNotNull(order.getDeliveryCodeExpiresAt());assertNotNull(issuedCode.get());

        byte[] png={(byte)0x89,'P','N','G',13,10,26,10,0};service.uploadDeliveryProof(9L,new MockMultipartFile("proof","handover.png","image/png",png));when(encryption.decrypt(order.getEncryptedDeliveryCode())).thenAnswer(call->new DecryptDTO(false,issuedCode.get()));
        var completed=service.confirmDelivery(9L,new SokoRequests.DeliveryConfirmation(issuedCode.get(),"Jane Buyer","merchant handover"));
        assertEquals("COMPLETED",completed.status());assertTrue(order.isDeliveryCodeVerified());assertEquals("AVAILABLE",rider.getAvailability());assertEquals(1,rider.getCompletedDeliveries());verifyNoInteractions(marketplaceKycGate);
    }

    @Test void owningMerchantCanFailAndReturnPhoneConfirmedUnlinkedRiderDelivery(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setName("Fresh Corner");store.setActive(true);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setStatus("DISPATCHED");order.setDeliveryMethod("DELIVERY");order.setPaymentStatus("PARTIALLY_REFUNDED");order.setRefundStatus("CONFIRMED");order.setTotal(new BigDecimal("100"));order.setRefundedAmount(new BigDecimal("25"));order.setRiderId(3L);order.setEncryptedDeliveryCode(new byte[]{1,2,3});order.setActive(true);
        SokoRider rider=existingRider();rider.setUserId(null);rider.setAvailability("BUSY");
        when(users.getUserId()).thenReturn(7L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(rider));

        var failed=service.failDelivery(9L,new SokoRequests.DeliveryException("Customer was unavailable"));
        assertEquals("DELIVERY_RETURN_REQUIRED",failed.status());assertNull(order.getEncryptedDeliveryCode());assertEquals("BUSY",rider.getAvailability());
        var returned=service.returnDelivery(9L,new SokoRequests.DeliveryException("Groceries returned to shop"));
        assertEquals("RETURNED",returned.status());assertEquals("REQUESTED",order.getRefundStatus());
    }

    @Test void preCollectionFailureReleasesRiderForReassignmentAndRetryIsIdempotent(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setStatus("ASSIGNMENT_ACCEPTED");order.setDeliveryMethod("DELIVERY");order.setRiderId(3L);order.setActive(true);
        SokoRider rider=existingRider();rider.setUserId(8L);rider.setAvailability("BUSY");
        when(users.getUserId()).thenReturn(8L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(rider));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));

        service.failDelivery(9L,new SokoRequests.DeliveryException("Motorbike fault before collection"));
        service.failDelivery(9L,new SokoRequests.DeliveryException("Lost response retry"));

        assertEquals("PACKED",order.getStatus());assertEquals("AVAILABLE",rider.getAvailability());assertNotNull(order.getDeliveryFailedAt());
        verify(riders,times(1)).save(rider);verify(orders,times(1)).save(order);
    }

    @Test void linkedRiderAcceptAndCollectionRetriesReturnTheRecordedSuccess(){
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setStatus("DELIVERY_ASSIGNED");order.setDeliveryMethod("DELIVERY");order.setRiderId(3L);order.setActive(true);
        SokoRider rider=existingRider();rider.setUserId(8L);rider.setAvailability("BUSY");
        when(users.getUserId()).thenReturn(8L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(rider));when(encryption.encrypt(anyString())).thenReturn(new byte[]{1,2,3});

        service.acceptAssignment(9L);service.acceptAssignment(9L);
        assertEquals("ASSIGNMENT_ACCEPTED",order.getStatus());verify(orders,times(1)).save(order);
        clearInvocations(orders);
        service.confirmCollection(9L);byte[] originalCode=order.getEncryptedDeliveryCode();service.confirmCollection(9L);
        assertEquals("DISPATCHED",order.getStatus());assertArrayEquals(originalCode,order.getEncryptedDeliveryCode());verify(orders,times(1)).save(order);verify(encryption,times(1)).encrypt(anyString());
    }

    @Test void cancellingPartiallyRefundedOrderRequestsRemainingRefund(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setName("Fresh Corner");store.setActive(true);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setOrderNumber("SOKO-9");order.setStoreId(2L);order.setCustomerUserId(4L);order.setStatus("CONFIRMED");order.setPaymentStatus("PARTIALLY_REFUNDED");order.setRefundStatus("CONFIRMED");order.setTotal(new BigDecimal("100"));order.setRefundedAmount(new BigDecimal("25"));order.setActive(true);
        when(users.getUserId()).thenReturn(4L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(stores.findById(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());
        service.cancel(9L,new SokoRequests.Cancellation("Buyer cancelled before dispatch"));
        assertEquals("CANCELLED",order.getStatus());assertEquals("REQUESTED",order.getRefundStatus());assertEquals(new BigDecimal("25"),order.getRefundedAmount());
    }

    @Test void otherMerchantCannotFailPhoneConfirmedUnlinkedRiderDelivery(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setStatus("DISPATCHED");order.setRiderId(3L);order.setActive(true);
        SokoRider rider=existingRider();rider.setUserId(null);rider.setAvailability("BUSY");
        when(users.getUserId()).thenReturn(99L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(rider));
        assertThrows(PMSCustomException.class,()->service.failDelivery(9L,new SokoRequests.DeliveryException("Not my delivery")));assertEquals("DISPATCHED",order.getStatus());assertEquals("BUSY",rider.getAvailability());verify(orders,never()).save(any());
    }

    @Test void merchantCannotImpersonateLinkedRiderAcceptanceOrCollection(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setStatus("DELIVERY_ASSIGNED");order.setRiderId(3L);order.setActive(true);
        SokoRider rider=existingRider();rider.setUserId(8L);rider.setAvailability("BUSY");
        when(users.getUserId()).thenReturn(7L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(rider));
        assertThrows(PMSCustomException.class,()->service.transition(9L,"ASSIGNMENT_ACCEPTED",null));
        order.setStatus("ASSIGNMENT_ACCEPTED");assertThrows(PMSCustomException.class,()->service.transition(9L,"DISPATCHED",null));
        verify(orders,never()).save(any());verifyNoInteractions(encryption);
    }

    @Test void deliveryCodeCompletesOrderAndReleasesPreferredRider(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setStatus("DISPATCHED");order.setDeliveryMethod("DELIVERY");order.setDeliveryCode("123456");order.setDeliveryCodeExpiresAt(ZonedDateTime.now().plusHours(1));order.setDeliveryProofReference("soko/delivery-proof/9/proof.jpg");order.setRiderId(3L);order.setActive(true);SokoRider rider=new SokoRider();rider.setId(3L);rider.setStoreId(2L);rider.setUserId(8L);rider.setStatus("ACTIVE");rider.setAvailability("BUSY");rider.setActive(true);
        rider.setPhoneConfirmed(true);rider.setVerified(true);
        when(users.getUserId()).thenReturn(8L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(rider));
        var result=service.confirmDelivery(9L,new SokoRequests.DeliveryConfirmation("123456"));
        var retry=service.confirmDelivery(9L,new SokoRequests.DeliveryConfirmation("123456"));
        assertEquals("COMPLETED",result.status());assertTrue(order.isDeliveryCodeVerified());assertEquals("AVAILABLE",rider.getAvailability());assertEquals(1,rider.getCompletedDeliveries());
        assertEquals("COMPLETED",retry.status());verify(riders,times(1)).save(rider);verify(orders,times(1)).save(order);
    }

    @Test void customerCanReadEncryptedDeliveryCodeWithoutExposingCiphertext(){
        SokoOrder order=new SokoOrder();order.setId(9L);order.setCustomerUserId(4L);order.setDeliveryMethod("DELIVERY");order.setStatus("DISPATCHED");order.setEncryptedDeliveryCode(new byte[]{1,2,3});order.setDeliveryCodeExpiresAt(ZonedDateTime.now().plusHours(1));
        when(users.getUserId()).thenReturn(4L);when(orders.findById(9L)).thenReturn(Optional.of(order));when(encryption.decrypt(order.getEncryptedDeliveryCode())).thenReturn(new DecryptDTO(false,"123456"));
        assertEquals("123456",service.deliveryCode(9L));
    }

    @Test void expiredDeliveryCodeCannotBeRead(){
        SokoOrder order=new SokoOrder();order.setId(9L);order.setCustomerUserId(4L);order.setDeliveryMethod("DELIVERY");order.setStatus("DISPATCHED");order.setDeliveryCode("123456");order.setDeliveryCodeExpiresAt(ZonedDateTime.now().minusMinutes(1));
        when(users.getUserId()).thenReturn(4L);when(orders.findById(9L)).thenReturn(Optional.of(order));
        assertThrows(PMSCustomException.class,()->service.deliveryCode(9L));
    }

    @Test void supportInvalidatesOldCodeAndStartsBuyerVerifiedRecoveryWithoutSeeingReplacement(){
        SokoStore store=new SokoStore();store.setId(2L);store.setName("Fresh Corner");store.setActive(true);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setOrderNumber("SOKO-9");order.setStoreId(2L);order.setCustomerUserId(4L);order.setStatus("DISPATCHED");order.setDeliveryMethod("DELIVERY");order.setEncryptedDeliveryCode(new byte[]{9});order.setActive(true);
        var buyer=new org.pms.silverocean.database.pms.entities.Users();buyer.setId(4L);buyer.setEmail("buyer@example.com");buyer.setEmailVerified(true);buyer.setActive(true);
        when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);when(users.getUserId()).thenReturn(99L);when(users.findById(4L)).thenReturn(Optional.of(buyer));when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(orders.save(any())).thenAnswer(i->i.getArgument(0));when(encryption.encrypt(anyString())).thenReturn(new byte[]{1,2,3});when(i18n.getLocalizedMessage(anyString())).thenReturn("Order %s OTP %s expires %s");when(stores.findById(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());
        service.reissueDeliveryCode(9L,new SokoRequests.CodeReissue("Buyer cannot access the original email"));
        assertNull(order.getEncryptedDeliveryCode());assertArrayEquals(new byte[]{1,2,3},order.getDeliveryRecoveryOtp());assertEquals(99L,order.getDeliveryRecoveryRequestedBy());assertEquals(1,order.getDeliveryRecoveryRequestCount());verify(notifications).queueNotification(any());
    }

    @Test void checkoutRejectsSelfOrdersBeforeStockOrInvoiceMutation(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(4L);store.setStatus("PUBLISHED");store.setActive(true);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"self-order")).thenReturn(Optional.empty());when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"PICKUP",null,"0712345678",null,null);

        PMSCustomException failure=assertThrows(PMSCustomException.class,()->service.checkout(request,"self-order"));

        assertTrue(String.valueOf(failure.getData()).contains("own Soko shop"));verify(stores).findByIdForCheckout(2L);verify(orders,never()).findPendingCheckoutForUpdate(anyLong(),anyLong(),any());verifyNoInteractions(products,invoices);
    }

    @Test void checkoutEnforcesProductionCartCapsEvenWhenCalledOutsideTheController(){
        when(users.getUserId()).thenReturn(4L);
        var tooManyLines=new SokoRequests.Checkout(2L,java.util.stream.LongStream.rangeClosed(1,26).mapToObj(id->new SokoRequests.CheckoutItem(id,1)).toList(),"PICKUP",null,"0712345678",null,null);
        var excessiveQuantity=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,101)),"PICKUP",null,"0712345678",null,null);

        assertThrows(PMSCustomException.class,()->service.checkout(tooManyLines,"too-many-lines"));
        assertThrows(PMSCustomException.class,()->service.checkout(excessiveQuantity,"too-much-stock"));

        verifyNoInteractions(stores,products,variations,orders,items,invoices,accounts);
    }

    @Test void checkoutBlocksASecondLiveUnpaidOrderAfterTakingTheStoreLock(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setStatus("PUBLISHED");store.setActive(true);
        SokoOrder unpaid=new SokoOrder();unpaid.setId(9L);unpaid.setStoreId(2L);unpaid.setCustomerUserId(4L);unpaid.setStatus("PENDING_PAYMENT");unpaid.setReservationExpiresAt(ZonedDateTime.now().plusMinutes(5));unpaid.setActive(true);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"second-order")).thenReturn(Optional.empty());when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(orders.findPendingCheckoutForUpdate(eq(4L),eq(2L),any())).thenReturn(List.of(unpaid));
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"PICKUP",null,"0712345678",null,null);

        PMSCustomException failure=assertThrows(PMSCustomException.class,()->service.checkout(request,"second-order"));

        assertTrue(String.valueOf(failure.getData()).contains("unpaid order"));verify(stores).findByIdForCheckout(2L);verify(orders,times(2)).findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"second-order");verifyNoInteractions(products,invoices);
    }

    @Test void concurrentSameKeyRechecksAfterStoreLockAndReplaysTheWinningCheckout(){
        SokoStore store=new SokoStore();store.setId(2L);store.setName("Fresh Corner");store.setOwnerUserId(7L);store.setStatus("PUBLISHED");store.setActive(true);
        SokoOrder winner=new SokoOrder();winner.setId(9L);winner.setStoreId(2L);winner.setCustomerUserId(4L);winner.setCheckoutIdempotencyKey("same-key");winner.setDeliveryMethod("PICKUP");winner.setCustomerPhone("0712345678");winner.setActive(true);
        SokoOrderItem item=new SokoOrderItem();item.setOrderId(9L);item.setProductId(5L);item.setQuantity(1);item.setActive(true);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"same-key")).thenReturn(Optional.empty(),Optional.of(winner));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(stores.findById(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of(item));
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"PICKUP",null,"0712345678",null,null);

        assertEquals(9L,service.checkout(request,"same-key").order().id());

        verify(stores).findByIdForCheckout(2L);verify(orders,times(2)).findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"same-key");verify(orders,never()).findPendingCheckoutForUpdate(anyLong(),anyLong(),any());verifyNoInteractions(products,invoices);
    }

    @Test void newCheckoutRejectsSellerWithoutActiveSokoSubscriptionBeforeStockReservation(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setStatus("PUBLISHED");store.setActive(true);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"inactive-seller")).thenReturn(Optional.empty());when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));
        doThrow(new PMSCustomException(org.pms.silverocean.common.ResponseCode.SUBSCRIPTION_ACCESS_REQUIRED))
                .when(subscriptionEntitlements).requireActiveProductForOwner(7L,SubscriptionProduct.SOKO);
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"PICKUP",null,"0712345678",null,null);

        assertThrows(PMSCustomException.class,()->service.checkout(request,"inactive-seller"));

        verify(stores).findByIdForCheckout(2L);verify(subscriptionEntitlements).requireActiveProductForOwner(7L,SubscriptionProduct.SOKO);
        verify(orders,never()).findPendingCheckoutForUpdate(anyLong(),anyLong(),any());verifyNoInteractions(products,invoices);
    }

    @Test void expiredUnpaidOrderIsReleasedUnderTheCheckoutLockBeforeTheReplacementReservesStock(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setName("Fresh Corner");store.setStatus("PUBLISHED");store.setActive(true);store.setPickupEnabled(true);store.setAddress("Market Road");store.setLatitude(-1.28);store.setLongitude(36.82);store.setCurrency("KES");store.setPaymentAccountId(3L);
        var account=new org.pms.silverocean.database.pms.entities.PaymentAccount();account.setActive(true);account.setVerified(true);account.setCategory(org.pms.silverocean.service.account.enums.AccountCategory.MERCHANT);account.setChannel(org.pms.silverocean.service.payment.wrappers.PaymentChannel.MPESA);
        SokoOrder expired=new SokoOrder();expired.setId(9L);expired.setOrderNumber("SOKO-OLD");expired.setStoreId(2L);expired.setCustomerUserId(4L);expired.setStatus("PENDING_PAYMENT");expired.setPaymentStatus("UNPAID");expired.setRefundStatus("NOT_REQUIRED");expired.setReservationExpiresAt(ZonedDateTime.now().minusMinutes(1));expired.setActive(true);
        SokoOrderItem reserved=new SokoOrderItem();reserved.setOrderId(9L);reserved.setProductId(5L);reserved.setQuantity(1);reserved.setActive(true);
        SokoProduct product=new SokoProduct();product.setId(5L);product.setStoreId(2L);product.setName("Milk");product.setUnit("item");product.setPrice(new BigDecimal("100"));product.setCurrency("KES");product.setStockQuantity(0);product.setStatus("OUT_OF_STOCK");product.setActive(true);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"replacement")).thenReturn(Optional.empty());when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(orders.findPendingCheckoutForUpdate(eq(4L),eq(2L),any())).thenReturn(List.of(expired));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of(reserved));when(products.findByIdForUpdate(5L)).thenReturn(Optional.of(product));when(variations.findAllByProductIdAndActiveTrueOrderByNameAscValueAsc(5L)).thenReturn(List.of());when(accounts.getAccountByIdAndCreatedBy(3L,7L)).thenReturn(account);when(accounts.getAccountById(3L)).thenReturn(account);when(orders.save(any())).thenAnswer(invocation->{SokoOrder saved=invocation.getArgument(0);if(saved.getId()==null)saved.setId(10L);return saved;});when(items.save(any())).thenAnswer(invocation->invocation.getArgument(0));doAnswer(invocation->{org.pms.silverocean.database.pms.entities.PMSInvoice invoice=invocation.getArgument(0);invoice.setRef("INV-10");return null;}).when(invoices).createInvoice(any());
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"PICKUP",null,"0712345678",null,null);

        var replacement=service.checkout(request,"replacement");

        assertEquals(10L,replacement.order().id());assertEquals("EXPIRED",expired.getStatus());assertTrue(expired.isStockReleased());assertEquals(0,product.getStockQuantity());assertEquals("OUT_OF_STOCK",product.getStatus());verify(invoices).createInvoice(any());
    }

    @Test void checkoutLocksSelectedVariationAndUsesItsPriceAndStock(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setName("Fresh Corner");store.setStatus("PUBLISHED");store.setActive(true);store.setPickupEnabled(true);store.setAddress("Market Road");store.setLatitude(-1.28);store.setLongitude(36.82);store.setCurrency("KES");store.setPaymentAccountId(3L);
        var account=new org.pms.silverocean.database.pms.entities.PaymentAccount();account.setActive(true);account.setVerified(true);account.setCategory(org.pms.silverocean.service.account.enums.AccountCategory.MERCHANT);account.setChannel(org.pms.silverocean.service.payment.wrappers.PaymentChannel.MPESA);
        SokoProduct product=new SokoProduct();product.setId(5L);product.setStoreId(2L);product.setName("Shirt");product.setUnit("item");product.setPrice(new BigDecimal("1000"));product.setCurrency("KES");product.setStockQuantity(3);product.setStatus("PUBLISHED");product.setActive(true);
        SokoProductVariation variation=new SokoProductVariation();variation.setId(11L);variation.setProductId(5L);variation.setName("Size");variation.setValue("Large");variation.setPriceAdjustment(new BigDecimal("100"));variation.setStockQuantity(2);variation.setActive(true);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"variant-1")).thenReturn(Optional.empty());when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(accounts.getAccountByIdAndCreatedBy(3L,7L)).thenReturn(account);when(accounts.getAccountById(3L)).thenReturn(account);when(products.findByIdForUpdate(5L)).thenReturn(Optional.of(product));when(variations.findAllByProductIdAndActiveTrueOrderByNameAscValueAsc(5L)).thenReturn(List.of(variation));when(variations.findForUpdate(11L,5L)).thenReturn(Optional.of(variation));when(orders.save(any())).thenAnswer(invocation->{SokoOrder saved=invocation.getArgument(0);if(saved.getId()==null)saved.setId(9L);return saved;});when(items.save(any())).thenAnswer(invocation->invocation.getArgument(0));doAnswer(invocation->{org.pms.silverocean.database.pms.entities.PMSInvoice invoice=invocation.getArgument(0);invoice.setRef("INV-9");return null;}).when(invoices).createInvoice(any());
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,2,11L)),"PICKUP",null,"0712345678",null,null);
        var result=service.checkout(request,"variant-1");
        assertEquals(new BigDecimal("2200"),result.order().total());assertEquals(1,product.getStockQuantity());assertEquals(0,variation.getStockQuantity());assertEquals("Large",result.items().getFirst().variationValue());assertEquals(new BigDecimal("1100"),result.items().getFirst().unitPrice());
        var invoice=org.mockito.ArgumentCaptor.forClass(org.pms.silverocean.database.pms.entities.PMSInvoice.class);verify(invoices).createInvoice(invoice.capture());assertEquals(new BigDecimal("2200.00"),invoice.getValue().moneyAmount());assertEquals(3L,invoice.getValue().getPaymentAccountId());assertEquals(7L,invoice.getValue().getPayToUserId());
    }

    @Test void checkoutLocksProductsInCanonicalOrderToAvoidInverseCartDeadlocks(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setName("Fresh Corner");store.setStatus("PUBLISHED");store.setActive(true);store.setPickupEnabled(true);store.setAddress("Market Road");store.setLatitude(-1.28);store.setLongitude(36.82);store.setCurrency("KES");store.setPaymentAccountId(3L);
        var account=new org.pms.silverocean.database.pms.entities.PaymentAccount();account.setActive(true);account.setVerified(true);account.setCategory(org.pms.silverocean.service.account.enums.AccountCategory.MERCHANT);account.setChannel(org.pms.silverocean.service.payment.wrappers.PaymentChannel.MPESA);
        SokoProduct first=new SokoProduct();first.setId(5L);first.setStoreId(2L);first.setName("Milk");first.setUnit("item");first.setPrice(new BigDecimal("100"));first.setCurrency("KES");first.setStockQuantity(5);first.setStatus("PUBLISHED");first.setActive(true);
        SokoProduct second=new SokoProduct();second.setId(9L);second.setStoreId(2L);second.setName("Bread");second.setUnit("item");second.setPrice(new BigDecimal("80"));second.setCurrency("KES");second.setStockQuantity(5);second.setStatus("PUBLISHED");second.setActive(true);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"canonical-locks")).thenReturn(Optional.empty());when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(accounts.getAccountByIdAndCreatedBy(3L,7L)).thenReturn(account);when(accounts.getAccountById(3L)).thenReturn(account);when(products.findByIdForUpdate(5L)).thenReturn(Optional.of(first));when(products.findByIdForUpdate(9L)).thenReturn(Optional.of(second));when(variations.findAllByProductIdAndActiveTrueOrderByNameAscValueAsc(anyLong())).thenReturn(List.of());when(orders.save(any())).thenAnswer(invocation->{SokoOrder saved=invocation.getArgument(0);if(saved.getId()==null)saved.setId(12L);return saved;});when(items.save(any())).thenAnswer(invocation->invocation.getArgument(0));doAnswer(invocation->{org.pms.silverocean.database.pms.entities.PMSInvoice invoice=invocation.getArgument(0);invoice.setRef("INV-12");return null;}).when(invoices).createInvoice(any());
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(9L,1),new SokoRequests.CheckoutItem(5L,2)),"PICKUP",null,"0712345678",null,null);
        var result=service.checkout(request,"canonical-locks");
        var lockOrder=inOrder(products);lockOrder.verify(products).findByIdForUpdate(5L);lockOrder.verify(products).findByIdForUpdate(9L);
        assertEquals(List.of(5L,9L),result.items().stream().map(SokoModels.OrderItemView::productId).toList());assertEquals(new BigDecimal("280"),result.order().total());
    }

    @Test void checkoutRejectsAProductFromAnotherSellerBeforeCreatingAnOrderOrInvoice(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setStatus("PUBLISHED");store.setActive(true);store.setPickupEnabled(true);store.setAddress("Market Road");store.setLatitude(-1.28);store.setLongitude(36.82);store.setCurrency("KES");store.setPaymentAccountId(3L);
        var account=new org.pms.silverocean.database.pms.entities.PaymentAccount();account.setActive(true);account.setVerified(true);account.setCategory(org.pms.silverocean.service.account.enums.AccountCategory.MERCHANT);account.setChannel(org.pms.silverocean.service.payment.wrappers.PaymentChannel.MPESA);
        SokoProduct otherSellerProduct=new SokoProduct();otherSellerProduct.setId(5L);otherSellerProduct.setStoreId(99L);otherSellerProduct.setStatus("PUBLISHED");otherSellerProduct.setStockQuantity(10);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"one-seller-only")).thenReturn(Optional.empty());when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(accounts.getAccountByIdAndCreatedBy(3L,7L)).thenReturn(account);when(products.findByIdForUpdate(5L)).thenReturn(Optional.of(otherSellerProduct));
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"PICKUP",null,"0712345678",null,null);
        assertThrows(PMSCustomException.class,()->service.checkout(request,"one-seller-only"));
        verify(orders,never()).save(any());verify(invoices,never()).createInvoice(any());verify(products,never()).save(any());
    }

    @Test void checkoutRejectsProductWhoseCurrencyDoesNotMatchItsShopBeforeStockMutation(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setStatus("PUBLISHED");store.setActive(true);store.setPickupEnabled(true);store.setAddress("Market Road");store.setLatitude(-1.28);store.setLongitude(36.82);store.setCurrency("KES");store.setPaymentAccountId(3L);
        var account=new org.pms.silverocean.database.pms.entities.PaymentAccount();account.setActive(true);account.setVerified(true);account.setCategory(org.pms.silverocean.service.account.enums.AccountCategory.MERCHANT);account.setChannel(org.pms.silverocean.service.payment.wrappers.PaymentChannel.MPESA);
        SokoProduct product=new SokoProduct();product.setId(5L);product.setStoreId(2L);product.setStatus("PUBLISHED");product.setCurrency("USD");product.setStockQuantity(10);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"currency-mismatch")).thenReturn(Optional.empty());when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(accounts.getAccountByIdAndCreatedBy(3L,7L)).thenReturn(account);when(products.findByIdForUpdate(5L)).thenReturn(Optional.of(product));
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"PICKUP",null,"0712345678",null,null);
        assertThrows(PMSCustomException.class,()->service.checkout(request,"currency-mismatch"));assertEquals(10,product.getStockQuantity());verify(products,never()).save(any());verify(orders,never()).save(any());verify(invoices,never()).createInvoice(any());
    }

    @Test void deliveryCheckoutWithinServiceAreaSnapshotsBuyerPin(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setName("Fresh Corner");store.setStatus("PUBLISHED");store.setActive(true);store.setDeliveryEnabled(true);store.setAddress("Market Road");store.setLatitude(-1.286389);store.setLongitude(36.817223);store.setServiceRadiusKm(new BigDecimal("5"));store.setDeliveryFee(new BigDecimal("100"));store.setCurrency("KES");store.setPaymentAccountId(3L);
        var account=new org.pms.silverocean.database.pms.entities.PaymentAccount();account.setActive(true);account.setVerified(true);account.setCreatedBy(7L);account.setCategory(org.pms.silverocean.service.account.enums.AccountCategory.MERCHANT);account.setChannel(org.pms.silverocean.service.payment.wrappers.PaymentChannel.MPESA);
        SokoProduct product=new SokoProduct();product.setId(5L);product.setStoreId(2L);product.setName("Milk");product.setUnit("litre");product.setPrice(new BigDecimal("120"));product.setCurrency("KES");product.setStockQuantity(3);product.setStatus("PUBLISHED");product.setActive(true);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"delivery-pin-1")).thenReturn(Optional.empty());when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(accounts.getAccountByIdAndCreatedBy(3L,7L)).thenReturn(account);when(accounts.getAccountById(3L)).thenReturn(account);when(products.findByIdForUpdate(5L)).thenReturn(Optional.of(product));when(variations.findAllByProductIdAndActiveTrueOrderByNameAscValueAsc(5L)).thenReturn(List.of());when(orders.save(any())).thenAnswer(invocation->{SokoOrder saved=invocation.getArgument(0);if(saved.getId()==null)saved.setId(9L);return saved;});when(items.save(any())).thenAnswer(invocation->invocation.getArgument(0));doAnswer(invocation->{org.pms.silverocean.database.pms.entities.PMSInvoice invoice=invocation.getArgument(0);invoice.setRef("INV-9");return null;}).when(invoices).createInvoice(any());
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"DELIVERY","Kenyatta Avenue","0712345678",null,null,-1.2900,36.8200);
        var result=service.checkout(request,"delivery-pin-1");
        assertEquals(-1.2900,result.order().deliveryLatitude());assertEquals(36.8200,result.order().deliveryLongitude());assertEquals("Kenyatta Avenue",result.order().deliveryAddress());assertEquals("Fresh Corner",result.storeName());assertEquals("Market Road",result.storeAddress());assertEquals(-1.286389,result.storeLatitude());assertEquals(36.817223,result.storeLongitude());verify(invoices).createInvoice(any());
    }

    @Test void deliveryDestinationsUseOnlyCurrentUsersActiveResidencesAndPreferLatestCompletedAddress(){
        var tenancy=destination(11L,21L,"A-11","Alpha Court","Alpha Road","-1.2862,36.8174");
        SokoDeliveryDestinationProjection malformed=mock(SokoDeliveryDestinationProjection.class);
        when(malformed.getUnitRef()).thenReturn("C-13");when(malformed.getPropertyName()).thenReturn("Corrupt Court");
        when(malformed.getAddress()).thenReturn("Unknown Road");when(malformed.getMapLocation()).thenReturn("not coordinates");
        var ownership=destination(12L,22L,"B-12","Beta Court","Beta Road","-1.2870,36.8180");
        when(users.getUserId()).thenReturn(4L);
        when(units.findAcceptedTenancyDeliveryDestinations(4L)).thenReturn(List.of(tenancy,malformed));
        when(units.findHomeownerDeliveryDestinations(4L)).thenReturn(List.of(ownership));
        when(orders.findRecentCompletedDestinationUnitIds(eq(4L),eq(List.of(11L,12L)),any())).thenReturn(List.of(12L));

        var result=service.deliveryDestinations();

        assertEquals(2,result.size());
        var first=result.getFirst();var second=result.get(1);
        assertEquals(11L,first.unitId());assertEquals("TENANCY",first.source());assertFalse(first.preferred());
        assertEquals("Alpha Court — A-11",first.label());assertEquals("Alpha Road, A-11",first.address());
        assertEquals(12L,second.unitId());assertEquals("HOMEOWNERSHIP",second.source());assertTrue(second.preferred());
        assertEquals(-1.2870,second.latitude());assertEquals(36.8180,second.longitude());
    }

    @Test void soleValidDeliveryDestinationIsPreferredWithoutOrderHistoryLookup(){
        var tenancy=destination(11L,21L,"A-11","Alpha Court","Alpha Road","-1.2862,36.8174");
        when(users.getUserId()).thenReturn(4L);
        when(units.findAcceptedTenancyDeliveryDestinations(4L)).thenReturn(List.of(tenancy));
        when(units.findHomeownerDeliveryDestinations(4L)).thenReturn(List.of());

        var result=service.deliveryDestinations();

        assertEquals(1,result.size());assertTrue(result.getFirst().preferred());
        verify(orders,never()).findRecentCompletedDestinationUnitIds(anyLong(),anyList(),any());
    }

    @Test void deliveryCheckoutRejectsArbitrarySystemUnitBeforeReadingShopOrMutatingStock(){
        when(users.getUserId()).thenReturn(4L);
        when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"unknown-home")).thenReturn(Optional.empty());
        when(units.findAcceptedTenancyDeliveryDestinations(4L)).thenReturn(List.of());
        when(units.findHomeownerDeliveryDestinations(4L)).thenReturn(List.of());
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"DELIVERY",
                "Spoofed address","0712345678",null,999L,-1.2900,36.8200);

        assertThrows(PMSCustomException.class,()->service.checkout(request,"unknown-home"));

        verifyNoInteractions(stores,products,accounts,invoices);
        verify(orders,never()).save(any());
    }

    @Test void deliveryCheckoutUsesServerOwnedDestinationInsteadOfClientAddressAndCoordinates(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setName("Fresh Corner");store.setStatus("PUBLISHED");store.setActive(true);store.setDeliveryEnabled(true);store.setAddress("Market Road");store.setLatitude(-1.286389);store.setLongitude(36.817223);store.setServiceRadiusKm(new BigDecimal("5"));store.setDeliveryFee(new BigDecimal("100"));store.setCurrency("KES");store.setPaymentAccountId(3L);
        var account=new org.pms.silverocean.database.pms.entities.PaymentAccount();account.setActive(true);account.setVerified(true);account.setCreatedBy(7L);account.setCategory(org.pms.silverocean.service.account.enums.AccountCategory.MERCHANT);account.setChannel(org.pms.silverocean.service.payment.wrappers.PaymentChannel.MPESA);
        SokoProduct product=new SokoProduct();product.setId(5L);product.setStoreId(2L);product.setName("Milk");product.setUnit("litre");product.setPrice(new BigDecimal("120"));product.setCurrency("KES");product.setStockQuantity(3);product.setStatus("PUBLISHED");product.setActive(true);
        var home=destination(12L,22L,"B-12","Beta Court","Beta Road","-1.2870,36.8180");
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"saved-home")).thenReturn(Optional.empty());when(units.findAcceptedTenancyDeliveryDestinations(4L)).thenReturn(List.of(home));when(units.findHomeownerDeliveryDestinations(4L)).thenReturn(List.of());when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(accounts.getAccountByIdAndCreatedBy(3L,7L)).thenReturn(account);when(accounts.getAccountById(3L)).thenReturn(account);when(products.findByIdForUpdate(5L)).thenReturn(Optional.of(product));when(variations.findAllByProductIdAndActiveTrueOrderByNameAscValueAsc(5L)).thenReturn(List.of());when(orders.save(any())).thenAnswer(invocation->{SokoOrder saved=invocation.getArgument(0);if(saved.getId()==null)saved.setId(9L);return saved;});when(items.save(any())).thenAnswer(invocation->invocation.getArgument(0));doAnswer(invocation->{org.pms.silverocean.database.pms.entities.PMSInvoice invoice=invocation.getArgument(0);invoice.setRef("INV-9");return null;}).when(invoices).createInvoice(any());
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"DELIVERY",
                "Attacker supplied address","0712345678",null,12L,40.0000,-70.0000);

        var result=service.checkout(request,"saved-home");

        assertEquals(12L,result.order().destinationUnitId());assertEquals("Beta Road, B-12",result.order().deliveryAddress());
        assertEquals(-1.2870,result.order().deliveryLatitude());assertEquals(36.8180,result.order().deliveryLongitude());
        assertEquals(2,product.getStockQuantity());verify(invoices).createInvoice(any());
    }

    @Test void pickupRejectsSavedDestinationBeforeShopOrStockAccess(){
        when(users.getUserId()).thenReturn(4L);
        when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"pickup-home")).thenReturn(Optional.empty());
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"PICKUP",
                null,"0712345678",null,12L,null,null);

        assertThrows(PMSCustomException.class,()->service.checkout(request,"pickup-home"));

        verifyNoInteractions(units,stores,products,accounts,invoices);
        verify(orders,never()).save(any());
    }

    @Test void savedDestinationReplayIgnoresClientAddressButRejectsChangedUnit(){
        SokoStore store=new SokoStore();store.setId(2L);store.setName("Fresh Corner");
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setCheckoutIdempotencyKey("saved-replay");order.setDeliveryMethod("DELIVERY");order.setDeliveryAddress("Beta Road, B-12");order.setDeliveryLatitude(-1.2870);order.setDeliveryLongitude(36.8180);order.setDestinationUnitId(12L);order.setCustomerPhone("0712345678");order.setActive(true);
        SokoOrderItem item=new SokoOrderItem();item.setOrderId(9L);item.setProductId(5L);item.setQuantity(1);item.setActive(true);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"saved-replay")).thenReturn(Optional.of(order));when(stores.findById(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of(item));
        var sameUnit=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"DELIVERY",
                "Spoofed but ignored","0712345678",null,12L,40.0000,-70.0000);
        var changedUnit=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"DELIVERY",
                "Beta Road, B-12","0712345678",null,13L,-1.2870,36.8180);

        assertEquals(9L,service.checkout(sameUnit,"saved-replay").order().id());
        assertThrows(PMSCustomException.class,()->service.checkout(changedUnit,"saved-replay"));
        verifyNoInteractions(units,products,invoices);
    }

    @Test void deliveryCheckoutRejectsMissingOrOutsideLocationBeforeStockAndInvoice(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setStatus("PUBLISHED");store.setActive(true);store.setDeliveryEnabled(true);store.setLatitude(-1.286389);store.setLongitude(36.817223);store.setServiceRadiusKm(new BigDecimal("2"));
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(eq(4L),anyString())).thenReturn(Optional.empty());when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));
        var missing=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"DELIVERY","Kenyatta Avenue","0712345678",null,null,null,null);
        var outside=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"DELIVERY","Thika","0712345678",null,null,-1.0332,37.0693);
        assertThrows(PMSCustomException.class,()->service.checkout(missing,"delivery-missing"));assertThrows(PMSCustomException.class,()->service.checkout(outside,"delivery-outside"));verifyNoInteractions(products,invoices,accounts);
    }

    @Test void deliveryCheckoutRejectsShopWithoutMappedServiceArea(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setStatus("PUBLISHED");store.setActive(true);store.setDeliveryEnabled(true);store.setServiceRadiusKm(new BigDecimal("5"));
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"shop-location-missing")).thenReturn(Optional.empty());when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"DELIVERY","Kenyatta Avenue","0712345678",null,null,-1.2900,36.8200);
        assertThrows(PMSCustomException.class,()->service.checkout(request,"shop-location-missing"));verifyNoInteractions(products,invoices,accounts);
    }

    @Test void repeatedCheckoutKeyReturnsOriginalOrderWithoutReservingStockAgain(){
        SokoStore store=new SokoStore();store.setId(2L);store.setName("Fresh Corner");SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setCheckoutIdempotencyKey("checkout-1");order.setDeliveryMethod("PICKUP");order.setCustomerPhone("0712345678");order.setActive(true);SokoOrderItem item=new SokoOrderItem();item.setOrderId(9L);item.setProductId(5L);item.setQuantity(1);item.setActive(true);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"checkout-1")).thenReturn(Optional.of(order));when(stores.findById(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of(item));
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"PICKUP",null,"0712345678",null,null);
        assertEquals(9L,service.checkout(request,"checkout-1").order().id());verifyNoInteractions(products);
    }

    @Test void repeatedCheckoutKeyRejectsChangedSellerBeforeMutation(){
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setCheckoutIdempotencyKey("checkout-1");order.setDeliveryMethod("PICKUP");order.setCustomerPhone("0712345678");order.setActive(true);SokoOrderItem item=new SokoOrderItem();item.setOrderId(9L);item.setProductId(5L);item.setQuantity(1);item.setActive(true);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"checkout-1")).thenReturn(Optional.of(order));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of(item));
        var changedSeller=new SokoRequests.Checkout(3L,List.of(new SokoRequests.CheckoutItem(5L,1)),"PICKUP",null,"0712345678",null,null);
        assertThrows(PMSCustomException.class,()->service.checkout(changedSeller,"checkout-1"));verifyNoInteractions(products,invoices);verify(stores,never()).findById(anyLong());
    }

    @Test void repeatedCheckoutKeyRejectsChangedProductVariationOrQuantityBeforeMutation(){
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setCheckoutIdempotencyKey("checkout-1");order.setDeliveryMethod("DELIVERY");order.setDeliveryAddress("Market Road");order.setCustomerPhone("0712345678");order.setDestinationUnitId(12L);order.setActive(true);SokoOrderItem item=new SokoOrderItem();item.setOrderId(9L);item.setProductId(5L);item.setVariationId(11L);item.setQuantity(2);item.setActive(true);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"checkout-1")).thenReturn(Optional.of(order));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of(item));
        var changedItems=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,3,12L)),"DELIVERY","Market Road","0712345678",null,12L);
        assertThrows(PMSCustomException.class,()->service.checkout(changedItems,"checkout-1"));verifyNoInteractions(products,invoices);verify(stores,never()).findById(anyLong());
    }

    @Test void repeatedCheckoutKeyRejectsChangedDeliveryNotes(){
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setCheckoutIdempotencyKey("checkout-1");order.setDeliveryMethod("PICKUP");order.setCustomerPhone("0712345678");order.setNotes("Call on arrival");order.setActive(true);SokoOrderItem item=new SokoOrderItem();item.setOrderId(9L);item.setProductId(5L);item.setQuantity(1);item.setActive(true);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"checkout-1")).thenReturn(Optional.of(order));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of(item));
        var changedNotes=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"PICKUP",null,"0712345678","Leave with security",null);
        assertThrows(PMSCustomException.class,()->service.checkout(changedNotes,"checkout-1"));verifyNoInteractions(products,invoices);verify(stores,never()).findById(anyLong());
    }

    @Test void repeatedCheckoutKeyRejectsChangedDeliveryPin(){
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setCheckoutIdempotencyKey("checkout-1");order.setDeliveryMethod("DELIVERY");order.setDeliveryAddress("Market Road");order.setDeliveryLatitude(-1.2800);order.setDeliveryLongitude(36.8200);order.setCustomerPhone("0712345678");order.setActive(true);SokoOrderItem item=new SokoOrderItem();item.setOrderId(9L);item.setProductId(5L);item.setQuantity(1);item.setActive(true);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"checkout-1")).thenReturn(Optional.of(order));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of(item));
        var changedPin=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"DELIVERY","Market Road","0712345678",null,null,-1.2900,36.8300);
        assertThrows(PMSCustomException.class,()->service.checkout(changedPin,"checkout-1"));verifyNoInteractions(products,invoices);verify(stores,never()).findById(anyLong());
    }

    @Test void deliveryProofUsesServerGeneratedKeyAndRejectsSpoofing() throws Exception {
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setStatus("DISPATCHED");order.setRiderId(3L);order.setActive(true);SokoRider rider=existingRider();rider.setUserId(null);
        when(users.getUserId()).thenReturn(7L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(rider));
        byte[] png={(byte)0x89,'P','N','G',13,10,26,10,0};assertThrows(PMSCustomException.class,()->service.uploadDeliveryProof(9L,new MockMultipartFile("proof","fake.png","image/png","bad".getBytes())));service.uploadDeliveryProof(9L,new MockMultipartFile("proof","../../proof.png","image/png",png));service.uploadDeliveryProof(9L,new MockMultipartFile("proof","lost-response-retry.png","image/png",png));verify(garage,times(1)).uploadBytes(matches("soko/delivery-proof/9/[0-9a-f-]+\\.png"),eq(png),eq("image/png"));
    }

    @Test void merchantCannotUploadProofOrConfirmForLinkedRider(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setStatus("DISPATCHED");order.setDeliveryMethod("DELIVERY");order.setDeliveryCode("123456");order.setDeliveryProofReference("proof");order.setRiderId(3L);order.setActive(true);SokoRider rider=existingRider();rider.setUserId(8L);
        when(users.getUserId()).thenReturn(7L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(rider));
        byte[] png={(byte)0x89,'P','N','G',13,10,26,10,0};assertThrows(PMSCustomException.class,()->service.uploadDeliveryProof(9L,new MockMultipartFile("proof","proof.png","image/png",png)));assertThrows(PMSCustomException.class,()->service.confirmDelivery(9L,new SokoRequests.DeliveryConfirmation("123456")));
        verifyNoInteractions(garage,malwarePolicy,encryption);verify(orders,never()).save(any());
    }

    @Test void createStoreCreatesDraftOwnedByMerchant(){
        when(users.hasRole(PMSRole.SERVICE_PROVIDER)).thenReturn(true);when(users.getUserId()).thenReturn(7L);when(stores.existsByOwnerUserIdAndActiveTrue(7L)).thenReturn(false);when(stores.save(any())).thenAnswer(i->i.getArgument(0));
        var request=new SokoRequests.StoreUpsert("Fresh Corner",null,"0712345678","Nairobi",-1.28,36.82,BigDecimal.valueOf(20),true,true,BigDecimal.valueOf(150),"kes",3L);
        SokoStore result=service.createStore(request);
        assertEquals("DRAFT",result.getStatus());assertEquals(7L,result.getOwnerUserId());assertEquals("KES",result.getCurrency());assertTrue(result.isDeliveryEnabled());
    }

    @Test void createStoreSerializesTheOneActiveShopCheck() throws Exception {
        var transaction=SokoService.class.getMethod("createStore",SokoRequests.StoreUpsert.class).getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertNotNull(transaction);assertEquals(org.springframework.transaction.annotation.Isolation.SERIALIZABLE,transaction.isolation());
    }

    @Test void createStoreRejectsSecondActiveShopForMerchant(){
        when(users.hasRole(PMSRole.SERVICE_PROVIDER)).thenReturn(true);when(users.getUserId()).thenReturn(7L);when(stores.existsByOwnerUserIdAndActiveTrue(7L)).thenReturn(true);
        var request=new SokoRequests.StoreUpsert("Second Shop","Fresh produce","0712345678","Nairobi",-1.28,36.82,BigDecimal.valueOf(20),true,true,BigDecimal.valueOf(150),"KES",3L);
        assertThrows(PMSCustomException.class,()->service.createStore(request));verify(stores,never()).save(any());
    }

    @Test void createStoreRejectsUserWithoutMerchantRole(){
        when(users.hasRole(PMSRole.SERVICE_PROVIDER)).thenReturn(false);when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(false);
        var request=new SokoRequests.StoreUpsert("Fresh Corner",null,null,null,null,null,BigDecimal.TEN,true,false,BigDecimal.ZERO,"KES",null);
        assertThrows(PMSCustomException.class,()->service.createStore(request));verifyNoInteractions(stores);
    }

    @Test void superadminCanRejectPendingShopWithAuditableReason(){
        SokoStore store=new SokoStore();store.setId(2L);store.setActive(true);store.setStatus("PENDING_REVIEW");when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);when(users.getUserId()).thenReturn(1L);when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(stores.save(store)).thenReturn(store);
        SokoStore result=service.moderateStore(2L,new SokoRequests.ModerationDecision("REJECT","Payment identity does not match the shop."));
        assertEquals("REJECTED",result.getStatus());assertEquals(1L,result.getReviewedByUserId());assertNotNull(result.getReviewedAt());assertEquals("Payment identity does not match the shop.",result.getReviewReason());
    }

    @Test void deliveryOrPickupShopCannotBeSubmittedWithoutAUsableCustomerLocation(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setStatus("DRAFT");store.setActive(true);store.setPaymentAccountId(3L);store.setServiceRadiusKm(BigDecimal.TEN);store.setDeliveryEnabled(true);
        when(users.getUserId()).thenReturn(7L);when(stores.findByIdAndOwnerUserIdAndActiveTrue(2L,7L)).thenReturn(Optional.of(store));
        assertThrows(PMSCustomException.class,()->service.publishStore(2L));
        store.setDeliveryEnabled(false);store.setPickupEnabled(true);
        assertThrows(PMSCustomException.class,()->service.publishStore(2L));
        verifyNoInteractions(accounts,marketplaceKycGate);verify(stores,never()).save(any());
    }

    @Test void merchantCannotUsePlatformModerationApi(){
        when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(false);
        assertThrows(PMSCustomException.class,()->service.moderateStore(2L,new SokoRequests.ModerationDecision("APPROVE",null)));verify(stores,never()).findByIdAndActiveTrue(anyLong());
    }

    @Test void checkoutRejectsInsufficientStockWithoutCreatingOrder(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);store.setStatus("PUBLISHED");store.setPickupEnabled(true);store.setAddress("Market Road");store.setLatitude(-1.28);store.setLongitude(36.82);store.setCurrency("KES");
        SokoProduct product=new SokoProduct();product.setId(5L);product.setStoreId(2L);product.setStatus("PUBLISHED");product.setCurrency("KES");product.setStockQuantity(1);product.setName("Milk");
        store.setPaymentAccountId(3L);
        var account=new org.pms.silverocean.database.pms.entities.PaymentAccount();
        account.setActive(true);account.setVerified(true);account.setCategory(org.pms.silverocean.service.account.enums.AccountCategory.MERCHANT);
        account.setChannel(org.pms.silverocean.service.payment.wrappers.PaymentChannel.MPESA);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"stock-check")).thenReturn(Optional.empty());when(accounts.getAccountByIdAndCreatedBy(3L,store.getOwnerUserId())).thenReturn(account);
        when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(products.findByIdForUpdate(5L)).thenReturn(Optional.of(product));
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,2)),"PICKUP",null,"0712345678",null,null);
        assertThrows(PMSCustomException.class,()->service.checkout(request,"stock-check"));verify(orders,never()).save(any());
    }

    @Test void checkoutRejectsBlankIdempotencyKeyBeforeReadingOrReservingCart(){
        when(users.getUserId()).thenReturn(4L);
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"PICKUP",null,"0712345678",null,null);
        assertThrows(PMSCustomException.class,()->service.checkout(request,"   "));
        verifyNoInteractions(stores,products,variations,orders,items,invoices,accounts);
    }

    @Test void paidInvoiceMovesOrderToPaidIdempotently(){
        SokoOrder order=new SokoOrder();order.setInvoiceRef("INV-9");order.setTotal(new BigDecimal("100"));order.setPaymentStatus("UNPAID");order.setStatus("PENDING_PAYMENT");order.setActive(true);
        when(orders.findByInvoiceRefAndActiveTrue("INV-9")).thenReturn(Optional.of(order));
        service.completePaidInvoice("INV-9","PS-1");
        assertEquals("PAID",order.getPaymentStatus());assertEquals("PAID",order.getStatus());verify(orders).save(order);
        service.completePaidInvoice("INV-9","PS-1");verify(orders,times(1)).save(order);
    }

    @Test void deliveryCannotBypassProofByBecomingPickup(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);
        order.setStatus("PACKED");order.setDeliveryMethod("DELIVERY");
        when(users.getUserId()).thenReturn(7L);
        when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));
        when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));
        assertThrows(PMSCustomException.class,()->service.transition(9L,"READY_FOR_PICKUP",null));
        order.setStatus("READY_FOR_PICKUP");
        assertThrows(PMSCustomException.class,()->service.transition(9L,"COMPLETED",null));
        verify(orders,never()).save(any());
    }
    @Test void pickupCannotEnterDeliveryDispatch(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);
        order.setStatus("PACKED");order.setDeliveryMethod("PICKUP");
        when(users.getUserId()).thenReturn(7L);
        when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));
        when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));
        assertThrows(PMSCustomException.class,()->service.transition(9L,"DISPATCHED",null));
        verify(orders,never()).save(any());
    }
    @Test void pickupRequiresBuyerCodeAndMerchantConfirmation(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setName("Fresh Corner");store.setActive(true);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setOrderNumber("SOKO-9");order.setStatus("PACKED");order.setDeliveryMethod("PICKUP");order.setActive(true);
        when(users.getUserId()).thenReturn(7L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(stores.findById(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());when(encryption.encrypt(anyString())).thenReturn(new byte[]{1,2,3});
        var ready=service.transition(9L,"READY_FOR_PICKUP",null);
        assertEquals("READY_FOR_PICKUP",ready.order().status());assertArrayEquals(new byte[]{1,2,3},order.getEncryptedDeliveryCode());assertNotNull(order.getDeliveryCodeExpiresAt());assertFalse(order.isDeliveryCodeVerified());verifyNoInteractions(notifications);

        when(users.getUserId()).thenReturn(4L);when(orders.findById(9L)).thenReturn(Optional.of(order));when(encryption.decrypt(order.getEncryptedDeliveryCode())).thenReturn(new DecryptDTO(false,"123456"));
        assertEquals("123456",service.pickupCode(9L));
        when(users.getUserId()).thenReturn(99L);assertThrows(PMSCustomException.class,()->service.pickupCode(9L));

        when(users.getUserId()).thenReturn(7L);when(stores.findByIdAndOwnerUserIdAndActiveTrue(2L,7L)).thenReturn(Optional.of(store));
        var completed=service.confirmPickup(9L,new SokoRequests.PickupConfirmation("123456","Jane Buyer"));
        var retried=service.confirmPickup(9L,new SokoRequests.PickupConfirmation("123456","Jane Buyer"));
        assertEquals("COMPLETED",completed.order().status());assertTrue(order.isDeliveryCodeVerified());assertEquals("Jane Buyer",order.getDeliveryRecipientName());assertNull(order.getEncryptedDeliveryCode());assertNotNull(order.getCompletedAt());
        assertEquals("COMPLETED",retried.order().status());
    }

    @Test void pickupCannotBeCompletedByTheBuyerOrWithTheWrongCode(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setName("Fresh Corner");store.setActive(true);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setStatus("READY_FOR_PICKUP");order.setDeliveryMethod("PICKUP");order.setDeliveryCode("123456");order.setDeliveryCodeExpiresAt(ZonedDateTime.now().plusHours(1));order.setActive(true);
        when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(users.getUserId()).thenReturn(4L);when(stores.findByIdAndOwnerUserIdAndActiveTrue(2L,4L)).thenReturn(Optional.empty());
        assertThrows(PMSCustomException.class,()->service.confirmPickup(9L,new SokoRequests.PickupConfirmation("123456")));assertEquals("READY_FOR_PICKUP",order.getStatus());assertEquals(0,order.getDeliveryCodeAttempts());

        when(users.getUserId()).thenReturn(7L);when(stores.findByIdAndOwnerUserIdAndActiveTrue(2L,7L)).thenReturn(Optional.of(store));
        assertThrows(PMSCustomException.class,()->service.confirmPickup(9L,new SokoRequests.PickupConfirmation("000000")));assertEquals("READY_FOR_PICKUP",order.getStatus());assertEquals(1,order.getDeliveryCodeAttempts());verify(orders).save(order);
    }

    @Test void expiredOrLockedPickupCodeIsHiddenButBuyerCanRecoverItWithoutChangingOrderState(){
        SokoOrder order=new SokoOrder();order.setId(9L);order.setOrderNumber("SOKO-9");order.setStoreId(2L);order.setCustomerUserId(4L);order.setStatus("READY_FOR_PICKUP");order.setDeliveryMethod("PICKUP");order.setEncryptedDeliveryCode(new byte[]{1});order.setDeliveryCodeExpiresAt(ZonedDateTime.now().minusMinutes(1));order.setDeliveryCodeLockedAt(ZonedDateTime.now());order.setActive(true);
        SokoStore store=new SokoStore();store.setId(2L);store.setName("Fresh Corner");store.setActive(true);
        var buyer=new org.pms.silverocean.database.pms.entities.Users();buyer.setId(4L);buyer.setEmail("buyer@example.test");buyer.setEmailVerified(true);buyer.setActive(true);
        when(users.getUserId()).thenReturn(4L);when(orders.findById(9L)).thenReturn(Optional.of(order));when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(users.findById(4L)).thenReturn(Optional.of(buyer));when(stores.findById(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());when(orders.save(any())).thenAnswer(i->i.getArgument(0));when(encryption.encrypt(anyString())).thenReturn(new byte[]{2});when(i18n.getLocalizedMessage(NotificationType.SOKO_DELIVERY_RECOVERY_EMAIL.getBody())).thenReturn("Order %s code %s expires %s");
        assertThrows(PMSCustomException.class,()->service.pickupCode(9L));
        service.requestDeliveryCodeRecovery(9L,null);
        assertNull(order.getEncryptedDeliveryCode());assertNull(order.getDeliveryCodeLockedAt());assertArrayEquals(new byte[]{2},order.getDeliveryRecoveryOtp());assertEquals("READY_FOR_PICKUP",order.getStatus());verify(notifications).queueNotification(any());

        when(encryption.decrypt(order.getDeliveryRecoveryOtp())).thenReturn(new DecryptDTO(false,"654321"));when(encryption.encrypt(anyString())).thenReturn(new byte[]{3});
        service.confirmDeliveryCodeRecovery(9L,new SokoRequests.DeliveryCodeRecoveryConfirm("654321"));
        assertEquals("READY_FOR_PICKUP",order.getStatus());assertArrayEquals(new byte[]{3},order.getEncryptedDeliveryCode());assertNull(order.getDeliveryRecoveryOtp());assertNotNull(order.getDeliveryCodeExpiresAt());assertFalse(order.isDeliveryCodeVerified());
    }

    @Test void anotherUserCannotRecoverBuyersPickupCode(){
        SokoOrder order=new SokoOrder();order.setId(9L);order.setCustomerUserId(4L);order.setStatus("READY_FOR_PICKUP");order.setDeliveryMethod("PICKUP");order.setActive(true);
        when(users.getUserId()).thenReturn(99L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));
        assertThrows(PMSCustomException.class,()->service.requestDeliveryCodeRecovery(9L,null));verify(orders,never()).save(any());verifyNoInteractions(notifications);
    }
    @Test void checkoutRechecksInactivePayeeBeforeReservingStock(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);
        store.setStatus("PUBLISHED");store.setPickupEnabled(true);store.setAddress("Market Road");store.setLatitude(-1.28);store.setLongitude(36.82);store.setPaymentAccountId(3L);
        var account=new org.pms.silverocean.database.pms.entities.PaymentAccount();
        account.setVerified(true);account.setActive(false);
        when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));
        when(accounts.getAccountByIdAndCreatedBy(3L,7L)).thenReturn(account);
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"PICKUP",null,"0712345678",null,null);
        assertThrows(PMSCustomException.class,()->service.checkout(request,"inactive-payee"));
        verifyNoInteractions(products,invoices);
    }
    @Test void wrongCodeRetainsProofAndLimitsAttempts(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setRiderId(3L);
        order.setStatus("DISPATCHED");order.setDeliveryMethod("DELIVERY");
        order.setDeliveryCode("123456");order.setDeliveryCodeExpiresAt(ZonedDateTime.now().plusHours(1));order.setDeliveryProofReference("protected-proof");
        SokoRider rider=existingRider();rider.setUserId(null);
        when(users.getUserId()).thenReturn(7L);
        when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));
        when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));
        when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(rider));
        for(int i=0;i<5;i++)assertThrows(PMSCustomException.class,()->service.confirmDelivery(9L,new SokoRequests.DeliveryConfirmation("000000")));
        assertEquals(5,order.getDeliveryCodeAttempts());
        assertEquals("protected-proof",order.getDeliveryProofReference());
        assertThrows(PMSCustomException.class,()->service.confirmDelivery(9L,new SokoRequests.DeliveryConfirmation("123456")));
        assertEquals("DISPATCHED",order.getStatus());
    }
}
