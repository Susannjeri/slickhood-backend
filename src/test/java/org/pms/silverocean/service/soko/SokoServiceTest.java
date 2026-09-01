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
import org.pms.silverocean.database.pms.SokoRiderRepo;
import org.pms.silverocean.database.pms.SokoStoreRepo;
import org.pms.silverocean.database.pms.entities.SokoOrder;
import org.pms.silverocean.database.pms.entities.SokoProduct;
import org.pms.silverocean.database.pms.entities.SokoRider;
import org.pms.silverocean.database.pms.entities.SokoStore;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.security.EncryptionService;
import org.pms.silverocean.service.security.DecryptDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.visitor.VisitorService;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SokoServiceTest {
    @Mock SokoStoreRepo stores; @Mock SokoProductRepo products; @Mock SokoOrderRepo orders;
    @Mock SokoOrderItemRepo items; @Mock InvoiceDao invoices; @Mock AccountDao accounts;
    @Mock SokoRiderRepo riders; @Mock UserDao users; @Mock VisitorService visitors;
    @Mock SokoProductImageRepo productImages; @Mock GarageService garage; @Mock EncryptionService encryption; @Mock NotificationService notifications; @Mock I18NService i18n;
    SokoService service;

    @BeforeEach void setup(){service=new SokoService(stores,products,productImages,orders,items,riders,invoices,accounts,users,visitors,garage,encryption,notifications,i18n);}

    @Test void createRiderRegistersAnAvailablePreferredRider(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);when(users.getUserId()).thenReturn(7L);when(stores.findByIdAndOwnerUserIdAndActiveTrue(2L,7L)).thenReturn(Optional.of(store));when(riders.save(any())).thenAnswer(i->i.getArgument(0));
        SokoRider rider=service.createRider(new SokoRequests.RiderUpsert(2L,"individual","Jane Rider","0712345678",null,"Motorbike","KDA 123A",null));
        assertEquals("AVAILABLE",rider.getAvailability());assertEquals("ACTIVE",rider.getStatus());assertEquals("INDIVIDUAL",rider.getRiderType());
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

    @Test void dispatchAssignsPreferredRiderAndMarksThemBusy(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setStatus("PACKED");order.setDeliveryMethod("DELIVERY");order.setActive(true);SokoRider rider=new SokoRider();rider.setId(3L);rider.setStoreId(2L);rider.setStatus("ACTIVE");rider.setAvailability("AVAILABLE");rider.setDisplayName("Jane Rider");rider.setPhoneNumber("0712345678");rider.setVehiclePlate("KDA 123A");rider.setActive(true);
        when(users.getUserId()).thenReturn(7L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(store));when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(rider));when(stores.findById(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());when(encryption.encrypt(anyString())).thenReturn(new byte[]{1,2,3});
        var result=service.transition(9L,"DISPATCHED",new SokoRequests.Dispatch(3L,null,null,null,java.time.LocalDateTime.now().plusHours(1)));
        assertEquals("BUSY",rider.getAvailability());assertEquals(3L,result.order().getRiderId());assertEquals("Jane Rider",result.order().getCourierName());assertArrayEquals(new byte[]{1,2,3},order.getEncryptedDeliveryCode());assertNull(order.getDeliveryCode());
    }

    @Test void deliveryCodeCompletesOrderAndReleasesPreferredRider(){
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setStatus("DISPATCHED");order.setDeliveryMethod("DELIVERY");order.setDeliveryCode("123456");order.setDeliveryProofReference("soko/delivery-proof/9/proof.jpg");order.setRiderId(3L);order.setActive(true);SokoRider rider=new SokoRider();rider.setId(3L);rider.setStoreId(2L);rider.setStatus("ACTIVE");rider.setAvailability("BUSY");rider.setActive(true);
        when(users.getUserId()).thenReturn(7L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndOwnerUserIdAndActiveTrue(2L,7L)).thenReturn(Optional.of(store));when(riders.findForUpdate(3L,2L)).thenReturn(Optional.of(rider));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());
        var result=service.confirmDelivery(9L,new SokoRequests.DeliveryConfirmation("123456"));
        assertEquals("COMPLETED",result.order().getStatus());assertTrue(result.order().isDeliveryCodeVerified());assertEquals("AVAILABLE",rider.getAvailability());assertEquals(1,rider.getCompletedDeliveries());
    }

    @Test void customerCanReadEncryptedDeliveryCodeWithoutExposingCiphertext(){
        SokoOrder order=new SokoOrder();order.setId(9L);order.setCustomerUserId(4L);order.setDeliveryMethod("DELIVERY");order.setStatus("DISPATCHED");order.setEncryptedDeliveryCode(new byte[]{1,2,3});
        when(users.getUserId()).thenReturn(4L);when(orders.findById(9L)).thenReturn(Optional.of(order));when(encryption.decrypt(order.getEncryptedDeliveryCode())).thenReturn(new DecryptDTO(false,"123456"));
        assertEquals("123456",service.deliveryCode(9L));
    }

    @Test void repeatedCheckoutKeyReturnsOriginalOrderWithoutReservingStockAgain(){
        SokoStore store=new SokoStore();store.setId(2L);store.setName("Fresh Corner");SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setCustomerUserId(4L);order.setCheckoutIdempotencyKey("checkout-1");order.setActive(true);
        when(users.getUserId()).thenReturn(4L);when(orders.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(4L,"checkout-1")).thenReturn(Optional.of(order));when(stores.findById(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());
        var request=new SokoRequests.Checkout(2L,List.of(new SokoRequests.CheckoutItem(5L,1)),"PICKUP",null,"0712345678",null,null);
        assertEquals(9L,service.checkout(request,"checkout-1").order().getId());verifyNoInteractions(products);
    }

    @Test void deliveryProofUsesServerGeneratedKeyAndRejectsSpoofing() throws Exception {
        SokoStore store=new SokoStore();store.setId(2L);store.setOwnerUserId(7L);store.setActive(true);SokoOrder order=new SokoOrder();order.setId(9L);order.setStoreId(2L);order.setStatus("DISPATCHED");order.setActive(true);
        when(users.getUserId()).thenReturn(7L);when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));when(stores.findByIdAndOwnerUserIdAndActiveTrue(2L,7L)).thenReturn(Optional.of(store));when(stores.findById(2L)).thenReturn(Optional.of(store));when(items.findAllByOrderIdAndActiveTrueOrderById(9L)).thenReturn(List.of());
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
}
