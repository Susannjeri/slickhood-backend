package org.pms.silverocean.service.soko;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.SokoOrderItemRepo;
import org.pms.silverocean.database.pms.SokoOrderRepo;
import org.pms.silverocean.database.pms.SokoProductImageRepo;
import org.pms.silverocean.database.pms.SokoProductRepo;
import org.pms.silverocean.database.pms.SokoProductVariationRepo;
import org.pms.silverocean.database.pms.SokoRiderRepo;
import org.pms.silverocean.database.pms.SokoStoreRepo;
import org.pms.silverocean.database.pms.entities.SokoProduct;
import org.pms.silverocean.database.pms.entities.SokoStore;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.filestorage.UploadMalwarePolicy;
import org.pms.silverocean.service.kyc.MarketplaceKycGate;
import org.pms.silverocean.service.notification.BusinessNotificationService;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.pms.silverocean.service.security.EncryptionService;
import org.pms.silverocean.service.visitor.VisitorService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SokoCatalogServiceTest {
    @Mock SokoStoreRepo stores;
    @Mock SokoProductRepo products;
    @Mock SokoProductImageRepo productImages;
    @Mock SokoProductVariationRepo variations;
    @Mock SokoOrderRepo orders;
    @Mock SokoOrderItemRepo items;
    @Mock SokoRiderRepo riders;
    @Mock InvoiceDao invoices;
    @Mock AccountDao accounts;
    @Mock UserDao users;
    @Mock VisitorService visitors;
    @Mock GarageService garage;
    @Mock UploadMalwarePolicy malwarePolicy;
    @Mock EncryptionService encryption;
    @Mock NotificationService notifications;
    @Mock BusinessNotificationService businessAlerts;
    @Mock I18NService i18n;
    @Mock MarketplaceKycGate marketplaceKycGate;

    private SokoService service;

    @BeforeEach
    void setUp() {
        service=new SokoService(stores,products,productImages,variations,orders,items,riders,invoices,accounts,users,
                visitors,garage,malwarePolicy,encryption,notifications,businessAlerts,i18n,marketplaceKycGate);
    }

    @Test
    void categoryFulfilmentAndPaginationReachTheRepositoryBeforeHydration() {
        SokoProduct product=new SokoProduct();
        product.setId(91L);product.setStoreId(7L);product.setName("Fresh milk");product.setCategory("DAIRY_EGGS");
        product.setPrice(new BigDecimal("125.00"));product.setCurrency("KES");product.setStockQuantity(4);product.setStatus("PUBLISHED");
        SokoStore store=new SokoStore();
        store.setId(7L);store.setName("Neighbourhood Grocer");store.setDeliveryEnabled(true);store.setDeliveryFee(BigDecimal.TEN);
        Pageable repositoryPageable=PageRequest.of(2,100);
        when(products.searchCatalog(any(),isNull(),eq("DAIRY_EGGS"),eq("milk"),eq("DELIVERY"),eq("PRICE"),isNull(),isNull(),isNull(),isNull(),isNull(),isNull(),isNull(),eq(0)))
                .thenReturn(new PageImpl<>(List.of(product),repositoryPageable,301));
        when(stores.findAllById(List.of(7L))).thenReturn(List.of(store));

        var result=service.catalog(PageRequest.of(2,500,Sort.by("price").descending()),null,"Dairy & eggs"," milk ",
                null,null,25d,"price","delivery");

        ArgumentCaptor<Pageable> pageable=ArgumentCaptor.forClass(Pageable.class);
        verify(products).searchCatalog(pageable.capture(),isNull(),eq("DAIRY_EGGS"),eq("milk"),eq("DELIVERY"),eq("PRICE"),isNull(),isNull(),isNull(),isNull(),isNull(),isNull(),isNull(),eq(0));
        assertEquals(2,pageable.getValue().getPageNumber());
        assertEquals(100,pageable.getValue().getPageSize());
        assertFalse(pageable.getValue().getSort().isSorted());
        assertEquals(301,result.getTotalElements());
        assertEquals(4,result.getTotalPages());
        assertEquals(91L,result.getContent().getFirst().product().id());
    }

    @Test
    void nearestUsesDefaultRadiusOnlyWhenCoordinatesArePresent() {
        Pageable firstPage=PageRequest.of(0,20);
        when(products.searchCatalog(any(),isNull(),isNull(),isNull(),eq("ALL"),eq("NEAREST"),eq(-1.286389),eq(36.817223),eq(25d),any(),any(),any(),any(),eq(0)))
                .thenReturn(new PageImpl<>(List.of(),firstPage,0));

        service.catalog(firstPage,null,"ALL",null,-1.286389,36.817223,null,"nearest",null);

        ArgumentCaptor<Double> minLatitude=ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> maxLatitude=ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> minLongitude=ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> maxLongitude=ArgumentCaptor.forClass(Double.class);
        verify(products).searchCatalog(any(),isNull(),isNull(),isNull(),eq("ALL"),eq("NEAREST"),eq(-1.286389),eq(36.817223),eq(25d),
                minLatitude.capture(),maxLatitude.capture(),minLongitude.capture(),maxLongitude.capture(),eq(0));
        assertEquals(-1.5112,minLatitude.getValue(),.001);
        assertEquals(-1.0616,maxLatitude.getValue(),.001);
        assertEquals(36.5923,minLongitude.getValue(),.001);
        assertEquals(37.0421,maxLongitude.getValue(),.001);
    }

    @Test
    void invalidCatalogueOptionsAreRejectedBeforeRepositoryAccess() {
        assertThrows(PMSCustomException.class,()->service.catalog(PageRequest.of(0,20),null,null,null,null,null,null,"CHEAPEST","ALL"));
        assertThrows(PMSCustomException.class,()->service.catalog(PageRequest.of(0,20),null,null,null,null,null,null,"PRICE","COURIER"));
        assertThrows(PMSCustomException.class,()->service.catalog(PageRequest.of(0,20),null,null,null,null,null,null,"NEAREST","ALL"));
        assertThrows(PMSCustomException.class,()->service.catalog(PageRequest.of(0,20),null,null,null,-1.2,null,25d,"RELEVANCE","ALL"));
        assertThrows(PMSCustomException.class,()->service.catalog(PageRequest.of(0,20),null,null,null,-1.2,36.8,.5d,"RELEVANCE","ALL"));
        assertThrows(PMSCustomException.class,()->service.catalog(PageRequest.of(0,20),null,null,null,-1.2,36.8,101d,"RELEVANCE","ALL"));
        verifyNoInteractions(products);
    }
}
