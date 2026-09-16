package org.pms.silverocean.service.soko;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.SokoOrderItemRepo;
import org.pms.silverocean.database.pms.SokoOrderRepo;
import org.pms.silverocean.database.pms.SokoProductRepo;
import org.pms.silverocean.database.pms.SokoProductImageRepo;
import org.pms.silverocean.database.pms.SokoProductVariationRepo;
import org.pms.silverocean.database.pms.SokoRiderRepo;
import org.pms.silverocean.database.pms.SokoStoreRepo;
import org.pms.silverocean.database.pms.entities.SokoOrder;
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
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.kyc.MarketplaceKycGate;
import org.pms.silverocean.service.visitor.VisitorService;
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
        var request=new SokoRequests.FinanceUpdate(SokoRequests.FinanceType.REFUND,SokoRequests.FinanceStatus.CONFIRMED,new BigDecimal("100"),"RF-TEST-1");
        service.finance(9L,request);
        assertEquals("REFUNDED",order.getPaymentStatus());assertEquals("REFUNDED",order.getStatus());
        assertNull(order.getEncryptedDeliveryCode());assertNull(order.getDeliveryCode());assertNull(order.getDeliveryRecoveryOtp());
        assertNull(order.getDeliveryRecoveryOtpExpiresAt());assertNull(order.getDeliveryCodeExpiresAt());
        service.finance(9L,request);
        verify(orders,times(1)).save(order);
        verifyNoInteractions(products,variations,riders,invoices);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"REFUND","REVERSAL","CHARGEBACK"})
    void confirmedFinanceOperationInvalidatesCodesButDoesNotAutomaticallyResellOrReassign(String type) {
        SokoOrder order=refundableOrder();
        when(orders.findByInvoiceRefAndActiveTrue("INV-TEST")).thenReturn(Optional.of(order));
        service.completeFinanceOperation("INV-TEST",type,new BigDecimal("100"),"RF-TEST-2");
        assertNull(order.getEncryptedDeliveryCode());assertNull(order.getDeliveryCode());assertNull(order.getDeliveryRecoveryOtp());
        assertNull(order.getDeliveryCodeExpiresAt());assertNull(order.getDeliveryRecoveryOtpExpiresAt());
        assertEquals("REFUND".equals(type)?"REFUNDED":"PAYMENT_REVERSED",order.getStatus());
        assertEquals(5L,order.getRiderId());assertFalse(order.isStockReleased());
        verifyNoInteractions(products,variations,invoices);
        // Existing status notifications may look up the assigned rider; no
        // rider availability/custody mutation is allowed by financial finality.
        verify(riders,never()).save(any());
    }

    private SokoOrder refundableOrder() {
        SokoOrder o=new SokoOrder();o.setId(9L);o.setStoreId(2L);o.setCustomerUserId(8L);o.setOrderNumber("SOKO-TEST");
        o.setStatus("DISPATCHED");o.setPaymentStatus("PAID");o.setRefundStatus("REQUESTED");o.setSettlementStatus("PENDING");o.setTotal(new BigDecimal("100"));o.setCurrency("KES");
        o.setDeliveryMethod("DELIVERY");o.setDeliveryCode("123456");o.setEncryptedDeliveryCode(new byte[]{1});o.setDeliveryRecoveryOtp(new byte[]{2});
        o.setDeliveryCodeExpiresAt(ZonedDateTime.now().plusHours(1));o.setDeliveryRecoveryOtpExpiresAt(ZonedDateTime.now().plusMinutes(10));o.setRiderId(5L);
        return o;
    }
    @Mock SokoStoreRepo stores; @Mock SokoProductRepo products; @Mock SokoOrderRepo orders;
    @Mock SokoOrderItemRepo items; @Mock InvoiceDao invoices; @Mock AccountDao accounts;
    @Mock SokoProductVariationRepo variations; @Mock SokoRiderRepo riders; @Mock UserDao users; @Mock VisitorService visitors;
    @Mock SokoProductImageRepo productImages; @Mock GarageService garage; @Mock UploadMalwarePolicy malwarePolicy; @Mock EncryptionService encryption; @Mock NotificationService notifications; @Mock I18NService i18n; @Mock MarketplaceKycGate marketplaceKycGate;
    @Mock org.pms.silverocean.service.notification.BusinessNotificationService businessAlerts;
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
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(8L);order.setOrderNumber("SOKO-TEST");order.setStatus("PENDING_PAYMENT");order.setPaymentStatus("UNPAID");
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);
        when(orders.findByInvoiceRefAndActiveTrue("INV-TEST")).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));
        service.completePaidInvoice("INV-TEST","provider-reference");service.completePaidInvoice("INV-TEST","provider-reference");
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

    private SokoRider existingRider(){SokoRider r=new SokoRider();r.setId(3L);r.setStoreId(2L);r.setActive(true);r.setRiderType("INDIVIDUAL");r.setDisplayName("Jane Rider");r.setPhoneNumber("0712345678");r.setEmail("jane@example.test");r.setVehicleType("Motorbike");r.setVehiclePlate("KDA 123A");r.setVerified(true);r.setStatus("ACTIVE");r.setAvailability("AVAILABLE");return r;}
    private void merchant(){SokoStore s=new SokoStore();s.setId(2L);s.setOwnerUserId(7L);when(users.getUserId()).thenReturn(7L);when(stores.findByIdAndOwnerUserIdAndActiveTrue(2L,7L)).thenReturn(Optional.of(s));}
    private SokoRequests.RiderUpsert riderUpdate(String name){return new SokoRequests.RiderUpsert(2L,"INDIVIDUAL",name,"0712345678","jane@example.test","Motorbike","KDA 123A","Updated notes");}
    @Test void busyRiderCannotBeEdited(){merchant();SokoRider r=existingRider();r.setAvailability("BUSY");when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(r));assertThrows(PMSCustomException.class,()->service.updateRider(3L,riderUpdate("Changed Name")));assertEquals("BUSY",r.getAvailability());verify(riders,never()).save(any());}
    @Test void activeAssignmentBlocksEditEvenIfLegacyAvailabilityIsWrong(){merchant();SokoRider r=existingRider();when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(r));when(orders.existsByRiderIdAndStatusInAndActiveTrue(eq(3L),anyList())).thenReturn(true);assertThrows(PMSCustomException.class,()->service.updateRider(3L,riderUpdate("Changed Name")));verify(riders,never()).save(any());}
    @Test void notesOnlyEditPreservesVerification(){merchant();SokoRider r=existingRider();when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(r));when(riders.save(any())).thenAnswer(i->i.getArgument(0));service.updateRider(3L,riderUpdate("Jane Rider"));assertTrue(r.isVerified());assertEquals("ACTIVE",r.getStatus());assertEquals("AVAILABLE",r.getAvailability());}
    @Test void identityEditRequiresVerificationAgain(){merchant();SokoRider r=existingRider();when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(r));when(riders.save(any())).thenAnswer(i->i.getArgument(0));service.updateRider(3L,riderUpdate("Changed Name"));assertFalse(r.isVerified());assertEquals("PENDING_VERIFICATION",r.getStatus());assertEquals("OFFLINE",r.getAvailability());}
    @Test void adminCannotResetBusyRiderThroughVerifyOrReject(){when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);SokoRider r=existingRider();r.setAvailability("BUSY");when(riders.findByIdForUpdate(3L)).thenReturn(Optional.of(r));assertThrows(PMSCustomException.class,()->service.riderDecision(3L,new SokoRequests.RiderDecision("VERIFY",null)));assertThrows(PMSCustomException.class,()->service.riderDecision(3L,new SokoRequests.RiderDecision("REJECT","Reason")));assertEquals("BUSY",r.getAvailability());verify(riders,never()).save(any());}
    @Test void verificationLinksAnAccountCreatedAfterMerchantAddedRider(){when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);SokoRider r=existingRider();r.setUserId(null);r.setVerified(false);when(riders.findByIdForUpdate(3L)).thenReturn(Optional.of(r));var u=new org.pms.silverocean.database.pms.entities.Users();u.setId(8L);u.setActive(true);u.setVerified(true);u.setEmailVerified(true);u.setAccountStatus("ACTIVE");when(users.findByEmail("jane@example.test")).thenReturn(Optional.of(u));when(users.findById(8L)).thenReturn(Optional.of(u));when(riders.save(any())).thenAnswer(i->i.getArgument(0));service.riderDecision(3L,new SokoRequests.RiderDecision("VERIFY",null));assertEquals(8L,r.getUserId());assertTrue(r.isVerified());verify(marketplaceKycGate).require(eq(8L),eq("PROVIDER_TYPE"),eq("DELIVERY_RIDER"),any());}

    @BeforeEach void setup(){service=new SokoService(stores,products,productImages,variations,orders,items,riders,invoices,accounts,users,visitors,garage,malwarePolicy,encryption,notifications,businessAlerts,i18n,marketplaceKycGate);}

    @Test void oneOffCourierDispatchIsRejectedBeforeAssignmentOrCodeGeneration(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setStatus("PACKED");order.setDeliveryMethod("DELIVERY");
        when(users.getUserId()).thenReturn(7L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));
        assertThrows(PMSCustomException.class,()->service.transition(9L,"DISPATCHED",new SokoRequests.Dispatch(null,"Unverified courier","0712345678",null,java.time.LocalDateTime.now().plusHours(1))));
        assertEquals("PACKED",order.getStatus());assertNull(order.getDeliveryCode());verifyNoInteractions(encryption,riders,visitors);verify(orders,never()).save(any());
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
        SokoOrder order=new SokoOrder();order.setId(41L);order.setCustomerUserId(7L);order.setStoreId(2L);order.setPaymentAccountId(33L);order.setPaymentChannel("MPESA");
        SokoStore store=new SokoStore();store.setId(2L);store.setPaymentAccountId(44L);
        when(users.getUserId()).thenReturn(7L);when(orders.findAllByCustomerUserIdAndActiveTrue(eq(7L),any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(order)));when(stores.findAllById(any())).thenReturn(List.of(store));
        var result=service.myOrders(org.springframework.data.domain.PageRequest.of(0,10)).getContent().getFirst();assertEquals(33L,result.paymentAccountId());assertEquals("MPESA",result.paymentChannel());verify(accounts,never()).getAccountById(44L);
    }

    @Test void createRiderRequiresVerificationBeforeAssignments(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);when(users.getUserId()).thenReturn(7L);when(stores.findByIdAndOwnerUserIdAndActiveTrue(2L,7L)).thenReturn(Optional.of(store));when(riders.save(any())).thenAnswer(i->i.getArgument(0));
        SokoRider rider=service.createRider(new SokoRequests.RiderUpsert(2L,"individual","Jane Rider","0712345678",null,"Motorbike","KDA 123A",null));
        assertEquals("OFFLINE",rider.getAvailability());assertEquals("PENDING_VERIFICATION",rider.getStatus());assertFalse(rider.isVerified());assertEquals("INDIVIDUAL",rider.getRiderType());
    }

    @Test void productImageUploadUsesServerGeneratedStorageKey() throws Exception {
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);
        SokoProduct product=new SokoProduct();product.setId(5L);product.setStoreId(2L);product.setActive(true);
        when(users.getUserId()).thenReturn(7L);when(products.findById(5L)).thenReturn(Optional.of(product));
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
        when(users.getUserId()).thenReturn(7L);when(products.findById(5L)).thenReturn(Optional.of(product));
        when(stores.findByIdAndOwnerUserIdAndActiveTrue(2L,7L)).thenReturn(Optional.of(store));
        var file=new MockMultipartFile("images","fake.jpg","image/jpeg","not-an-image".getBytes());
        assertThrows(PMSCustomException.class,()->service.replaceProductImages(5L,List.of(file)));
        verifyNoInteractions(garage);
    }

    @Test void dispatchAssignsOnlyVerifiedRiderAndWaitsForCollectionBeforeIssuingCode(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setStatus("PACKED");order.setDeliveryMethod("DELIVERY");order.setActive(true);SokoRider rider=new SokoRider();rider.setId(3L);rider.setStoreId(2L);rider.setUserId(8L);rider.setVerified(true);rider.setStatus("ACTIVE");rider.setAvailability("AVAILABLE");rider.setDisplayName("Jane Rider");rider.setPhoneNumber("0712345678");rider.setVehiclePlate("KDA 123A");rider.setActive(true);
        when(users.getUserId()).thenReturn(7L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(rider));when(stores.findById(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());
        var result=service.transition(9L,"DISPATCHED",new SokoRequests.Dispatch(3L,null,null,null,
                java.time.LocalDateTime.now(java.time.ZoneId.of("Africa/Nairobi")).plusHours(1)));
        assertEquals("DELIVERY_ASSIGNED",result.order().getStatus());assertEquals("BUSY",rider.getAvailability());assertEquals(3L,result.order().getRiderId());assertEquals("Jane Rider",result.order().getCourierName());assertNull(order.getEncryptedDeliveryCode());assertNull(order.getDeliveryCodeExpiresAt());
    }

    @Test void deliveryCodeCompletesOrderAndReleasesPreferredRider(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setStatus("DISPATCHED");order.setDeliveryMethod("DELIVERY");order.setDeliveryCode("123456");order.setDeliveryCodeExpiresAt(ZonedDateTime.now().plusHours(1));order.setDeliveryProofReference("soko/delivery-proof/9/proof.jpg");order.setRiderId(3L);order.setActive(true);SokoRider rider=new SokoRider();rider.setId(3L);rider.setStoreId(2L);rider.setUserId(8L);rider.setStatus("ACTIVE");rider.setAvailability("BUSY");rider.setActive(true);
        rider.setVerified(true);
        when(users.getUserId()).thenReturn(8L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(rider));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());
        var result=service.confirmDelivery(9L,new SokoRequests.DeliveryConfirmation("123456"));
        assertEquals("COMPLETED",result.order().getStatus());assertTrue(result.order().isDeliveryCodeVerified());assertEquals("AVAILABLE",rider.getAvailability());assertEquals(1,rider.getCompletedDeliveries());
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

    @Test void checkoutLocksSelectedVariationAndUsesItsPriceAndStock(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setName("Fresh Corner");store.setStatus("PUBLISHED");store.setActive(true);store.setPickupEnabled(true);store.setCurrency("KES");store.setPaymentAccountId(3L);
        var account=new org.pms.silverocean.database.pms.entities.PaymentAccount();account.setActive(true);account.setVerified(true);account.setCategory(org.pms.silverocean.service.account.enums.AccountCategory.MERCHANT);account.setChannel(org.pms.silverocean.service.payment.wrappers.PaymentChannel.MPESA);
        SokoProduct product=new SokoProduct();product.setId(5L);product.setStoreId(2L);product.setName("Shirt");product.setUnit("item");product.setPrice(new BigDecimal("1000"));product.setStockQuantity(3);product.setStatus("PUBLISHED");product.setActive(true);
        SokoProductVariation variation=new SokoProductVariation();variation.setId(11L);variation.setProductId(5L);variation.setName("Size");variation.setValue("Large");variation.setPriceAdjustment(new BigDecimal("100"));variation.setStockQuantity(2);variation.setActive(true);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"variant-1")).thenReturn(Optional.empty());when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(accounts.getAccountByIdAndCreatedBy(3L,7L)).thenReturn(account);when(accounts.getAccountById(3L)).thenReturn(account);when(products.findByIdForUpdate(5L)).thenReturn(Optional.of(product));when(variations.findAllByProductIdAndActiveTrueOrderByNameAscValueAsc(5L)).thenReturn(List.of(variation));when(variations.findForUpdate(11L,5L)).thenReturn(Optional.of(variation));when(orders.save(any())).thenAnswer(invocation->{SokoOrder saved=invocation.getArgument(0);if(saved.getId()==null)saved.setId(9L);return saved;});when(items.save(any())).thenAnswer(invocation->invocation.getArgument(0));doAnswer(invocation->{org.pms.silverocean.database.pms.entities.PMSInvoice invoice=invocation.getArgument(0);invoice.setRef("INV-9");return null;}).when(invoices).createInvoice(any());
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,2,11L)),"PICKUP",null,"0712345678",null,null);
        var result=service.checkout(request,"variant-1");
        assertEquals(new BigDecimal("2200"),result.order().getTotal());assertEquals(1,product.getStockQuantity());assertEquals(0,variation.getStockQuantity());assertEquals("Large",result.items().getFirst().getVariationValue());assertEquals(new BigDecimal("1100"),result.items().getFirst().getUnitPrice());
    }

    @Test void repeatedCheckoutKeyReturnsOriginalOrderWithoutReservingStockAgain(){
        SokoStore store=new SokoStore();store.setId(2L);store.setName("Fresh Corner");SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setCheckoutIdempotencyKey("checkout-1");order.setActive(true);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"checkout-1")).thenReturn(Optional.of(order));when(stores.findById(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"PICKUP",null,"0712345678",null,null);
        assertEquals(9L,service.checkout(request,"checkout-1").order().getId());verifyNoInteractions(products);
    }

    @Test void deliveryProofUsesServerGeneratedKeyAndRejectsSpoofing() throws Exception {
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setStatus("DISPATCHED");order.setActive(true);
        when(users.getUserId()).thenReturn(7L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(stores.findById(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());
        byte[] png={(byte)0x89,'P','N','G',13,10,26,10,0};service.uploadDeliveryProof(9L,new MockMultipartFile("proof","../../proof.png","image/png",png));verify(garage).uploadBytes(matches("soko/delivery-proof/9/[0-9a-f-]+\\.png"),eq(png),eq("image/png"));assertThrows(PMSCustomException.class,()->service.uploadDeliveryProof(9L,new MockMultipartFile("proof","fake.png","image/png","bad".getBytes())));
    }

    @Test void createStoreCreatesDraftOwnedByMerchant(){
        when(users.hasRole(PMSRole.SERVICE_PROVIDER)).thenReturn(true);when(users.getUserId()).thenReturn(7L);when(stores.save(any())).thenAnswer(i->i.getArgument(0));
        var request=new SokoRequests.StoreUpsert("Fresh Corner",null,"0712345678","Nairobi",-1.28,36.82,BigDecimal.valueOf(20),true,true,BigDecimal.valueOf(150),"kes",3L);
        SokoStore result=service.createStore(request);
        assertEquals("DRAFT",result.getStatus());assertEquals(7L,result.getOwnerUserId());assertEquals("KES",result.getCurrency());assertTrue(result.isDeliveryEnabled());
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

    @Test void merchantCannotUsePlatformModerationApi(){
        when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(false);
        assertThrows(PMSCustomException.class,()->service.moderateStore(2L,new SokoRequests.ModerationDecision("APPROVE",null)));verify(stores,never()).findByIdAndActiveTrue(anyLong());
    }

    @Test void checkoutRejectsInsufficientStockWithoutCreatingOrder(){
        SokoStore store=new SokoStore();store.setId(2L);store.setActive(true);store.setStatus("PUBLISHED");store.setPickupEnabled(true);store.setCurrency("KES");
        SokoProduct product=new SokoProduct();product.setId(5L);product.setStoreId(2L);product.setStatus("PUBLISHED");product.setStockQuantity(1);product.setName("Milk");
        store.setPaymentAccountId(3L);
        var account=new org.pms.silverocean.database.pms.entities.PaymentAccount();
        account.setActive(true);account.setVerified(true);account.setCategory(org.pms.silverocean.service.account.enums.AccountCategory.MERCHANT);
        account.setChannel(org.pms.silverocean.service.payment.wrappers.PaymentChannel.MPESA);
        when(accounts.getAccountByIdAndCreatedBy(3L,store.getOwnerUserId())).thenReturn(account);
        when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(products.findByIdForUpdate(5L)).thenReturn(Optional.of(product));
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,2)),"PICKUP",null,"0712345678",null,null);
        assertThrows(PMSCustomException.class,()->service.checkout(request));verify(orders,never()).save(any());
    }

    @Test void paidInvoiceMovesOrderToPaidIdempotently(){
        SokoOrder order=new SokoOrder();order.setPaymentStatus("UNPAID");order.setStatus("PENDING_PAYMENT");order.setActive(true);
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
    @Test void checkoutRechecksInactivePayeeBeforeReservingStock(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);
        store.setStatus("PUBLISHED");store.setPickupEnabled(true);store.setPaymentAccountId(3L);
        var account=new org.pms.silverocean.database.pms.entities.PaymentAccount();
        account.setVerified(true);account.setActive(false);
        when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));
        when(accounts.getAccountByIdAndCreatedBy(3L,7L)).thenReturn(account);
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"PICKUP",null,"0712345678",null,null);
        assertThrows(PMSCustomException.class,()->service.checkout(request));
        verifyNoInteractions(products,invoices);
    }
    @Test void wrongCodeRetainsProofAndLimitsAttempts(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);
        SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);
        order.setStatus("DISPATCHED");order.setDeliveryMethod("DELIVERY");
        order.setDeliveryCode("123456");order.setDeliveryCodeExpiresAt(ZonedDateTime.now().plusHours(1));order.setDeliveryProofReference("protected-proof");
        when(users.getUserId()).thenReturn(7L);
        when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));
        when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));
        for(int i=0;i<5;i++)assertThrows(PMSCustomException.class,()->service.confirmDelivery(9L,new SokoRequests.DeliveryConfirmation("000000")));
        assertEquals(5,order.getDeliveryCodeAttempts());
        assertEquals("protected-proof",order.getDeliveryProofReference());
        assertThrows(PMSCustomException.class,()->service.confirmDelivery(9L,new SokoRequests.DeliveryConfirmation("123456")));
        assertEquals("DISPATCHED",order.getStatus());
    }
}
