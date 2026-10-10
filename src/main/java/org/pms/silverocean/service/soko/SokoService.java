package org.pms.silverocean.service.soko;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.SokoOrderItemRepo;
import org.pms.silverocean.database.pms.SokoOrderRepo;
import org.pms.silverocean.database.pms.SokoFinanceOperationRepo;
import org.pms.silverocean.database.pms.SokoProductRepo;
import org.pms.silverocean.database.pms.SokoProductImageRepo;
import org.pms.silverocean.database.pms.SokoProductVariationRepo;
import org.pms.silverocean.database.pms.SokoRiderRepo;
import org.pms.silverocean.database.pms.SokoStoreRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.database.pms.entities.SokoOrder;
import org.pms.silverocean.database.pms.entities.SokoFinanceOperation;
import org.pms.silverocean.database.pms.entities.SokoOrderItem;
import org.pms.silverocean.database.pms.entities.SokoProduct;
import org.pms.silverocean.database.pms.entities.SokoProductImage;
import org.pms.silverocean.database.pms.entities.SokoProductVariation;
import org.pms.silverocean.database.pms.entities.SokoRider;
import org.pms.silverocean.database.pms.entities.SokoStore;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.pms.silverocean.service.payment.PaymentRequestException;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.filestorage.UploadMalwarePolicy;
import org.pms.silverocean.service.security.EncryptionService;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.kyc.MarketplaceKycGate;
import org.pms.silverocean.service.subscription.SubscriptionEntitlementService;
import org.pms.silverocean.service.subscription.enums.SubscriptionProduct;
import org.pms.silverocean.service.users.ProfileType;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.soko.SokoModels.CatalogProduct;
import org.pms.silverocean.service.soko.SokoModels.OrderDetail;
import org.pms.silverocean.service.soko.SokoModels.DeliveryDestination;
import org.pms.silverocean.service.soko.SokoModels.SellerSummary;
import org.pms.silverocean.service.soko.SokoModels.StoreDetail;
import org.pms.silverocean.service.visitor.VisitorService;
import org.pms.silverocean.service.visitor.enums.VisitorCategory;
import org.pms.silverocean.service.visitor.wrappers.CreateVisitorRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pms.silverocean.service.audit.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;

@Service
@RequiredArgsConstructor
@Slf4j
public class SokoService {
    private static final String DRAFT="DRAFT", PENDING_REVIEW="PENDING_REVIEW", PUBLISHED="PUBLISHED", REJECTED="REJECTED", SUSPENDED="SUSPENDED", OUT_OF_STOCK="OUT_OF_STOCK";
    private static final int MAX_CHECKOUT_LINES=25, MAX_CHECKOUT_QUANTITY=100;
    private static final Set<String> CATALOG_SORT_MODES=Set.of("RELEVANCE","PRICE","NEAREST");
    private static final Set<String> CATALOG_FULFILMENT=Set.of("ALL","DELIVERY","PICKUP");
    private static final SecureRandom SECURE_RANDOM=new SecureRandom();
    private static final java.util.regex.Pattern RESTRICTED_PRODUCT=java.util.regex.Pattern.compile("(?i)\\b(alcohol|beer|wine|spirit|cigarette|tobacco|vape|cannabis|marijuana|firearm|ammunition|weapon)\\b");
    private final SokoStoreRepo storeRepo;
    private final SokoProductRepo productRepo;
    private final SokoProductImageRepo productImageRepo;
    private final SokoProductVariationRepo productVariationRepo;
    private final SokoOrderRepo orderRepo;
    private final SokoFinanceOperationRepo financeOperationRepo;
    private final SokoOrderItemRepo itemRepo;
    private final SokoRiderRepo riderRepo;
    private final UnitRepo unitRepo;
    private final InvoiceDao invoiceDao;
    private final AccountDao accountDao;
    private final UserDao userDao;
    private final VisitorService visitorService;
    private final GarageService garageService;
    private final UploadMalwarePolicy malwarePolicy;
    private final EncryptionService encryptionService;
    private final NotificationService notificationService;
    private final org.pms.silverocean.service.notification.BusinessNotificationService businessAlerts;
    private final I18NService i18n;
    private final MarketplaceKycGate marketplaceKycGate;
    private final SubscriptionEntitlementService subscriptionEntitlements;
    @Value("${soko.stock-reservation-minutes:20}") private long reservationMinutes;
    @Value("${soko.delivery-code-valid-hours:24}") private long deliveryCodeValidHours;
    @Value("${soko.delivery-recovery-otp-valid-minutes:10}") private long deliveryRecoveryOtpValidMinutes;
    @Value("${soko.delivery-recovery-cooldown-seconds:60}") private long deliveryRecoveryCooldownSeconds;
    @Value("${soko.delivery-recovery-max-requests-per-day:3}") private int deliveryRecoveryMaxRequestsPerDay;
    @Value("${soko.rider-phone-confirmation-valid-minutes:10}") private long riderPhoneConfirmationValidMinutes;
    @Value("${soko.rider-phone-confirmation-cooldown-seconds:60}") private long riderPhoneConfirmationCooldownSeconds;
    @Value("${soko.rider-phone-confirmation-max-attempts:5}") private int riderPhoneConfirmationMaxAttempts;
    @Value("${soko.rider-phone-confirmation-max-requests-per-day:5}") private int riderPhoneConfirmationMaxRequestsPerDay;
    @Value("${soko.rider-assignment-token-valid-hours:2}") private long riderAssignmentTokenValidHours;
    @Value("${soko.rider-assignment-link-cooldown-seconds:60}") private long riderAssignmentLinkCooldownSeconds;
    @Value("${soko.rider-assignment-link-max-sends:5}") private int riderAssignmentLinkMaxSends;
    @Value("${soko.rider-assignment-accepted-collect-minutes:60}") private long riderAssignmentAcceptedCollectMinutes;
    @Value("${soko.rider-assignment-url:https://app.slickhood.com/soko/rider-assignment}") private String riderAssignmentUrl;
    private static final ObjectMapper JSON = new ObjectMapper();
    @Autowired(required=false) private AuditLogService auditLogService;

    public Page<CatalogProduct> catalog(Pageable pageable, Long storeId, String category, String query, Double latitude, Double longitude, Double radiusKm) {
        return catalog(pageable,storeId,category,query,latitude,longitude,radiusKm,null,null);
    }

    public Page<CatalogProduct> catalog(Pageable pageable, Long storeId, String category, String query, Double latitude, Double longitude, Double radiusKm, String sortMode, String fulfilment) {
        if(storeId!=null&&storeId<=0)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Choose a valid Soko shop.");
        String rawCategory=StringUtils.trimToNull(category);
        String cleanCategory=rawCategory==null||"ALL".equalsIgnoreCase(rawCategory)?null:SokoGroceryCategory.normalize(rawCategory);
        String cleanQuery=StringUtils.trimToNull(query);
        if(cleanQuery!=null&&cleanQuery.length()>160)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Search text cannot exceed 160 characters.");
        String cleanSortMode=catalogOption(sortMode,"RELEVANCE",CATALOG_SORT_MODES,"Choose relevance, price or nearest sorting.");
        String cleanFulfilment=catalogOption(fulfilment,"ALL",CATALOG_FULFILMENT,"Choose all, delivery or pickup fulfilment.");
        validateCatalogLocation(latitude,longitude,radiusKm,cleanSortMode);
        Double effectiveRadius=latitude==null?null:radiusKm==null?25d:radiusKm;
        GeoBounds bounds=GeoBounds.of(latitude,longitude,effectiveRadius);
        Page<SokoProduct> page=productRepo.searchCatalog(catalogPageable(pageable),storeId,cleanCategory,cleanQuery,
                cleanFulfilment,cleanSortMode,latitude,longitude,effectiveRadius,bounds.minLatitude(),bounds.maxLatitude(),
                bounds.minLongitude(),bounds.maxLongitude(),bounds.wrapLongitude()?1:0);
        List<Long> productIds=page.stream().map(SokoProduct::getId).toList();
        Map<Long,List<String>> images=imageUrlsByProduct(productIds);
        hydrateVariationStock(page.getContent());
        Map<Long,SokoStore> stores=storeRepo.findAllById(page.stream().map(SokoProduct::getStoreId).distinct().toList())
                .stream().collect(Collectors.toMap(SokoStore::getId, Function.identity()));
        return page.map(p->{
            SokoStore s=stores.get(p.getStoreId());
            return new CatalogProduct(SokoModels.PublicProduct.from(p),s==null?"Soko merchant":s.getName(),
                    s==null?null:s.getAddress(),s==null?null:s.getPhoneNumber(),s!=null&&s.isDeliveryEnabled(),
                    s!=null&&s.isPickupEnabled(),s==null?null:distanceKm(latitude,longitude,s.getLatitude(),s.getLongitude()),
                    s==null?null:s.getServiceRadiusKm(),images.getOrDefault(p.getId(),legacyImage(p)),s==null?BigDecimal.ZERO:zero(s.getDeliveryFee()));
        });
    }

    public Page<SellerSummary> sellers(Pageable pageable,String query,String fulfilment) {
        String cleanQuery=StringUtils.trimToNull(query);
        if(cleanQuery!=null&&cleanQuery.length()>160)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Search text cannot exceed 160 characters.");
        String cleanFulfilment=catalogOption(fulfilment,"ALL",CATALOG_FULFILMENT,"Choose all, delivery or pickup fulfilment.");
        return storeRepo.searchPublicSellers(cleanQuery,cleanFulfilment,catalogPageable(pageable)).map(SellerSummary::from);
    }

    public List<DeliveryDestination> deliveryDestinations() {
        long userId=userDao.getUserId();
        List<SystemDestination> destinations=authorizedSystemDestinations(userId);
        Long preferredUnitId=null;
        if(destinations.size()==1)preferredUnitId=destinations.getFirst().unitId();
        else if(!destinations.isEmpty()){
            List<Long> recent=orderRepo.findRecentCompletedDestinationUnitIds(userId,
                    destinations.stream().map(SystemDestination::unitId).toList(),PageRequest.of(0,1));
            if(!recent.isEmpty())preferredUnitId=recent.getFirst();
        }
        final Long preferred=preferredUnitId;
        return destinations.stream().map(destination->new DeliveryDestination(destination.unitId(),destination.propertyId(),
                destination.label(),destination.address(),destination.latitude(),destination.longitude(),
                destination.source(),java.util.Objects.equals(destination.unitId(),preferred))).toList();
    }

    public StoreDetail storeDetail(long id) {
        SokoStore s=storeRepo.findByIdAndActiveTrue(id).filter(x->PUBLISHED.equals(x.getStatus())).orElseThrow(this::notFound);
        List<SokoProduct> products=productRepo.findAllByStoreIdAndActiveTrueOrderByName(id).stream().filter(p->PUBLISHED.equals(p.getStatus())&&p.getStockQuantity()>0).toList();
        hydrateVariationStock(products);
        return new StoreDetail(SokoModels.PublicStore.from(s),products.stream().map(SokoModels.PublicProduct::from).toList());
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public SokoStore createStore(SokoRequests.StoreUpsert request) {
        requireMerchantRole();
        long ownerUserId=userDao.getUserId();
        if(storeRepo.existsByOwnerUserIdAndActiveTrue(ownerUserId))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Your Soko workspace already has a shop. Edit that shop instead of creating another one.");
        SokoStore s=new SokoStore(); s.setOwnerUserId(ownerUserId); s.setCreatedBy(ownerUserId); s.setActive(true); s.setStatus(DRAFT);
        applyStore(s,request); return storeRepo.save(s);
    }

    @Transactional
    public SokoStore updateStore(long id,SokoRequests.StoreUpsert request) {
        SokoStore s=ownedStore(id); applyStore(s,request); if(PUBLISHED.equals(s.getStatus()))validatePublishable(s);else if(PENDING_REVIEW.equals(s.getStatus())||REJECTED.equals(s.getStatus()))s.setStatus(DRAFT); return storeRepo.save(s);
    }

    @Transactional
    public SokoStore publishStore(long id) {
        SokoStore s=ownedStore(id); validatePublishable(s);if(SUSPENDED.equals(s.getStatus()))throw forbidden();
        marketplaceKycGate.require(s.getOwnerUserId(),"PROVIDER_TYPE","SOKO_MERCHANT",profileType(s.getOwnerUserId()));
        s.setStatus(PENDING_REVIEW);s.setSubmittedAt(now());s.setReviewReason(null);s.setReviewedAt(null);s.setReviewedByUserId(null);return storeRepo.save(s);
    }

    public List<SokoStore> myStores(){return storeRepo.findAllByOwnerUserIdAndActiveTrueOrderByName(userDao.getUserId());}

    @Transactional
    public SokoProduct createProduct(SokoRequests.ProductUpsert request){
        SokoStore store=ownedStore(request.storeId()); SokoProduct p=new SokoProduct(); p.setStoreId(store.getId()); p.setCreatedBy(userDao.getUserId()); p.setActive(true); p.setStatus(DRAFT); applyProduct(p,request,store); p=productRepo.save(p);syncVariations(p,request.variations());return productRepo.save(p);
    }

    @Transactional
    public SokoProduct updateProduct(long id,SokoRequests.ProductUpsert request){
        SokoProduct p=productRepo.findByIdForUpdate(id).orElseThrow(this::notFound); SokoStore store=ownedStore(p.getStoreId()); if(store.getId()!=request.storeId())throw invalid();if(productIsInOpenOrder(id))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"This product is part of an open order. Finish or cancel that order before changing product stock or variations.");applyProduct(p,request,store);if(request.variations()!=null)syncVariations(p,request.variations());return productRepo.save(p);
    }

    @Transactional
    public SokoProduct publishProduct(long id){
        SokoProduct p=productRepo.findByIdForUpdate(id).orElseThrow(this::notFound);
        SokoStore store=ownedStore(p.getStoreId());
        if(SUSPENDED.equals(p.getStatus()))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                "This product is suspended and cannot be published until its moderation issue is resolved.");
        if(!PUBLISHED.equals(store.getStatus())){
            String message=PENDING_REVIEW.equals(store.getStatus())
                    ? "Your shop is awaiting approval. Publish products after the shop has been approved."
                    : "Publish and obtain approval for your shop before publishing products.";
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,message);
        }
        p.setCategory(SokoGroceryCategory.normalize(p.getCategory()));
        validateAllowedGrocery(p.getName(),p.getDescription());
        marketplaceKycGate.require(store.getOwnerUserId(),"SOKO_CATEGORY",p.getCategory(),profileType(store.getOwnerUserId()));
        if(StringUtils.isBlank(p.getImageUrl())&&productImageRepo.findAllByProductIdAndActiveTrueOrderByDisplayOrderAsc(id).isEmpty())
            throw new PMSCustomException(ResponseCode.INVALID_IMAGE,"Add at least one JPEG, PNG or WebP product photo before publishing.");
        p.setStatus(p.getStockQuantity()>0?PUBLISHED:OUT_OF_STOCK); return productRepo.save(p);
    }

    @Transactional
    public SokoProduct pauseProduct(long id){SokoProduct p=productRepo.findByIdForUpdate(id).orElseThrow(this::notFound);ownedStore(p.getStoreId());if(!List.of(PUBLISHED,OUT_OF_STOCK).contains(p.getStatus()))throw invalid();p.setStatus("PAUSED");p=productRepo.save(p);audit(p,"SOKO_PRODUCT_PAUSED");return p;}

    @Transactional
    public void removeProduct(long id){SokoProduct p=productRepo.findByIdForUpdate(id).orElseThrow(this::notFound);ownedStore(p.getStoreId());if(productIsInOpenOrder(id))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"This product is part of an open order. Pause it instead.");p.setActive(false);p.setStatus("REMOVED");productRepo.save(p);audit(p,"SOKO_PRODUCT_REMOVED");}

    public List<SokoProduct> myProducts(long storeId){ownedStore(storeId);List<SokoProduct> products=productRepo.findAllByStoreIdAndActiveTrueOrderByName(storeId);hydrateVariationStock(products);return products;}

    public SokoModels.AdminSummary adminSummary(){requireSuperAdmin();long orders=orderRepo.countByActiveTrue();long complete=orderRepo.countByStatusAndActiveTrue("COMPLETED")+orderRepo.countByStatusAndActiveTrue("CANCELLED")+orderRepo.countByStatusAndActiveTrue("EXPIRED");return new SokoModels.AdminSummary(storeRepo.countByActiveTrue(),storeRepo.countByStatusAndActiveTrue(PENDING_REVIEW),storeRepo.countByStatusAndActiveTrue(PUBLISHED),productRepo.countByActiveTrue(),productRepo.countByStatusAndActiveTrue(PUBLISHED),orders,Math.max(0,orders-complete));}
    public Page<SokoStore> adminStores(String status,Pageable pageable){requireSuperAdmin();Pageable safe=bounded(pageable);return StringUtils.isBlank(status)?storeRepo.findAllByActiveTrue(safe):storeRepo.findAllByStatusAndActiveTrue(status.trim().toUpperCase(Locale.ROOT),safe);}
    public Page<SokoProduct> adminProducts(Pageable pageable){requireSuperAdmin();return productRepo.findAllByActiveTrue(bounded(pageable));}
    public Page<OrderDetail> adminOrders(Pageable pageable){requireSuperAdmin();return hydrate(orderRepo.findAllByActiveTrue(bounded(pageable)));}
    @Transactional public SokoStore moderateStore(long id,SokoRequests.ModerationDecision request){requireSuperAdmin();SokoStore store=storeRepo.findByIdAndActiveTrue(id).orElseThrow(this::notFound);String decision=request.decision().toUpperCase(Locale.ROOT);if(("REJECT".equals(decision)||"SUSPEND".equals(decision))&&StringUtils.isBlank(request.reason()))throw invalid();switch(decision){case "APPROVE"->{if(!PENDING_REVIEW.equals(store.getStatus()))throw invalid();validatePublishable(store);marketplaceKycGate.require(store.getOwnerUserId(),"PROVIDER_TYPE","SOKO_MERCHANT",profileType(store.getOwnerUserId()));store.setStatus(PUBLISHED);}case "REJECT"->{if(!PENDING_REVIEW.equals(store.getStatus()))throw invalid();store.setStatus(REJECTED);}case "SUSPEND"->{if(!PUBLISHED.equals(store.getStatus()))throw invalid();store.setStatus(SUSPENDED);}case "REACTIVATE"->{if(!SUSPENDED.equals(store.getStatus()))throw invalid();validatePublishable(store);marketplaceKycGate.require(store.getOwnerUserId(),"PROVIDER_TYPE","SOKO_MERCHANT",profileType(store.getOwnerUserId()));store.setStatus(PUBLISHED);}default->throw invalid();}store.setReviewedAt(now());store.setReviewedByUserId(userDao.getUserId());store.setReviewReason(StringUtils.left(StringUtils.trimToNull(request.reason()),1000));store=storeRepo.save(store);notifyModeration(store.getOwnerUserId(),store.getName(),store.getStatus(),store.getReviewReason());return store;}
    @Transactional public SokoProduct moderateProduct(long id,SokoRequests.ModerationDecision request){requireSuperAdmin();SokoProduct product=productRepo.findByIdForUpdate(id).orElseThrow(this::notFound);String decision=request.decision().toUpperCase(Locale.ROOT);if("SUSPEND".equals(decision)){if(StringUtils.isBlank(request.reason())||!List.of(PUBLISHED,OUT_OF_STOCK).contains(product.getStatus()))throw invalid();product.setStatus(SUSPENDED);}else if("REACTIVATE".equals(decision)){if(!SUSPENDED.equals(product.getStatus()))throw invalid();SokoStore store=storeRepo.findByIdAndActiveTrue(product.getStoreId()).orElseThrow(this::notFound);if(!PUBLISHED.equals(store.getStatus()))throw invalid();marketplaceKycGate.require(store.getOwnerUserId(),"SOKO_CATEGORY",product.getCategory(),profileType(store.getOwnerUserId()));product.setStatus(product.getStockQuantity()>0?PUBLISHED:OUT_OF_STOCK);}else throw invalid();product.setModeratedAt(now());product.setModeratedByUserId(userDao.getUserId());product.setModerationReason(StringUtils.left(StringUtils.trimToNull(request.reason()),1000));product=productRepo.save(product);SokoStore owner=storeRepo.findByIdAndActiveTrue(product.getStoreId()).orElseThrow(this::notFound);notifyModeration(owner.getOwnerUserId(),product.getName(),product.getStatus(),product.getModerationReason());return product;}

    @Transactional
    public SokoModels.ProductImages replaceProductImages(long productId, List<MultipartFile> images) throws IOException {
        SokoProduct product=productRepo.findByIdForUpdate(productId).orElseThrow(this::notFound);
        ownedStore(product.getStoreId());
        if(images==null||images.isEmpty()||images.size()>5)throw new PMSCustomException(ResponseCode.INVALID_IMAGE);
        List<PendingImage> pending=new ArrayList<>(images.size());
        long total=0;
        for(MultipartFile image:images){
            byte[] bytes=validatedImageBytes(image);malwarePolicy.requireSafe(bytes);
            total+=bytes.length;
            if(total>25L*1024*1024)throw new PMSCustomException(ResponseCode.MAX_UPLOAD_SIZE_EXCEEDED);
            String type=image.getContentType().toLowerCase(Locale.ROOT);
            pending.add(new PendingImage(bytes,type,imageExtension(type)));
        }
        String prefix="soko/products/"+productId+"/"+UUID.randomUUID()+"/";
        List<SokoProductImage> existing=productImageRepo.findAllByProductIdAndActiveTrueOrderByDisplayOrderAsc(productId);
        existing.forEach(i->i.setActive(false));
        productImageRepo.saveAll(existing);
        List<SokoProductImage> saved=new ArrayList<>();
        for(int i=0;i<pending.size();i++){
            PendingImage image=pending.get(i); String key=prefix+UUID.randomUUID()+"."+image.extension();
            garageService.uploadBytes(key,image.bytes(),image.contentType());
            SokoProductImage row=new SokoProductImage();row.setProductId(productId);row.setFileRef(key);row.setContentType(image.contentType());row.setFileSize(image.bytes().length);row.setDisplayOrder(i);row.setCreatedBy(userDao.getUserId());row.setActive(true);saved.add(row);
        }
        productImageRepo.saveAll(saved);
        product.setImageUrl(null);
        productRepo.save(product);
        return productImages(productId);
    }

    public SokoModels.ProductImages productImages(long productId){
        SokoProduct product=productRepo.findById(productId).filter(SokoProduct::isActive).orElseThrow(this::notFound);
        if(!PUBLISHED.equals(product.getStatus()))ownedStore(product.getStoreId());
        List<String> urls=productImageRepo.findAllByProductIdAndActiveTrueOrderByDisplayOrderAsc(productId).stream().map(i->garageService.getPresignedUrlForStoredObject(i.getFileRef())).filter(StringUtils::isNotBlank).toList();
        if(urls.isEmpty())urls=legacyImage(product);
        return new SokoModels.ProductImages(productId,urls);
    }

    @Transactional
    public SokoModels.RiderVerificationResult createRider(SokoRequests.RiderUpsert request){
        ownedStore(request.storeId());
        String phone=normalizeRiderPhone(request.phoneNumber());
        if(hasDuplicateRiderPhone(request.storeId(),phone,null))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                "This phone number is already registered as a rider for the shop.");
        SokoRider rider=new SokoRider();
        rider.setStoreId(request.storeId());rider.setCreatedBy(userDao.getUserId());rider.setActive(true);
        rider.setStatus("PENDING_VERIFICATION");rider.setVerificationStatus("PENDING_VERIFICATION");rider.setAvailability("OFFLINE");
        rider.setCompletedDeliveries(0);rider.setVerified(false);rider.setPhoneConfirmed(false);rider.setPhoneConfirmationStatus("PENDING");
        applyRider(rider,request,phone);rider=riderRepo.save(rider);
        SokoModels.RiderVerificationResult result=issueRiderPhoneConfirmation(rider,false);
        audit(rider,"SOKO_RIDER_CREATED");
        return result;
    }

    @Transactional
    public SokoModels.RiderVerificationResult updateRider(long id,SokoRequests.RiderUpsert request){
        ownedStore(request.storeId());SokoRider rider=riderRepo.findForUpdate(id,request.storeId()).orElseThrow(this::notFound);
        requireRiderIdle(rider);
        String phone=normalizeRiderPhone(request.phoneNumber());
        if(!phone.equals(normalizeStoredPhone(rider.getPhoneNumber()))&&hasDuplicateRiderPhone(request.storeId(),phone,id))
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"This phone number is already registered as a rider for the shop.");
        boolean identityChanged=!java.util.Objects.equals(rider.getDisplayName(),request.displayName().trim())
                ||!java.util.Objects.equals(normalizeStoredPhone(rider.getPhoneNumber()),phone)
                ||!java.util.Objects.equals(rider.getNationalIdNumber(),request.nationalIdNumber().trim().toUpperCase(Locale.ROOT))
                ||!java.util.Objects.equals(StringUtils.lowerCase(StringUtils.trimToNull(rider.getEmail())),StringUtils.lowerCase(StringUtils.trimToNull(request.email())))
                ||!java.util.Objects.equals(rider.getRiderType(),request.riderType().trim().toUpperCase(Locale.ROOT))
                ||!java.util.Objects.equals(rider.getVehicleType(),StringUtils.trimToNull(request.vehicleType()))
                ||!java.util.Objects.equals(rider.getVehiclePlate(),StringUtils.trimToNull(request.vehiclePlate()));
        applyRider(rider,request,phone);
        if(identityChanged){
            rider.setVerificationStatus("PENDING_VERIFICATION");rider.setVerified(false);rider.setStatus("PENDING_VERIFICATION");rider.setAvailability("OFFLINE");rider.setVerifiedAt(null);rider.setVerifiedByUserId(null);
            resetRiderPhoneConfirmation(rider);
            linkRiderUser(rider);
        }
        rider=riderRepo.save(rider);
        SokoModels.RiderVerificationResult result=identityChanged
                ?issueRiderPhoneConfirmation(rider,false)
                :riderVerificationResult(rider,StringUtils.defaultIfBlank(rider.getPhoneConfirmationStatus(),rider.isPhoneConfirmed()?"CONFIRMED":"PENDING"),"Rider details saved.");
        audit(rider,"SOKO_RIDER_UPDATED");return result;
    }

    @Transactional
    public SokoModels.RiderVerificationResult requestRiderPhoneConfirmation(long id){
        SokoRider rider=riderRepo.findByIdForUpdate(id).orElseThrow(this::notFound);
        ownedStore(rider.getStoreId());
        requireRiderIdle(rider);
        return issueRiderPhoneConfirmation(rider,true);
    }

    @Transactional(noRollbackFor=PMSCustomException.class)
    public SokoModels.RiderVerificationResult confirmRiderPhone(long id,SokoRequests.RiderVerificationConfirm request){
        SokoRider rider=riderRepo.findByIdForUpdate(id).orElseThrow(this::notFound);
        ownedStore(rider.getStoreId());
        requireRiderIdle(rider);
        if(rider.isPhoneConfirmed())return riderVerificationResult(rider,"CONFIRMED","This rider's phone number is already confirmed.");
        ZonedDateTime timestamp=now();
        if(rider.getPhoneConfirmationOtp()==null||rider.getPhoneConfirmationExpiresAt()==null||!rider.getPhoneConfirmationExpiresAt().isAfter(timestamp)){
            rider.setPhoneConfirmationStatus("EXPIRED");rider.setPhoneConfirmationOtp(null);rider.setPhoneConfirmationExpiresAt(null);riderRepo.save(rider);
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"The rider confirmation code has expired. Request a new code.");
        }
        int maxAttempts=Math.max(3,Math.min(riderPhoneConfirmationMaxAttempts,10));
        if(rider.getPhoneConfirmationAttempts()>=maxAttempts){
            lockRiderPhoneConfirmation(rider);riderRepo.save(rider);
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Too many incorrect confirmation attempts. Request a new code.");
        }
        String expected=encryptionService.decrypt(rider.getPhoneConfirmationOtp()).decryptedValue();
        rider.setPhoneConfirmationAttempts(rider.getPhoneConfirmationAttempts()+1);
        if(!constantTimeEquals(expected,request.code())){
            int remaining=maxAttempts-rider.getPhoneConfirmationAttempts();
            if(remaining<=0)lockRiderPhoneConfirmation(rider);
            riderRepo.save(rider);
            String message=remaining<=0?"Too many incorrect confirmation attempts. Request a new code."
                    :"The rider confirmation code is incorrect. "+remaining+" attempt"+(remaining==1?"":"s")+" remaining.";
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,message);
        }
        rider.setPhoneConfirmed(true);rider.setPhoneConfirmationStatus("CONFIRMED");rider.setPhoneConfirmationConfirmedAt(timestamp);
        rider.setPhoneConfirmationOtp(null);rider.setPhoneConfirmationExpiresAt(null);rider.setPhoneConfirmationAttempts(0);
        linkRiderUser(rider);
        rider.setVerified(true);rider.setVerificationStatus("VERIFIED");rider.setStatus("ACTIVE");rider.setVerifiedAt(timestamp);rider.setVerifiedByUserId(userDao.getUserId());
        rider.setAvailability("AVAILABLE");
        rider=riderRepo.save(rider);audit(rider,"SOKO_RIDER_PHONE_CONFIRMED");
        return riderVerificationResult(rider,"CONFIRMED","Phone confirmed. The rider is active, available and ready for merchant-managed delivery assignments.");
    }

    public List<SokoRider> myRiders(long storeId){ownedStore(storeId);return riderRepo.findAllByStoreIdAndActiveTrueOrderByDisplayName(storeId);}

    public Page<SokoModels.RiderAssignment> riderAssignments(Pageable pageable){List<Long> ids=riderRepo.findAllByUserIdAndActiveTrue(userDao.getUserId()).stream().filter(r->r.isPhoneConfirmed()&&r.isVerified()&&"ACTIVE".equals(r.getStatus())).map(SokoRider::getId).toList();return ids.isEmpty()?Page.empty(bounded(pageable)):hydrateRiderAssignments(orderRepo.findAllByRiderIdInAndStatusNotInAndActiveTrue(ids,List.of("PACKED","FINANCE_HOLD"),bounded(pageable)));}
    public Page<SokoRider> adminRiders(Pageable pageable){requireSuperAdmin();return riderRepo.findAllByActiveTrue(bounded(pageable));}

    @Transactional public SokoRider riderDecision(long id,SokoRequests.RiderDecision request){
        requireSuperAdmin();SokoRider r=riderRepo.findByIdForUpdate(id).orElseThrow(this::notFound);
        String decision=request.decision().toUpperCase(Locale.ROOT);requireRiderIdle(r);
        if(("SUSPEND".equals(decision)||"REJECT".equals(decision))&&StringUtils.isBlank(request.reason()))throw invalid();
        switch(decision){
            case "VERIFY","ACTIVATE"->{
                if(!r.isPhoneConfirmed())throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"The rider must confirm the SMS code before activation.");
                linkRiderUser(r);
                // A rider can remain merchant-managed after confirming the phone.
                // Only expose self-service rider access when the matching account is
                // fully active; an incomplete/suspended phone owner must not block
                // the merchant from assigning the rider.
                if(r.getUserId()!=null){
                    userDao.findById(r.getUserId()).filter(u->u.isActive()&&u.isVerified()&&u.isEmailVerified()&&"ACTIVE".equals(u.getAccountStatus())).orElseThrow(()->new PMSCustomException(ResponseCode.KYC_MISSING_DOCUMENTS,"The rider must complete their existing account KYC and verify their email before self-service access."));
                    marketplaceKycGate.require(r.getUserId(),"PROVIDER_TYPE","DELIVERY_RIDER",profileType(r.getUserId()));
                }
                r.setVerified(true);r.setVerificationStatus("VERIFIED");r.setStatus("ACTIVE");r.setAvailability("AVAILABLE");r.setVerifiedAt(now());r.setVerifiedByUserId(userDao.getUserId());
            }
            case "SUSPEND"->{r.setStatus("SUSPENDED");r.setAvailability("OFFLINE");}
            case "REJECT"->{r.setVerified(false);r.setVerificationStatus("REJECTED");r.setStatus("INACTIVE");r.setAvailability("OFFLINE");}
            default->throw invalid();
        }
        r.setVerificationNotes(StringUtils.left(StringUtils.trimToNull(request.reason()),1000));r=riderRepo.save(r);audit(r,"SOKO_RIDER_"+decision);
        String eventKey="soko-rider:"+r.getId()+":"+decision+":"+java.util.UUID.randomUUID();
        String message="A registered rider\'s verification or availability was updated. Review the current status in Soko.";
        if(r.getUserId()!=null)businessAlerts.publish(r.getUserId(),eventKey,"SOKO_RIDER_STATUS",message,"/dashboard/soko-deliveries");
        storeRepo.findByIdAndActiveTrue(r.getStoreId()).ifPresent(store->businessAlerts.publish(store.getOwnerUserId(),eventKey,"SOKO_RIDER_STATUS",message,"/dashboard/soko"));
        return r;
    }

    private void requireRiderIdle(SokoRider rider){if("BUSY".equals(rider.getAvailability())||orderRepo.existsByRiderIdAndStatusInAndActiveTrue(rider.getId(),List.of("DELIVERY_ASSIGNED","ASSIGNMENT_ACCEPTED","DISPATCHED","DELIVERY_RETURN_REQUIRED","FINANCE_HOLD_RETURN_REQUIRED")))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Finish the rider's active delivery before editing, verifying, suspending or removing this rider.");}

    @Transactional
    public SokoRider setRiderAvailability(long id,String requested){
        SokoRider rider=riderRepo.findByIdForUpdate(id).orElseThrow(this::notFound);ownedStore(rider.getStoreId());String availability=requested.toUpperCase(Locale.ROOT);
        requireRiderIdle(rider);
        if(!rider.isPhoneConfirmed()||!rider.isVerified()||!"ACTIVE".equals(rider.getStatus())||!List.of("AVAILABLE","OFFLINE").contains(availability)||"BUSY".equals(rider.getAvailability()))throw invalid();
        rider.setAvailability(availability);return riderRepo.save(rider);
    }

    @Transactional
    public void removeRider(long id){SokoRider rider=riderRepo.findByIdForUpdate(id).orElseThrow(this::notFound);ownedStore(rider.getStoreId());requireRiderIdle(rider);rider.setActive(false);rider.setStatus("INACTIVE");rider.setAvailability("OFFLINE");riderRepo.save(rider);audit(rider,"SOKO_RIDER_REMOVED");}

    @Transactional
    public OrderDetail checkout(SokoRequests.Checkout request){
        return checkout(request,UUID.randomUUID().toString());
    }

    @Transactional
    public OrderDetail checkout(SokoRequests.Checkout request,String idempotencyKey){
        long customerUserId=userDao.getUserId();
        String cleanKey=StringUtils.trimToNull(idempotencyKey);
        if(cleanKey==null)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"A checkout idempotency key is required. Refresh your cart and try again.");
        if(cleanKey.length()>80||!cleanKey.matches("[A-Za-z0-9._:-]+"))throw invalid();
        if(request==null||request.storeId()==null||request.items()==null||request.items().isEmpty())throw invalid();
        if(request.items().size()>MAX_CHECKOUT_LINES)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"A Soko order can contain at most 25 product lines.");
        if(request.items().stream().anyMatch(line->line==null||line.productId()==null||line.quantity()<1||line.quantity()>MAX_CHECKOUT_QUANTITY))
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Choose a quantity between 1 and 100 for each product.");
        if(request.items().stream().map(line->line.productId()+":"+String.valueOf(line.variationId())).distinct().count()!=request.items().size())throw invalid();
        var existing=orderRepo.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(customerUserId,cleanKey);
        if(existing.isPresent())return existingCheckout(existing.get(),request);
        CheckoutDestination destination=resolveCheckoutDestination(request,customerUserId);
        SokoStore store=storeRepo.findByIdForCheckout(request.storeId()).filter(s->PUBLISHED.equals(s.getStatus())).orElseThrow(this::notFound);
        existing=orderRepo.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(customerUserId,cleanKey);
        if(existing.isPresent())return existingCheckout(existing.get(),request);
        if(store.getOwnerUserId()==customerUserId)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"You cannot place an order with your own Soko shop.");
        subscriptionEntitlements.requireActiveProductForOwner(store.getOwnerUserId(),SubscriptionProduct.SOKO);
        List<SokoOrder> pending=orderRepo.findPendingCheckoutForUpdate(customerUserId,store.getId(),PageRequest.of(0,1));
        if(!pending.isEmpty()){
            SokoOrder unpaid=pending.getFirst();
            if(unpaid.getReservationExpiresAt()!=null&&!unpaid.getReservationExpiresAt().isAfter(now()))expirePendingCheckout(unpaid);
            else throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"You already have an unpaid order from this shop. Complete or cancel it before starting another checkout.");
        }
        validateDelivery(store,request.deliveryMethod(),destination);
        validatePublishable(store);
        List<SokoRequests.CheckoutItem> checkoutLines=request.items().stream()
                .sorted(java.util.Comparator.comparingLong(SokoRequests.CheckoutItem::productId)
                        .thenComparing(line->line.variationId()==null?Long.MIN_VALUE:line.variationId()))
                .toList();
        List<SokoProduct> products=new ArrayList<>();List<SokoProductVariation> selectedVariations=new ArrayList<>();List<BigDecimal> unitPrices=new ArrayList<>(); BigDecimal subtotal=BigDecimal.ZERO;
        for(SokoRequests.CheckoutItem line:checkoutLines){
            SokoProduct p=productRepo.findByIdForUpdate(line.productId()).filter(x->x.getStoreId()==store.getId()&&PUBLISHED.equals(x.getStatus())).orElseThrow(this::notFound);
            if(!sameCurrency(p.getCurrency(),store.getCurrency()))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"This product has an invalid shop currency. The shop must correct it before checkout.");
            if(p.getStockQuantity()<line.quantity())throw new PMSCustomException(ResponseCode.INVALID_AMOUNT,"Insufficient stock for "+p.getName());
            List<SokoProductVariation> available=productVariationRepo.findAllByProductIdAndActiveTrueOrderByNameAscValueAsc(p.getId());SokoProductVariation selected=null;
            if(!available.isEmpty()){if(line.variationId()==null)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Choose a variation for "+p.getName()+".");selected=productVariationRepo.findForUpdate(line.variationId(),p.getId()).orElseThrow(this::notFound);if(selected.getStockQuantity()<line.quantity())throw new PMSCustomException(ResponseCode.INVALID_AMOUNT,"Insufficient stock for "+p.getName()+" ("+selected.getValue()+")");selected.setStockQuantity(selected.getStockQuantity()-line.quantity());productVariationRepo.save(selected);}else if(line.variationId()!=null)throw invalid();
            BigDecimal unitPrice=p.getPrice().add(selected==null?BigDecimal.ZERO:zero(selected.getPriceAdjustment()));p.setStockQuantity(p.getStockQuantity()-line.quantity()); if(p.getStockQuantity()==0)p.setStatus(OUT_OF_STOCK); productRepo.save(p); products.add(p);selectedVariations.add(selected);unitPrices.add(unitPrice);
            subtotal=subtotal.add(unitPrice.multiply(BigDecimal.valueOf(line.quantity())));
        }
        BigDecimal fee="DELIVERY".equalsIgnoreCase(request.deliveryMethod())?zero(store.getDeliveryFee()):BigDecimal.ZERO;
        SokoOrder o=new SokoOrder(); o.setOrderNumber("SOKO-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT)); o.setStoreId(store.getId()); o.setStoreNameSnapshot(store.getName());o.setStoreAddressSnapshot(store.getAddress());o.setStorePhoneSnapshot(store.getPhoneNumber());o.setStoreLatitudeSnapshot(store.getLatitude());o.setStoreLongitudeSnapshot(store.getLongitude()); o.setCustomerUserId(customerUserId); o.setCreatedBy(customerUserId); o.setCheckoutIdempotencyKey(cleanKey); o.setActive(true); o.setStatus("PENDING_PAYMENT"); o.setPaymentStatus("UNPAID");o.setRefundStatus("NOT_REQUIRED");o.setSettlementStatus("PENDING");o.setReservationExpiresAt(now().plusMinutes(reservationMinutes)); o.setDeliveryMethod(request.deliveryMethod().toUpperCase(Locale.ROOT)); o.setDeliveryAddress(destination.address());o.setDeliveryLatitude(destination.latitude());o.setDeliveryLongitude(destination.longitude()); o.setCustomerPhone(request.customerPhone()); o.setNotes(StringUtils.trimToNull(request.notes())); o.setDestinationUnitId(destination.unitId()); o.setSubtotal(subtotal); o.setDeliveryFee(fee); o.setTotal(subtotal.add(fee)); o.setCurrency(store.getCurrency());o.setPaymentAccountId(store.getPaymentAccountId());o.setPaymentChannel(accountDao.getAccountById(store.getPaymentAccountId()).getChannel().name()); o.setPlacedAt(now()); orderRepo.save(o);
        List<SokoOrderItem> items=new ArrayList<>();
        for(int i=0;i<products.size();i++){SokoProduct p=products.get(i);SokoProductVariation v=selectedVariations.get(i);BigDecimal unitPrice=unitPrices.get(i);int qty=checkoutLines.get(i).quantity();SokoOrderItem it=new SokoOrderItem();it.setOrderId(o.getId());it.setProductId(p.getId());it.setProductName(p.getName());if(v!=null){it.setVariationId(v.getId());it.setVariationName(v.getName());it.setVariationValue(v.getValue());}it.setUnit(p.getUnit());it.setUnitPrice(unitPrice);it.setQuantity(qty);it.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(qty)));it.setCreatedBy(userDao.getUserId());it.setActive(true);items.add(itemRepo.save(it));}
        PMSInvoice invoice=createInvoice(o,store,items);o.setInvoiceRef(invoice.getRef());orderRepo.save(o);notifyOrder(o,"Awaiting payment","");return detail(o,store,items,invoice.getId());
    }

    public Page<OrderDetail> myOrders(Pageable pageable){return hydrate(orderRepo.findAllByCustomerUserIdAndActiveTrue(userDao.getUserId(),bounded(pageable)));}
    @Transactional public Page<OrderDetail> merchantOrders(Pageable pageable){List<Long> ids=myStores().stream().map(SokoStore::getId).toList();if(ids.isEmpty())return Page.empty(bounded(pageable));expireRiderAssignments(ids);return hydrate(orderRepo.findAllByStoreIdInAndActiveTrue(ids,bounded(pageable)));}

    /**
     * Selects another ready receiving rail for an unpaid order. The order and
     * invoice are changed together so callbacks can never settle to a stale or
     * customer-supplied destination.
     */
    @Transactional("pmsDBTransactionManager")
    public void selectPendingPaymentDestination(PMSInvoice invoice,PaymentAccount account){
        if(invoice==null||account==null||!"SOKO".equals(invoice.getBillingType())
                ||StringUtils.isBlank(invoice.getRef())||!invoice.isActive()||invoice.isPaid()
                ||invoice.moneyPendingAmount().signum()<=0||account.getChannel()==null
                ||!account.isActive()||!account.isVerified()||account.getCategory()!=AccountCategory.MERCHANT
                ||!java.util.Objects.equals(account.getCreatedBy(),invoice.getPayToUserId()))
            throw new PaymentRequestException(ResponseCode.ACCOUNT_UNAUTHORIZED);
        SokoOrder order=orderRepo.findByInvoiceRefAndActiveTrue(invoice.getRef())
                .orElseThrow(()->new PaymentRequestException(ResponseCode.ACCOUNT_UNAUTHORIZED));
        SokoStore store=storeRepo.findByIdAndActiveTrue(order.getStoreId())
                .orElseThrow(()->new PaymentRequestException(ResponseCode.ACCOUNT_UNAUTHORIZED));
        boolean payable="PENDING_PAYMENT".equals(order.getStatus())&&"UNPAID".equals(order.getPaymentStatus())
                &&!order.isStockReleased()&&order.getReservationExpiresAt()!=null
                &&order.getReservationExpiresAt().isAfter(now());
        boolean sameSource=java.util.Objects.equals(order.getPaymentAccountId(),invoice.getPaymentAccountId())
                &&order.getCustomerUserId()==invoice.getBilledUserId()
                &&store.getOwnerUserId()==invoice.getPayToUserId();
        if(!payable||!sameSource)throw new PaymentRequestException(ResponseCode.ACCOUNT_UNAUTHORIZED);
        if(java.util.Objects.equals(order.getPaymentAccountId(),account.getId())
                &&account.getChannel().name().equals(order.getPaymentChannel()))return;
        order.setPaymentAccountId(account.getId());order.setPaymentChannel(account.getChannel().name());
        invoice.setPaymentAccountId(account.getId());
        orderRepo.save(order);invoiceDao.saveInvoice(invoice);audit(order,"SOKO_PAYMENT_DESTINATION_SELECTED");
    }
    public Page<SokoModels.RefundQueueItem> refundQueue(Pageable pageable,String requestedStatus){
        requireFinanceRole();
        List<String> statuses=StringUtils.isBlank(requestedStatus)?List.of("REQUESTED","PROCESSING","FAILED"):
                List.of(StringUtils.upperCase(requestedStatus.trim(),Locale.ROOT));
        if(!List.of("REQUESTED","PROCESSING","FAILED").containsAll(statuses))throw invalid();
        return orderRepo.findAllByRefundStatusInAndActiveTrue(statuses,bounded(pageable)).map(this::refundQueueItem);
    }

    public String deliveryCode(long orderId){SokoOrder o=orderRepo.findById(orderId).orElseThrow(this::notFound);if(o.getCustomerUserId()!=userDao.getUserId())throw forbidden();if(!"DELIVERY".equals(o.getDeliveryMethod())||!"DISPATCHED".equals(o.getStatus())||o.isDeliveryCodeVerified()||codeExpired(o)||o.getDeliveryCodeLockedAt()!=null)throw invalid();String code=decryptDeliveryCode(o);if(StringUtils.isBlank(code))throw invalid();return code;}

    public String pickupCode(long orderId){SokoOrder o=orderRepo.findById(orderId).filter(SokoOrder::isActive).orElseThrow(this::notFound);if(o.getCustomerUserId()!=userDao.getUserId())throw forbidden();if(!"PICKUP".equals(o.getDeliveryMethod())||!"READY_FOR_PICKUP".equals(o.getStatus())||o.isDeliveryCodeVerified()||codeExpired(o)||o.getDeliveryCodeLockedAt()!=null)throw invalid();String code=decryptDeliveryCode(o);if(StringUtils.isBlank(code))throw invalid();return code;}

    @Transactional public SokoModels.OrderAction uploadDeliveryProof(long orderId,MultipartFile proof)throws IOException{SokoOrder o=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);requireAssignedRiderForDoorstep(o);storeDeliveryProof(o,proof);return SokoModels.OrderAction.from(o);}
    public String deliveryProof(long orderId){SokoOrder o=orderRepo.findById(orderId).filter(x->x.isActive()).orElseThrow(this::notFound);SokoStore store=storeRepo.findByIdAndActiveTrue(o.getStoreId()).orElseThrow(this::notFound);if(o.getCustomerUserId()!=userDao.getUserId()&&store.getOwnerUserId()!=userDao.getUserId())throw forbidden();if(StringUtils.isBlank(o.getDeliveryProofReference()))throw notFound();return garageService.getPresignedUrlForStoredObject(o.getDeliveryProofReference());}

    @Transactional(noRollbackFor=PMSCustomException.class)
    public SokoModels.OrderAction confirmDelivery(long orderId,SokoRequests.DeliveryConfirmation request){SokoOrder o=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);requireAssignedRiderForDoorstep(o);confirmDeliveryOrder(o,request);return SokoModels.OrderAction.from(o);}

    @Transactional(noRollbackFor=PMSCustomException.class)
    public OrderDetail confirmPickup(long orderId,SokoRequests.PickupConfirmation request){
        SokoOrder o=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);
        SokoStore store=ownedStore(o.getStoreId());
        if("PICKUP".equals(o.getDeliveryMethod())&&"COMPLETED".equals(o.getStatus())&&o.isDeliveryCodeVerified())return detail(o,store,itemRepo.findAllByOrderIdAndActiveTrueOrderById(o.getId()));
        String code=decryptDeliveryCode(o);
        if(!"PICKUP".equals(o.getDeliveryMethod())||!"READY_FOR_PICKUP".equals(o.getStatus())||o.isDeliveryCodeVerified()||StringUtils.isBlank(code)||codeExpired(o)||o.getDeliveryCodeLockedAt()!=null)throw invalid();
        if(o.getDeliveryCodeAttempts()>=5){o.setDeliveryCodeLockedAt(now());orderRepo.save(o);throw forbidden();}
        o.setDeliveryCodeAttempts(o.getDeliveryCodeAttempts()+1);
        if(!constantTimeEquals(code,request.code())){if(o.getDeliveryCodeAttempts()>=5)o.setDeliveryCodeLockedAt(now());orderRepo.save(o);throw invalid();}
        o.setDeliveryCodeVerified(true);o.setDeliveryCode(null);o.setEncryptedDeliveryCode(null);
        o.setDeliveryRecipientName(StringUtils.left(StringUtils.trimToNull(request.recipientName()),160));o.setStatus("COMPLETED");o.setCompletedAt(now());
        orderRepo.save(o);audit(o,"SOKO_PICKUP_COMPLETED");notifyOrder(o,"Collected","Pickup was verified using the buyer's single-use code.");
        return detail(o,store,itemRepo.findAllByOrderIdAndActiveTrueOrderById(o.getId()));
    }

    @Transactional
    public OrderDetail transition(long orderId,String requested,SokoRequests.Dispatch dispatch){
        SokoOrder o=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);SokoStore store=storeRepo.findByIdAndActiveTrue(o.getStoreId()).orElseThrow(this::notFound);String next=requested.toUpperCase(Locale.ROOT);
        if(expireRiderAssignmentIfNeeded(o))return detail(o);
        boolean merchant=store.getOwnerUserId()==userDao.getUserId(),assignmentCreated=false;
        if("CANCELLED".equals(next))throw invalid();
        else {
            if(!merchant)throw forbidden();
            if(next.equals(o.getStatus()))return detail(o);
            if("DISPATCHED".equals(next)&&"DELIVERY_ASSIGNED".equals(o.getStatus())){
                if(!"DELIVERY".equals(o.getDeliveryMethod())||o.getRiderId()==null||o.getAssignedAt()==null)throw invalid();
                return detail(o);
            }
            switch(next){
                case "CONFIRMED"->{requireState(o,"PAID");o.setConfirmedAt(now());}
                case "PACKED"->requireState(o,"CONFIRMED");
                case "ASSIGNMENT_ACCEPTED"->throw new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS,
                        "Only the assigned rider can accept this delivery.");
                case "DISPATCHED"->{
                    if(!"PACKED".equals(o.getStatus()))throw new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS,
                            "The assigned rider must confirm collection from their private delivery link.");
                    if(!"DELIVERY".equals(o.getDeliveryMethod()))throw invalid();
                    assignAndRegisterDelivery(o,dispatch);next="DELIVERY_ASSIGNED";o.setAssignedAt(now());assignmentCreated=true;
                }
                case "READY_FOR_PICKUP"->{requireState(o,"PACKED");if(!"PICKUP".equals(o.getDeliveryMethod()))throw invalid();generatePickupCode(o);}
                default->throw invalid();
            }
            o.setStatus(next);
        }
        if(assignmentCreated)issueRiderAssignmentLink(o,store,false,true);
        orderRepo.save(o);audit(o,"SOKO_ORDER_"+next);notifyOrder(o,o.getStatus().replace('_',' '),"Your order progress was updated by the merchant.");return detail(o);
    }

    @Transactional public SokoModels.OrderAction acceptAssignment(long orderId){return acceptAssignedOrder(assignedOrder(orderId,null));}
    @Transactional public SokoModels.OrderAction confirmCollection(long orderId){return collectAssignedOrder(assignedOrder(orderId,null));}

    @Transactional(readOnly=true)
    public SokoModels.PublicRiderAssignment publicRiderAssignment(String token){
        SokoOrder order=publicAssignmentOrderForInspect(token);
        return publicRiderView(order,!terminalAssignmentReceipt(order));
    }

    @Transactional(noRollbackFor=PMSCustomException.class)
    public SokoModels.PublicRiderAssignment acceptPublicRiderAssignment(String token){
        SokoOrder order=publicAssignmentOrder(token,true);
        acceptAssignedOrder(order);
        return publicRiderView(order);
    }

    @Transactional(noRollbackFor=PMSCustomException.class)
    public SokoModels.PublicRiderAssignment collectPublicRiderAssignment(String token){
        SokoOrder order=publicAssignmentOrder(token,true);
        requirePersonalRiderAcceptance(order);
        collectAssignedOrder(order);
        return publicRiderView(order);
    }

    @Transactional(noRollbackFor=PMSCustomException.class)
    public SokoModels.PublicRiderAssignment uploadPublicRiderDeliveryProof(String token,MultipartFile proof)throws IOException{
        SokoOrder order=publicAssignmentOrder(token,true);requirePersonalRiderAcceptance(order);storeDeliveryProof(order,proof);return publicRiderView(order);
    }

    @Transactional(noRollbackFor=PMSCustomException.class)
    public SokoModels.PublicRiderAssignment confirmPublicRiderDelivery(String token,SokoRequests.DeliveryConfirmation request){
        SokoOrder order=publicAssignmentOrderForCompletion(token);
        if(terminalAssignmentReceipt(order))return publicRiderView(order,false);
        requirePersonalRiderAcceptance(order);
        confirmDeliveryOrder(order,request);return publicRiderView(order,false);
    }

    @Transactional(noRollbackFor=PMSCustomException.class)
    public SokoModels.RiderAssignmentDecision declinePublicRiderAssignment(String token,SokoRequests.RiderAssignmentDecline request){
        SokoOrder order=publicAssignmentOrderForDecline(token);
        if(order.getRiderAssignmentDeclinedAt()!=null)return new SokoModels.RiderAssignmentDecision(order.getOrderNumber(),"DECLINED");
        if(!List.of("DELIVERY_ASSIGNED","ASSIGNMENT_ACCEPTED").contains(order.getStatus()))throw invalid();
        String reason=request==null?null:StringUtils.left(StringUtils.trimToNull(request.reason()),500);
        releaseRider(order,false);order.setStatus("PACKED");order.setRiderAssignmentDeclinedAt(now());
        order.setRiderAssignmentDeclineReason(reason);revokeRiderAssignmentAccess(order);clearPriorAssignmentDisplay(order);
        orderRepo.save(order);audit(order,"SOKO_RIDER_ASSIGNMENT_DECLINED");
        notifyOrder(order,"Rider declined","Choose another available rider for this order.");
        return new SokoModels.RiderAssignmentDecision(order.getOrderNumber(),"DECLINED");
    }

    @Transactional(noRollbackFor=PMSCustomException.class)
    public SokoModels.AssignmentLinkDelivery resendRiderAssignmentLink(long orderId){
        SokoOrder order=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);
        SokoStore store=ownedStore(order.getStoreId());
        if(expireRiderAssignmentIfNeeded(order))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                "This assignment link expired. The rider was released; assign an available rider again.");
        if(!List.of("DELIVERY_ASSIGNED","ASSIGNMENT_ACCEPTED","DISPATCHED").contains(order.getStatus())||order.getRiderId()==null)throw invalid();
        SokoRider rider=riderRepo.findForUpdate(order.getRiderId(),order.getStoreId()).orElseThrow(this::notFound);
        if(!rider.isPhoneConfirmed()||!rider.isVerified()||!"ACTIVE".equals(rider.getStatus()))throw invalid();
        // Before the private-link workflow, a merchant could stamp acceptance on
        // an accountless rider's behalf. Never trust that legacy timestamp as
        // proof of the rider's personal consent when issuing the first bearer.
        if(order.getRiderAssignmentTokenHash()==null){
            order.setAssignmentAcceptedAt(null);
            if("ASSIGNMENT_ACCEPTED".equals(order.getStatus()))order.setStatus("DELIVERY_ASSIGNED");
        }
        issueRiderAssignmentLink(order,store,true,false);orderRepo.save(order);
        return new SokoModels.AssignmentLinkDelivery(order.getOrderNumber(),"LINK_QUEUED",
                order.getRiderAssignmentTokenExpiresAt(),"A new private assignment link was queued to the rider's confirmed phone.");
    }
    @Transactional public SokoModels.OrderAction failDelivery(long orderId,SokoRequests.DeliveryException request){SokoOrder o=deliveryExceptionOrder(orderId,null);if("PACKED".equals(o.getStatus())&&o.getDeliveryFailedAt()!=null||"DELIVERY_RETURN_REQUIRED".equals(o.getStatus()))return SokoModels.OrderAction.from(o);if(!List.of("DELIVERY_ASSIGNED","ASSIGNMENT_ACCEPTED","DISPATCHED").contains(o.getStatus()))throw invalid();boolean collected=o.getCollectedAt()!=null||"DISPATCHED".equals(o.getStatus());o.setStatus(collected?"DELIVERY_RETURN_REQUIRED":"PACKED");o.setDeliveryFailedAt(now());o.setDeliveryExceptionReason(request.reason().trim());clearDeliveryCode(o);revokeRiderAssignmentAccess(o);cancelDeliveryVisitor(o);if(!collected){releaseRider(o,false);clearPriorAssignmentDisplay(o);}orderRepo.save(o);audit(o,collected?"SOKO_DELIVERY_RETURN_REQUIRED":"SOKO_DELIVERY_REASSIGNMENT_REQUIRED");notifyOrder(o,collected?"Delivery failed — return required":"Delivery assignment failed — choose another rider",request.reason());return SokoModels.OrderAction.from(o);}
    @Transactional public SokoModels.OrderAction returnDelivery(long orderId,SokoRequests.DeliveryException request){SokoOrder o=deliveryExceptionOrder(orderId,null);if("RETURNED".equals(o.getStatus()))return SokoModels.OrderAction.from(o);if(!List.of("DELIVERY_FAILED","DELIVERY_RETURN_REQUIRED").contains(o.getStatus()))throw invalid();BigDecimal collected=authoritativeCollected(o);o.setStatus("RETURNED");o.setReturnedAt(now());o.setDeliveryExceptionReason(request.reason().trim());revokeRiderAssignmentAccess(o);cancelDeliveryVisitor(o);releaseRider(o,false);requestOutstandingRefund(o,collected);orderRepo.save(o);audit(o,"SOKO_DELIVERY_RETURNED");notifyOrder(o,"Order returned",request.reason());return SokoModels.OrderAction.from(o);}
    @Transactional public SokoModels.OrderAction returnAfterFinanceHold(long orderId,SokoRequests.DeliveryException request){
        SokoOrder o=financeHoldOrder(orderId);
        if("FINANCE_HOLD_RETURNED".equals(o.getStatus()))return SokoModels.OrderAction.from(o);
        releaseRider(o,false);clearDeliveryAuthorization(o);
        // A physical return ends rider custody, but groceries are not silently put back on sale.
        // The merchant can inspect them and explicitly adjust inventory if they are saleable.
        o.setReturnedAt(now());o.setDeliveryExceptionReason(request.reason().trim());o.setStatus("FINANCE_HOLD_RETURNED");
        orderRepo.save(o);audit(o,"SOKO_FINANCE_HOLD_RETURNED");notifyOrder(o,"Order returned to the shop",request.reason());return SokoModels.OrderAction.from(o);
    }
    @Transactional public OrderDetail reissueDeliveryCode(long orderId,SokoRequests.CodeReissue request){requireSuperAdmin();return requestDeliveryCodeRecovery(orderId,request.reason());}

    @Transactional
    public OrderDetail requestDeliveryCodeRecovery(long orderId,String supportReason){
        SokoOrder o=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);
        long actor=userDao.getUserId();boolean support=StringUtils.isNotBlank(supportReason);
        if(support)requireSuperAdmin();else if(o.getCustomerUserId()!=actor)throw forbidden();
        if(!handoverCodeRecoveryAllowed(o))throw invalid();
        var buyer=userDao.findById(o.getCustomerUserId()).filter(u->u.isActive()&&u.isEmailVerified()&&StringUtils.isNotBlank(u.getEmail()))
                .orElseThrow(()->new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Verify the buyer's email before recovering a handover code."));
        ZonedDateTime timestamp=now();
        if(o.getDeliveryRecoveryWindowStartedAt()==null||o.getDeliveryRecoveryWindowStartedAt().plusHours(24).isBefore(timestamp)){
            o.setDeliveryRecoveryWindowStartedAt(timestamp);o.setDeliveryRecoveryRequestCount(0);
        }
        if(o.getDeliveryRecoveryRequestedAt()!=null&&o.getDeliveryRecoveryRequestedAt().plusSeconds(Math.max(30,deliveryRecoveryCooldownSeconds)).isAfter(timestamp))
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"A verification code was just sent. Wait one minute before requesting another.");
        if(o.getDeliveryRecoveryRequestCount()>=Math.max(1,deliveryRecoveryMaxRequestsPerDay))
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Handover-code recovery has reached today's limit. Contact support and try again after 24 hours.");
        String otp=String.format(Locale.ROOT,"%06d",SECURE_RANDOM.nextInt(1_000_000));
        // A recovery request means the original code can no longer be trusted.
        // Invalidate it before sending the identity challenge; only the buyer can
        // generate a replacement by completing the fresh-contact OTP check.
        clearDeliveryCode(o);
        o.setDeliveryRecoveryOtp(encryptionService.encrypt(otp));o.setDeliveryRecoveryOtpExpiresAt(timestamp.plusMinutes(Math.max(5,Math.min(deliveryRecoveryOtpValidMinutes,30))));
        o.setDeliveryRecoveryRequestedAt(timestamp);o.setDeliveryRecoveryRequestCount(o.getDeliveryRecoveryRequestCount()+1);o.setDeliveryRecoveryOtpAttempts(0);
        o.setDeliveryRecoveryRequestedBy(actor);o.setDeliveryRecoverySupportReason(StringUtils.left(StringUtils.trimToNull(supportReason),1000));
        orderRepo.save(o);audit(o,support?"SOKO_DELIVERY_RECOVERY_ASSISTED":"SOKO_DELIVERY_RECOVERY_REQUESTED");
        String body=String.format(i18n.getLocalizedMessage(NotificationType.SOKO_DELIVERY_RECOVERY_EMAIL.getBody()),HtmlUtils.htmlEscape(o.getOrderNumber()),otp,o.getDeliveryRecoveryOtpExpiresAt());
        notificationService.queueNotification(new NotificationDTO(body,buyer.getEmail(),NotificationType.SOKO_DELIVERY_RECOVERY_EMAIL));
        return detail(o);
    }

    @Transactional(noRollbackFor=PMSCustomException.class)
    public OrderDetail confirmDeliveryCodeRecovery(long orderId,SokoRequests.DeliveryCodeRecoveryConfirm request){
        SokoOrder o=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);
        if(o.getCustomerUserId()!=userDao.getUserId())throw forbidden();
        if(!handoverCodeRecoveryAllowed(o)||o.getDeliveryRecoveryOtp()==null||o.getDeliveryRecoveryOtpExpiresAt()==null||!o.getDeliveryRecoveryOtpExpiresAt().isAfter(now()))throw invalid();
        if(o.getDeliveryRecoveryOtpAttempts()>=5)throw forbidden();
        o.setDeliveryRecoveryOtpAttempts(o.getDeliveryRecoveryOtpAttempts()+1);
        String expected=encryptionService.decrypt(o.getDeliveryRecoveryOtp()).decryptedValue();
        if(!constantTimeEquals(expected,request.otp())){orderRepo.save(o);throw invalid();}
        if("PICKUP".equals(o.getDeliveryMethod()))generatePickupCode(o);else generateDeliveryCode(o);o.setDeliveryCodeReissuedAt(now());o.setDeliveryCodeReissuedBy(userDao.getUserId());o.setDeliveryCodeReissueReason("Buyer identity confirmed using a fresh email OTP.");
        o.setDeliveryRecoveryOtp(null);o.setDeliveryRecoveryOtpExpiresAt(null);o.setDeliveryRecoveryCompletedAt(now());
        orderRepo.save(o);audit(o,"SOKO_DELIVERY_CODE_RECOVERED");return detail(o);
    }

    @Transactional
    public void completePaidInvoice(String invoiceRef,String providerReference){
        applyInvoicePayment(invoiceRef,providerReference);
    }

    /** Reconciles from the locked invoice instead of trusting callback order or amount. */
    @Transactional
    public void applyInvoicePayment(String invoiceRef,String providerReference){
        orderRepo.findByInvoiceRefAndActiveTrue(invoiceRef).ifPresent(o->{
            String previousStatus=o.getStatus(),previousPaymentStatus=o.getPaymentStatus(),previousRefundStatus=o.getRefundStatus();
            BigDecimal previousRefundRequestedAmount=o.getRefundRequestedAmount();
            BigDecimal collected=authoritativeCollected(o);
            if(collected.signum()<=0)return;
            boolean fullyPaid=collected.compareTo(zero(o.getTotal()))>=0;
            BigDecimal reversals=zero(o.getReversedAmount()).add(zero(o.getChargedBackAmount()));
            BigDecimal refunds=zero(o.getRefundedAmount());
            if(reversals.signum()>0)o.setPaymentStatus(reversals.add(refunds).compareTo(collected)>=0?"REVERSED":"PARTIALLY_REVERSED");
            else if(refunds.signum()>0)o.setPaymentStatus(refunds.compareTo(collected)>=0?"REFUNDED":"PARTIALLY_REFUNDED");
            else o.setPaymentStatus(fullyPaid?"PAID":"PARTIALLY_PAID");
            if(List.of("CANCELLED","EXPIRED","RETURNED","REFUNDED","PAYMENT_REVERSED","DELIVERY_RETURN_REQUIRED",
                    "FINANCE_HOLD","FINANCE_HOLD_RETURN_REQUIRED","FINANCE_HOLD_RETURNED").contains(o.getStatus()))requestOutstandingRefund(o,collected);
            else if(fullyPaid&&confirmedFinancialAdjustments(o).signum()==0&&"PENDING_PAYMENT".equals(o.getStatus()))o.setStatus("PAID");
            if(java.util.Objects.equals(previousStatus,o.getStatus())
                    &&java.util.Objects.equals(previousPaymentStatus,o.getPaymentStatus())
                    &&java.util.Objects.equals(previousRefundStatus,o.getRefundStatus())
                    &&sameAmount(previousRefundRequestedAmount,o.getRefundRequestedAmount()))return;
            orderRepo.save(o);audit(o,fullyPaid?"SOKO_PAYMENT_CONFIRMED":"SOKO_PARTIAL_PAYMENT_APPLIED");
            notifyOrder(o,fullyPaid?"Payment confirmed":"Partial payment received","");
        });
    }

    @Transactional
    public void completeFinanceOperation(String invoiceRef,String type,BigDecimal amount,String providerReference){
        orderRepo.findByInvoiceRefAndActiveTrue(invoiceRef).ifPresent(o->{
            String operation=StringUtils.upperCase(StringUtils.trimToEmpty(type),Locale.ROOT);
            if(!List.of("REFUND","REVERSAL","CHARGEBACK").contains(operation)||amount==null||amount.signum()<=0)throw invalid();
            String reference=StringUtils.left(StringUtils.trimToNull(providerReference),120);
            if(reference==null)throw invalid();
            if(financeOperationReplay(o.getId(),operation,reference,amount))return;
            BigDecimal collected=authoritativeCollected(o);
            BigDecimal adjusted=confirmedFinancialAdjustments(o);
            if(adjusted.add(amount).compareTo(collected)>0)
                throw new PMSCustomException(ResponseCode.INVALID_AMOUNT,"The confirmed finance total cannot exceed money collected for this order.");
            if("REFUND".equals(operation)){
                o.setRefundedAmount(zero(o.getRefundedAmount()).add(amount));o.setRefundReference(reference);o.setRefundStatus("CONFIRMED");o.setRefundRequestedAmount(null);
            }else if("REVERSAL".equals(operation)){
                o.setReversedAmount(zero(o.getReversedAmount()).add(amount));o.setReversalReference(reference);o.setReversalStatus("CONFIRMED");o.setSettlementStatus("BLOCKED");
            }else{
                o.setChargedBackAmount(zero(o.getChargedBackAmount()).add(amount));o.setChargebackReference(reference);o.setChargebackStatus("CONFIRMED");o.setSettlementStatus("BLOCKED");
            }
            boolean finality=confirmedFinancialAdjustments(o).compareTo(collected)>=0;
            if(finality){
                String terminal=zero(o.getReversedAmount()).add(zero(o.getChargedBackAmount())).signum()>0?"PAYMENT_REVERSED":"REFUNDED";
                o.setPaymentStatus("PAYMENT_REVERSED".equals(terminal)?"REVERSED":"REFUNDED");
                applyFinancialFinality(o,terminal);
            }else{
                o.setPaymentStatus("REFUND".equals(operation)?"PARTIALLY_REFUNDED":"PARTIALLY_REVERSED");
                applyFinancialReductionHold(o,collected);
            }
            orderRepo.save(o);financeOperationRepo.save(new SokoFinanceOperation(o.getId(),operation,reference,amount));audit(o,"SOKO_PAYMENT_"+operation);
            notifyOrder(o,finality?"Payment adjustment completed":"Partial payment adjustment completed",o.getCurrency()+" "+amount.toPlainString()+" was confirmed.");
        });
    }

    @Transactional public OrderDetail cancel(long orderId,SokoRequests.Cancellation request){SokoOrder o=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);SokoStore s=storeRepo.findByIdAndActiveTrue(o.getStoreId()).orElseThrow(this::notFound);boolean allowed=o.getCustomerUserId()==userDao.getUserId()||s.getOwnerUserId()==userDao.getUserId();if(!allowed)throw forbidden();if("CANCELLED".equals(o.getStatus()))return detail(o);if(!List.of("PENDING_PAYMENT","PAID","CONFIRMED").contains(o.getStatus()))throw invalid();BigDecimal collected=authoritativeCollected(o);if(collected.signum()>0)o.setPaymentStatus(collected.compareTo(zero(o.getTotal()))>=0?"PAID":"PARTIALLY_PAID");restoreStock(o);revokeRiderAssignmentAccess(o);releaseRider(o,false);o.setCancellationReason(request.reason().trim());o.setCancelledAt(now());o.setStatus("CANCELLED");requestOutstandingRefund(o,collected);orderRepo.save(o);audit(o,"SOKO_ORDER_CANCELLED");notifyOrder(o,"Cancelled","");return detail(o);}
    @Transactional public OrderDetail finance(long orderId,SokoRequests.FinanceUpdate r){requireFinanceRole();SokoOrder o=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);
        if(r.type()==SokoRequests.FinanceType.REFUND)return updateManualRefund(o,r);
        BigDecimal collected=authoritativeCollected(o),refunded=zero(o.getRefundedAmount());
        if(!"COMPLETED".equals(o.getStatus())||r.amount().compareTo(collected.subtract(refunded).max(BigDecimal.ZERO))>0)throw invalid();
        String reference=StringUtils.left(StringUtils.trimToNull(r.providerReference()),120);
        if(r.status()==SokoRequests.FinanceStatus.CONFIRMED&&reference==null)throw invalid();
        if(java.util.Objects.equals(o.getSettlementStatus(),r.status().name())&&zero(o.getSettledAmount()).compareTo(r.amount())==0&&java.util.Objects.equals(o.getSettlementReference(),reference))return detail(o);
        o.setSettlementStatus(r.status().name());o.setSettledAmount(r.amount());o.setSettlementReference(reference);orderRepo.save(o);audit(o,"SOKO_FINANCE_RECORD_SETTLEMENT_"+r.status().name());notifyFinanceRecord(o,false);return detail(o);}
    @Scheduled(fixedDelayString="${soko.reservation-expiry-scan-ms:300000}")
    @Transactional public void expireReservations(){
        Pageable batch=PageRequest.of(0,100,Sort.by("reservationExpiresAt"));
        for(int scan=0;scan<10;scan++){
            List<SokoOrder> expired=orderRepo.findExpiredReservations(now(),batch);
            expired.forEach(this::expirePendingCheckout);
            if(expired.size()<100)break;
        }
    }

    @Scheduled(fixedDelayString="${soko.rider-assignment-expiry-scan-ms:300000}")
    @Transactional public void expireRiderAssignments(){
        Pageable batch=PageRequest.of(0,100,Sort.by("riderAssignmentTokenExpiresAt"));
        for(int scan=0;scan<10;scan++){
            List<SokoOrder> expired=orderRepo.findExpiredRiderAssignmentsForUpdate(now(),batch);
            expired.forEach(this::expireRiderAssignmentIfNeeded);
            if(expired.size()<100)break;
        }
    }

    private void expirePendingCheckout(SokoOrder o){BigDecimal collected=authoritativeCollected(o);if(collected.signum()>0)o.setPaymentStatus(collected.compareTo(zero(o.getTotal()))>=0?"PAID":"PARTIALLY_PAID");restoreStock(o);o.setStatus("EXPIRED");o.setCancelledAt(now());o.setCancellationReason("Payment reservation expired");requestOutstandingRefund(o,collected);orderRepo.save(o);audit(o,"SOKO_RESERVATION_EXPIRED");notifyOrder(o,"Payment reservation expired","");}

    private void applyStore(SokoStore s,SokoRequests.StoreUpsert r){s.setName(r.name().trim());s.setDescription(StringUtils.trimToNull(r.description()));s.setPhoneNumber(StringUtils.trimToNull(r.phoneNumber()));s.setAddress(StringUtils.trimToNull(r.address()));s.setLatitude(r.latitude());s.setLongitude(r.longitude());s.setServiceRadiusKm(r.serviceRadiusKm()==null?BigDecimal.valueOf(25):r.serviceRadiusKm());s.setPickupEnabled(r.pickupEnabled());s.setDeliveryEnabled(r.deliveryEnabled());s.setDeliveryFee(zero(r.deliveryFee()));s.setCurrency(org.pms.silverocean.service.payment.money.MonetaryPolicy.currency(r.currency()));s.setPaymentAccountId(r.paymentAccountId());if(!s.isPickupEnabled()&&!s.isDeliveryEnabled())throw invalid();if((s.getLatitude()==null)!=(s.getLongitude()==null)||s.getLatitude()!=null&&(!validLatitude(s.getLatitude())||!validLongitude(s.getLongitude())))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Choose a valid shop location on the map.");if(s.getServiceRadiusKm()==null||s.getServiceRadiusKm().compareTo(BigDecimal.ONE)<0||s.getServiceRadiusKm().compareTo(BigDecimal.valueOf(100))>0)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"The service radius must be between 1 km and 100 km.");}
    private void applyProduct(SokoProduct p,SokoRequests.ProductUpsert r,SokoStore store){validateAllowedGrocery(r.name(),r.description());p.setName(r.name().trim());p.setDescription(StringUtils.trimToNull(r.description()));p.setCategory(SokoGroceryCategory.normalize(r.category()));p.setUnit(r.unit().trim());p.setPrice(r.price());p.setCurrency(store.getCurrency());if(r.variations()!=null||p.getId()==0)p.setStockQuantity(r.variations()!=null&&!r.variations().isEmpty()?r.variations().stream().mapToInt(v->v.stockQuantity()==null?0:v.stockQuantity()).sum():r.stockQuantity());p.setImageUrl(safeLegacyImageUrl(r.imageUrl()));if(List.of(OUT_OF_STOCK,"PAUSED").contains(p.getStatus())&&p.getStockQuantity()>0)p.setStatus(DRAFT);}
    private void validateAllowedGrocery(String name,String description){String text=StringUtils.defaultString(name)+" "+StringUtils.defaultString(description);if(RESTRICTED_PRODUCT.matcher(text).find())throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Soko is for groceries only. Alcohol and restricted products cannot be listed.");}
    private void syncVariations(SokoProduct p,List<SokoRequests.ProductVariation> requested){List<SokoProductVariation> old=productVariationRepo.findAllByProductIdAndActiveTrueOrderByNameAscValueAsc(p.getId());old.forEach(v->v.setActive(false));if(!old.isEmpty())productVariationRepo.saveAll(old);List<SokoProductVariation> saved=new ArrayList<>();Set<String> keys=new java.util.HashSet<>();for(SokoRequests.ProductVariation r:requested==null?List.<SokoRequests.ProductVariation>of():requested){String key=(r.name().trim()+"\u0000"+r.value().trim()).toLowerCase(Locale.ROOT);if(!keys.add(key))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Product variation names and values must be unique.");SokoProductVariation v=new SokoProductVariation();v.setProductId(p.getId());v.setName(r.name().trim());v.setValue(r.value().trim());v.setPriceAdjustment(zero(r.priceAdjustment()));v.setStockQuantity(r.stockQuantity());v.setCreatedBy(userDao.getUserId());v.setActive(true);saved.add(productVariationRepo.save(v));}try{p.setVariationsJson(saved.isEmpty()?null:JSON.writeValueAsString(saved.stream().map(v->new VariationView(v.getId(),v.getName(),v.getValue(),v.getPriceAdjustment(),v.getStockQuantity())).toList()));}catch(Exception e){throw invalid();}}
    private record VariationView(long id,String name,String value,BigDecimal priceAdjustment,int stockQuantity){}
    private void hydrateVariationStock(List<SokoProduct> products) {
        if(products.isEmpty())return;
        Map<Long,List<SokoProductVariation>> current=productVariationRepo.findAllByProductIdInAndActiveTrueOrderByProductIdAscNameAscValueAsc(products.stream().map(SokoProduct::getId).toList()).stream().collect(Collectors.groupingBy(SokoProductVariation::getProductId));
        for(SokoProduct p:products){try{List<SokoProductVariation> rows=current.getOrDefault(p.getId(),List.of());p.setVariationsJson(rows.isEmpty()?null:JSON.writeValueAsString(rows.stream().map(v->new VariationView(v.getId(),v.getName(),v.getValue(),zero(v.getPriceAdjustment()),v.getStockQuantity())).toList()));}catch(Exception invalidSnapshot){throw invalid();}}
    }
    private Map<Long,List<String>> imageUrlsByProduct(List<Long> ids){if(ids.isEmpty())return Map.of();return productImageRepo.findAllByProductIdInAndActiveTrueOrderByProductIdAscDisplayOrderAsc(ids).stream().collect(Collectors.groupingBy(SokoProductImage::getProductId,Collectors.mapping(i->garageService.getPresignedUrlForStoredObject(i.getFileRef()),Collectors.toList())));}
    private List<String> legacyImage(SokoProduct product){return StringUtils.isBlank(product.getImageUrl())?List.of():List.of(product.getImageUrl());}
    private String safeLegacyImageUrl(String value){String clean=StringUtils.trimToNull(value);if(clean==null)return null;try{URI uri=URI.create(clean);if(!"https".equalsIgnoreCase(uri.getScheme())||StringUtils.isBlank(uri.getHost()))throw invalid();return clean;}catch(IllegalArgumentException ex){throw invalid();}}
    private byte[] validatedImageBytes(MultipartFile image)throws IOException{if(image==null||image.isEmpty())throw new PMSCustomException(ResponseCode.INVALID_IMAGE);if(image.getSize()>8L*1024*1024)throw new PMSCustomException(ResponseCode.MAX_UPLOAD_SIZE_EXCEEDED);String type=StringUtils.defaultString(image.getContentType()).toLowerCase(Locale.ROOT);if(!Set.of("image/jpeg","image/png","image/webp").contains(type))throw new PMSCustomException(ResponseCode.INVALID_IMAGE);byte[] b=image.getBytes();boolean valid=switch(type){case "image/jpeg"->b.length>3&&(b[0]&255)==0xff&&(b[1]&255)==0xd8;case "image/png"->b.length>8&&(b[0]&255)==0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G';case "image/webp"->b.length>12&&b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'&&b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P';default->false;};if(!valid)throw new PMSCustomException(ResponseCode.INVALID_IMAGE);return b;}
    private String imageExtension(String type){return switch(type){case "image/png"->"png";case "image/webp"->"webp";default->"jpg";};}
    private record PendingImage(byte[] bytes,String contentType,String extension){}
    private void applyRider(SokoRider rider,SokoRequests.RiderUpsert r,String normalizedPhone){String type=r.riderType().trim().toUpperCase(Locale.ROOT);if(!List.of("INDIVIDUAL","DELIVERY_COMPANY").contains(type))throw invalid();rider.setRiderType(type);rider.setDisplayName(r.displayName().trim());rider.setPhoneNumber(normalizedPhone);rider.setNationalIdNumber(r.nationalIdNumber().trim().toUpperCase(Locale.ROOT));rider.setEmail(StringUtils.lowerCase(StringUtils.trimToNull(r.email())));rider.setVehicleType(StringUtils.trimToNull(r.vehicleType()));rider.setVehiclePlate(StringUtils.trimToNull(r.vehiclePlate()));rider.setNotes(StringUtils.trimToNull(r.notes()));}
    private void linkRiderUser(SokoRider rider){
        // A merchant-supplied email is profile metadata, not proof that the rider
        // owns that account. Link self-service access only after the rider proves
        // ownership of the normalized phone using the SMS challenge.
        if(!rider.isPhoneConfirmed()){rider.setUserId(null);return;}
        rider.setUserId(findRiderUserByPhone(rider.getPhoneNumber())
                .filter(org.pms.silverocean.database.pms.entities.Users::isActive)
                .filter(org.pms.silverocean.database.pms.entities.Users::isVerified)
                .filter(org.pms.silverocean.database.pms.entities.Users::isEmailVerified)
                .filter(user->org.pms.silverocean.service.kyc.AccountStatus.ACTIVE.name().equals(user.getAccountStatus()))
                .map(org.pms.silverocean.database.pms.entities.Users::getId).orElse(null));
    }
    private java.util.Optional<org.pms.silverocean.database.pms.entities.Users> findRiderUserByPhone(String phone){
        String normalized=normalizeStoredPhone(phone);
        var user=userDao.findByPhone(normalized);
        if(user.isEmpty()&&normalized.startsWith("+254")&&normalized.length()==13)user=userDao.findByPhone("0"+normalized.substring(4));
        return user;
    }
    private String normalizeRiderPhone(String raw){
        String compact=StringUtils.defaultString(raw).trim().replaceAll("[\\s()\\-]","");
        if(compact.matches("254\\d{9}"))compact="+"+compact;
        String normalized=PMSUtils.getLocalisedPhoneNumber(compact);
        if(StringUtils.isBlank(normalized))throw new PMSCustomException(ResponseCode.INVALID_PHONENUMBER,
                "Enter a valid phone number, for example 0712 345 678 or +254 712 345 678.");
        return normalized;
    }
    private String normalizeStoredPhone(String raw){
        try{return normalizeRiderPhone(raw);}catch(PMSCustomException ignored){return StringUtils.defaultString(raw).trim();}
    }
    private boolean hasDuplicateRiderPhone(long storeId,String normalizedPhone,Long excludedId){
        return riderRepo.findAllByStoreIdAndActiveTrueOrderByDisplayName(storeId).stream()
                .filter(r->excludedId==null||r.getId()!=excludedId)
                .anyMatch(r->normalizedPhone.equals(normalizeStoredPhone(r.getPhoneNumber())));
    }
    private void resetRiderPhoneConfirmation(SokoRider rider){
        rider.setPhoneConfirmed(false);rider.setPhoneConfirmationStatus("PENDING");rider.setPhoneConfirmationOtp(null);
        rider.setPhoneConfirmationExpiresAt(null);rider.setPhoneConfirmationRequestedAt(null);rider.setPhoneConfirmationConfirmedAt(null);
        rider.setPhoneConfirmationAttempts(0);
    }
    private void lockRiderPhoneConfirmation(SokoRider rider){
        rider.setPhoneConfirmationStatus("LOCKED");rider.setPhoneConfirmationOtp(null);rider.setPhoneConfirmationExpiresAt(null);
    }
    private SokoModels.RiderVerificationResult issueRiderPhoneConfirmation(SokoRider rider,boolean respectCooldown){
        if(rider.isPhoneConfirmed())return riderVerificationResult(rider,"CONFIRMED","This rider's phone number is already confirmed.");
        ZonedDateTime timestamp=now();
        long cooldown=Math.max(30,Math.min(riderPhoneConfirmationCooldownSeconds,600));
        if(respectCooldown&&rider.getPhoneConfirmationRequestedAt()!=null
                &&rider.getPhoneConfirmationRequestedAt().plusSeconds(cooldown).isAfter(timestamp)){
            if(rider.getPhoneConfirmationOtp()!=null&&rider.getPhoneConfirmationExpiresAt()!=null&&rider.getPhoneConfirmationExpiresAt().isAfter(timestamp))
                return riderVerificationResult(rider,"CODE_ALREADY_QUEUED","A confirmation code was sent recently. Use that code or wait before requesting another.");
            throw new PMSCustomException(ResponseCode.OTP_RESEND_TOO_SOON,"Wait one minute before requesting another rider confirmation code.");
        }
        if(rider.getPhoneConfirmationWindowStartedAt()==null||rider.getPhoneConfirmationWindowStartedAt().plusHours(24).isBefore(timestamp)){
            rider.setPhoneConfirmationWindowStartedAt(timestamp);rider.setPhoneConfirmationRequestCount(0);
        }
        int maxRequests=Math.max(3,Math.min(riderPhoneConfirmationMaxRequestsPerDay,10));
        if(rider.getPhoneConfirmationRequestCount()>=maxRequests)throw new PMSCustomException(ResponseCode.OTP_RESEND_TOO_SOON,
                "Too many rider confirmation codes were requested. Try again later.");
        long validMinutes=Math.max(5,Math.min(riderPhoneConfirmationValidMinutes,30));
        String code=String.format(Locale.ROOT,"%06d",SECURE_RANDOM.nextInt(1_000_000));
        rider.setPhoneConfirmed(false);rider.setPhoneConfirmationStatus("CODE_QUEUED");rider.setPhoneConfirmationOtp(encryptionService.encrypt(code));
        rider.setPhoneConfirmationExpiresAt(timestamp.plusMinutes(validMinutes));rider.setPhoneConfirmationRequestedAt(timestamp);
        rider.setPhoneConfirmationRequestCount(rider.getPhoneConfirmationRequestCount()+1);rider.setPhoneConfirmationAttempts(0);
        rider= riderRepo.save(rider);
        String body=String.format(i18n.getLocalizedMessage(NotificationType.SOKO_RIDER_CONFIRMATION_SMS.getBody()),code,validMinutes);
        notificationService.queueNotification(new NotificationDTO(body,rider.getPhoneNumber(),NotificationType.SOKO_RIDER_CONFIRMATION_SMS));
        audit(rider,"SOKO_RIDER_PHONE_CONFIRMATION_SENT");
        return riderVerificationResult(rider,"CODE_QUEUED","A confirmation code was queued to the rider's phone. Enter it to activate the rider.");
    }
    private SokoModels.RiderVerificationResult riderVerificationResult(SokoRider rider,String status,String message){
        return new SokoModels.RiderVerificationResult(rider,status,message);
    }
    private ProfileType profileType(long userId){return userDao.findById(userId).map(u->{try{return ProfileType.valueOf(u.getProfileType());}catch(Exception ignored){return ProfileType.INDIVIDUAL;}}).orElse(ProfileType.INDIVIDUAL);}
    private List<SystemDestination> authorizedSystemDestinations(long userId){
        Map<Long,SystemDestination> destinations=new java.util.LinkedHashMap<>();
        for(SokoDeliveryDestinationProjection row:unitRepo.findAcceptedTenancyDeliveryDestinations(userId)){
            parseSystemDestination(row,"TENANCY").ifPresent(destination->destinations.putIfAbsent(destination.unitId(),destination));
        }
        for(SokoDeliveryDestinationProjection row:unitRepo.findHomeownerDeliveryDestinations(userId)){
            // Active ownership is the stronger source if the same unit is also an accepted tenancy.
            parseSystemDestination(row,"HOMEOWNERSHIP").ifPresent(destination->destinations.put(destination.unitId(),destination));
        }
        return destinations.values().stream().sorted(java.util.Comparator.comparing(SystemDestination::label,
                String.CASE_INSENSITIVE_ORDER).thenComparingLong(SystemDestination::unitId)).toList();
    }
    private java.util.Optional<SystemDestination> parseSystemDestination(SokoDeliveryDestinationProjection row,String source){
        String address=StringUtils.trimToNull(row.getAddress()),unitRef=StringUtils.trimToNull(row.getUnitRef());
        String propertyName=StringUtils.trimToNull(row.getPropertyName()),mapLocation=StringUtils.trimToNull(row.getMapLocation());
        if(address==null||propertyName==null||mapLocation==null)return java.util.Optional.empty();
        String[] coordinates=mapLocation.split(",",-1);if(coordinates.length!=2)return java.util.Optional.empty();
        try{
            Double latitude=Double.valueOf(coordinates[0].trim()),longitude=Double.valueOf(coordinates[1].trim());
            if(!validLatitude(latitude)||!validLongitude(longitude))return java.util.Optional.empty();
            String label=unitRef==null?propertyName:propertyName+" — "+unitRef;
            String fullAddress=unitRef==null?address:address+", "+unitRef;
            return java.util.Optional.of(new SystemDestination(row.getUnitId(),row.getPropertyId(),label,fullAddress,
                    latitude,longitude,source));
        }catch(NumberFormatException invalidCoordinates){return java.util.Optional.empty();}
    }
    private CheckoutDestination resolveCheckoutDestination(SokoRequests.Checkout request,long customerUserId){
        String method=StringUtils.upperCase(StringUtils.trimToEmpty(request.deliveryMethod()),Locale.ROOT);
        if("PICKUP".equals(method)){
            if(request.destinationUnitId()!=null)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                    "A SlickHood delivery address cannot be used for pickup. Remove it or choose delivery.");
            return new CheckoutDestination(null,null,null,null);
        }
        if(!"DELIVERY".equals(method))throw invalid();
        if(request.destinationUnitId()==null)return new CheckoutDestination(StringUtils.trimToNull(request.deliveryAddress()),
                request.deliveryLatitude(),request.deliveryLongitude(),null);
        SystemDestination saved=authorizedSystemDestinations(customerUserId).stream()
                .filter(destination->destination.unitId()==request.destinationUnitId()).findFirst()
                .orElseThrow(()->new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                        "Choose a delivery address linked to your SlickHood account, or ship to a different location."));
        return new CheckoutDestination(saved.address(),saved.latitude(),saved.longitude(),saved.unitId());
    }
    private void validatePublishable(SokoStore s){validateFulfilmentLocation(s);if(s.getPaymentAccountId()==null)throw new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND);PaymentAccount a=accountDao.getAccountByIdAndCreatedBy(s.getPaymentAccountId(),s.getOwnerUserId());if(!a.isVerified()||!a.isActive()||a.getCategory()!=AccountCategory.MERCHANT||a.getChannel()==null)throw new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND);}
    private void validateFulfilmentLocation(SokoStore s){if((s.isPickupEnabled()||s.isDeliveryEnabled())&&(StringUtils.isBlank(s.getAddress())||!validLatitude(s.getLatitude())||!validLongitude(s.getLongitude())))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Add the shop address and choose its location on the map before publishing.");if(s.isDeliveryEnabled()&&(s.getServiceRadiusKm()==null||s.getServiceRadiusKm().compareTo(BigDecimal.ONE)<0||s.getServiceRadiusKm().compareTo(BigDecimal.valueOf(100))>0))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Set a delivery radius between 1 km and 100 km before publishing.");}
    private void validateDelivery(SokoStore s,String deliveryMethod,CheckoutDestination destination){
        if("DELIVERY".equalsIgnoreCase(deliveryMethod)){
            if(!s.isDeliveryEnabled())throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"This shop does not offer delivery. Choose pickup or another shop.");
            if(StringUtils.isBlank(destination.address())||destination.latitude()==null||destination.longitude()==null)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Enter the delivery address and choose its location on the map.");
            if(!validLatitude(destination.latitude())||!validLongitude(destination.longitude()))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Choose a valid delivery location on the map.");
            if(!validLatitude(s.getLatitude())||!validLongitude(s.getLongitude())||s.getServiceRadiusKm()==null||s.getServiceRadiusKm().signum()<=0)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"This shop has not configured a delivery area. Choose pickup or contact the shop.");
            double distance=exactDistanceKm(destination.latitude(),destination.longitude(),s.getLatitude(),s.getLongitude());
            if(distance>s.getServiceRadiusKm().doubleValue()+0.000001)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"This location is outside the shop's "+s.getServiceRadiusKm().stripTrailingZeros().toPlainString()+" km delivery area. Choose pickup or another shop.");
        }else if("PICKUP".equalsIgnoreCase(deliveryMethod)){if(!s.isPickupEnabled())throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"This shop does not offer pickup. Choose delivery or another shop.");if(StringUtils.isBlank(s.getAddress())||!validLatitude(s.getLatitude())||!validLongitude(s.getLongitude()))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"This shop has not configured a pickup location. Choose another shop.");}else throw invalid();
    }
    private OrderDetail existingCheckout(SokoOrder existing,SokoRequests.Checkout request){
        List<SokoOrderItem> savedItems=itemRepo.findAllByOrderIdAndActiveTrueOrderById(existing.getId());
        Map<String,Integer> requestedItems=request.items().stream().collect(Collectors.toMap(line->line.productId()+":"+String.valueOf(line.variationId()),SokoRequests.CheckoutItem::quantity));
        Map<String,Integer> persistedItems=savedItems.stream().collect(Collectors.toMap(line->line.getProductId()+":"+String.valueOf(line.getVariationId()),SokoOrderItem::getQuantity));
        String requestedMethod=StringUtils.upperCase(StringUtils.trimToEmpty(request.deliveryMethod()),Locale.ROOT);
        boolean destinationMatches;
        if("PICKUP".equals(existing.getDeliveryMethod()))destinationMatches=request.destinationUnitId()==null;
        else if(existing.getDestinationUnitId()!=null)destinationMatches=java.util.Objects.equals(existing.getDestinationUnitId(),request.destinationUnitId());
        else destinationMatches=request.destinationUnitId()==null
                &&java.util.Objects.equals(StringUtils.trimToNull(existing.getDeliveryAddress()),StringUtils.trimToNull(request.deliveryAddress()))
                &&java.util.Objects.equals(existing.getDeliveryLatitude(),request.deliveryLatitude())
                &&java.util.Objects.equals(existing.getDeliveryLongitude(),request.deliveryLongitude());
        boolean matches=existing.getStoreId()==request.storeId()
                &&java.util.Objects.equals(existing.getDeliveryMethod(),requestedMethod)
                &&destinationMatches
                &&java.util.Objects.equals(StringUtils.trimToNull(existing.getCustomerPhone()),StringUtils.trimToNull(request.customerPhone()))
                &&java.util.Objects.equals(StringUtils.trimToNull(existing.getNotes()),StringUtils.trimToNull(request.notes()))
                &&java.util.Objects.equals(existing.getDestinationUnitId(),request.destinationUnitId())
                &&persistedItems.equals(requestedItems);
        if(!matches)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"This checkout key was already used for a different order. Refresh your cart and try again.");
        SokoStore store=storeRepo.findById(existing.getStoreId()).orElseThrow(this::notFound);return detail(existing,store,savedItems);
    }
    private PMSInvoice createInvoice(SokoOrder o,SokoStore s,List<SokoOrderItem> items){PMSInvoice inv=new PMSInvoice();inv.setUnitId(o.getDestinationUnitId()==null?0:o.getDestinationUnitId());inv.setPropertyId(0);inv.setDescription(("Soko order "+o.getOrderNumber()).getBytes(StandardCharsets.UTF_8));String html=items.stream().map(i->"<tr><td><span>"+HtmlUtils.htmlEscape(i.getProductName())+" x "+i.getQuantity()+"</span></td><td class='amount-col'>"+i.getLineTotal()+"</td></tr>").collect(Collectors.joining());inv.setHtmlDescription(html.getBytes(StandardCharsets.UTF_8));inv.setMoneyAmount(o.getTotal());inv.setMoneyPendingAmount(o.getTotal());inv.setCurrency(org.pms.silverocean.service.payment.money.MonetaryPolicy.currency(o.getCurrency()));inv.setBilledUserId(o.getCustomerUserId());inv.setPayToUserId(s.getOwnerUserId());inv.setPaymentAccountId(o.getPaymentAccountId());inv.setActive(true);inv.setBillingType("SOKO");inv.setCustomerPhoneNumber(o.getCustomerPhone());userDao.findById(o.getCustomerUserId()).ifPresent(u->inv.setCustomerEmail(u.getEmail()));invoiceDao.createInvoice(inv);return inv;}
    private void assignAndRegisterDelivery(SokoOrder o,SokoRequests.Dispatch d){
        if(d==null||d.riderId()==null)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Select a verified, registered and available SlickHood rider. One-off courier dispatch is no longer available.");
        if(d.expectedArrivalTime()==null)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Enter the rider's expected arrival time before dispatching the order.");
        ZonedDateTime expectedArrival=d.expectedArrivalTime().atZone(ZoneId.of("Africa/Nairobi")).withZoneSameInstant(ZoneId.of("UTC"));
        if(!expectedArrival.isAfter(now()))throw invalid();
        SokoRider rider=riderRepo.findForUpdate(d.riderId(),o.getStoreId()).orElseThrow(this::notFound);if(!rider.isPhoneConfirmed()||!rider.isVerified()||!"ACTIVE".equals(rider.getStatus())||!"AVAILABLE".equals(rider.getAvailability()))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Choose a phone-confirmed, active and available rider.");rider.setAvailability("BUSY");riderRepo.save(rider);o.setRiderId(rider.getId());o.setCourierName(rider.getDisplayName());o.setCourierPhone(rider.getPhoneNumber());o.setCourierVehiclePlate(rider.getVehiclePlate());o.setDeliveryFailedAt(null);o.setDeliveryExceptionReason(null);
        o.setExpectedArrivalAt(expectedArrival);
        resetRiderAssignmentAccess(o);
    }
    private SokoModels.OrderAction acceptAssignedOrder(SokoOrder order){
        if(order.getAssignmentAcceptedAt()!=null&&List.of("ASSIGNMENT_ACCEPTED","DISPATCHED","COMPLETED").contains(order.getStatus()))
            return SokoModels.OrderAction.from(order);
        // Rolling-deploy recovery: older accountless assignments may already be
        // DISPATCHED without ever having had a personal acceptance link.
        if("DISPATCHED".equals(order.getStatus())&&order.getRiderAssignmentTokenHash()!=null){
            order.setAssignmentAcceptedAt(now());extendAcceptedAssignmentWindow(order);orderRepo.save(order);audit(order,"SOKO_DELIVERY_ACCEPTED");
            notifyOrder(order,"Rider confirmed","The assigned rider confirmed custody through the private delivery link.");
            return SokoModels.OrderAction.from(order);
        }
        requireState(order,"DELIVERY_ASSIGNED");order.setStatus("ASSIGNMENT_ACCEPTED");order.setAssignmentAcceptedAt(now());extendAcceptedAssignmentWindow(order);
        orderRepo.save(order);audit(order,"SOKO_DELIVERY_ACCEPTED");notifyOrder(order,"Rider assigned","Your verified rider accepted the delivery assignment.");
        return SokoModels.OrderAction.from(order);
    }
    private void extendAcceptedAssignmentWindow(SokoOrder order){
        ZonedDateTime collectBy=now().plusMinutes(Math.max(15,Math.min(riderAssignmentAcceptedCollectMinutes,240)));
        if(order.getRiderAssignmentTokenExpiresAt()==null||order.getRiderAssignmentTokenExpiresAt().isBefore(collectBy))
            order.setRiderAssignmentTokenExpiresAt(collectBy);
    }
    private SokoModels.OrderAction collectAssignedOrder(SokoOrder order){
        if(order.getCollectedAt()!=null&&List.of("DISPATCHED","COMPLETED").contains(order.getStatus()))return SokoModels.OrderAction.from(order);
        requireState(order,"ASSIGNMENT_ACCEPTED");order.setStatus("DISPATCHED");order.setCollectedAt(now());order.setDispatchedAt(now());
        ensureDeliveryVisitor(order);generateDeliveryCode(order);
        if(order.getDeliveryCodeExpiresAt()!=null&&(order.getRiderAssignmentTokenExpiresAt()==null
                ||order.getRiderAssignmentTokenExpiresAt().isBefore(order.getDeliveryCodeExpiresAt())))
            order.setRiderAssignmentTokenExpiresAt(order.getDeliveryCodeExpiresAt());
        orderRepo.save(order);audit(order,"SOKO_DELIVERY_COLLECTED");
        notifyOrder(order,"Out for delivery","The rider collected your order and is on the way.");return SokoModels.OrderAction.from(order);
    }
    private void ensureDeliveryVisitor(SokoOrder order){
        if(order.getDestinationUnitId()==null||order.getDeliveryVisitorId()!=null)return;
        if(order.getExpectedArrivalAt()==null)throw invalid();
        java.time.LocalDateTime arrival=order.getExpectedArrivalAt()
                .withZoneSameInstant(ZoneId.of("Africa/Nairobi")).toLocalDateTime();
        var visitor=visitorService.preRegisterDeliveryForHost(order.getCustomerUserId(),
                new CreateVisitorRequest(order.getCourierName(),order.getCourierVehiclePlate(),arrival,null,false,
                        order.getDestinationUnitId(),order.getCourierPhone(),VisitorCategory.DELIVERY));
        order.setDeliveryVisitorId(visitor.getId());
    }
    private void storeDeliveryProof(SokoOrder order,MultipartFile proof)throws IOException{
        if(StringUtils.isNotBlank(order.getDeliveryProofReference()))return;
        if(!"DISPATCHED".equals(order.getStatus())||proof==null||proof.isEmpty()||proof.getSize()>5L*1024*1024)throw invalid();
        String type=StringUtils.defaultString(proof.getContentType()).toLowerCase(Locale.ROOT);byte[] bytes=proof.getBytes();
        if(!validProof(type,bytes))throw new PMSCustomException(ResponseCode.INVALID_IMAGE);
        malwarePolicy.requireSafe(bytes);String extension="image/png".equals(type)?"png":"jpg";
        String ref="soko/delivery-proof/"+order.getId()+"/"+UUID.randomUUID()+"."+extension;
        garageService.uploadBytes(ref,bytes,type);order.setDeliveryProofReference(ref);order.setDeliveryProofContentType(type);
        order.setDeliveryProofSize((long)bytes.length);order.setDeliveryProofAt(now());orderRepo.save(order);
    }
    private void confirmDeliveryOrder(SokoOrder order,SokoRequests.DeliveryConfirmation request){
        if("DELIVERY".equals(order.getDeliveryMethod())&&"COMPLETED".equals(order.getStatus())&&order.isDeliveryCodeVerified())return;
        String code=decryptDeliveryCode(order);
        if(!"DELIVERY".equals(order.getDeliveryMethod())||!"DISPATCHED".equals(order.getStatus())||order.isDeliveryCodeVerified()
                ||StringUtils.isBlank(code)||StringUtils.isBlank(order.getDeliveryProofReference())||codeExpired(order))throw invalid();
        if(order.getDeliveryCodeLockedAt()!=null)throw new SokoDeliveryCodeLockedException();
        if(order.getDeliveryCodeAttempts()>=5){order.setDeliveryCodeLockedAt(now());orderRepo.save(order);throw new SokoDeliveryCodeLockedException();}
        order.setDeliveryCodeAttempts(order.getDeliveryCodeAttempts()+1);
        if(!constantTimeEquals(code,request.code())){if(order.getDeliveryCodeAttempts()>=5){order.setDeliveryCodeLockedAt(now());orderRepo.save(order);throw new SokoDeliveryCodeLockedException();}orderRepo.save(order);throw invalid();}
        order.setDeliveryCodeVerified(true);order.setDeliveryCode(null);order.setEncryptedDeliveryCode(null);
        order.setDeliveryRecipientName(StringUtils.left(StringUtils.trimToNull(request.recipientName()),160));order.setDeliveryProofAt(now());
        order.setStatus("COMPLETED");order.setCompletedAt(now());revokeRiderAssignmentAccess(order);cancelDeliveryVisitor(order);
        releaseRider(order,true);orderRepo.save(order);audit(order,"SOKO_DELIVERY_COMPLETED");
        notifyOrder(order,"Delivered","Delivery was verified using the buyer's single-use code.");
    }
    private void issueRiderAssignmentLink(SokoOrder order,SokoStore store,boolean respectCooldown,boolean newAssignment){
        SokoRider rider=riderRepo.findForUpdate(order.getRiderId(),order.getStoreId()).orElseThrow(this::notFound);
        ZonedDateTime timestamp=now();
        if(newAssignment)resetRiderAssignmentAccess(order);
        long cooldown=Math.max(30,Math.min(riderAssignmentLinkCooldownSeconds,600));
        if(respectCooldown&&order.getRiderAssignmentLinkRequestedAt()!=null
                &&order.getRiderAssignmentLinkRequestedAt().plusSeconds(cooldown).isAfter(timestamp))
            throw new PMSCustomException(ResponseCode.OTP_RESEND_TOO_SOON,"A rider assignment link was just sent. Wait one minute before resending.");
        int maxSends=Math.max(2,Math.min(riderAssignmentLinkMaxSends,10));
        if(order.getRiderAssignmentLinkRequestCount()>=maxSends)
            throw new PMSCustomException(ResponseCode.OTP_RESEND_TOO_SOON,"This assignment has reached the SMS link limit. Choose another rider or contact support.");
        RawAssignmentToken token=newAssignmentToken();long validHours=Math.max(1,Math.min(riderAssignmentTokenValidHours,168));
        order.setRiderAssignmentTokenHash(token.hash());order.setRiderAssignmentTokenIssuedAt(timestamp);
        ZonedDateTime expiresAt=timestamp.plusHours(validHours);
        if("DISPATCHED".equals(order.getStatus())){
            if(order.getDeliveryCodeExpiresAt()!=null&&order.getDeliveryCodeExpiresAt().isAfter(expiresAt))expiresAt=order.getDeliveryCodeExpiresAt();
            if(order.getRiderAssignmentTokenExpiresAt()!=null&&order.getRiderAssignmentTokenExpiresAt().isAfter(expiresAt))expiresAt=order.getRiderAssignmentTokenExpiresAt();
        }
        order.setRiderAssignmentTokenExpiresAt(expiresAt);order.setRiderAssignmentTokenRevokedAt(null);
        order.setRiderAssignmentLinkRequestedAt(timestamp);order.setRiderAssignmentLinkRequestCount(order.getRiderAssignmentLinkRequestCount()+1);
        boolean enRoute="DISPATCHED".equals(order.getStatus());
        String template=i18n.getLocalizedMessage(enRoute?"sms.soko.rider.delivery.continue":NotificationType.SOKO_RIDER_ASSIGNMENT_SMS.getBody());
        if(StringUtils.isBlank(template))template=enRoute
                ?"SlickHood delivery %s from %s is in progress. Continue delivery and complete secure handover: %s. This private link expires in %s hours. Do not share it."
                :"SlickHood delivery %s from %s is ready. Accept or decline: %s. This private link expires in %s hours. Do not share it.";
        String link=assignmentLinkBase()+"#token="+token.raw();
        long displayedValidityHours=Math.max(1,(java.time.Duration.between(timestamp,expiresAt).toMinutes()+59)/60);
        String body=String.format(template,order.getOrderNumber(),storeName(order,store),link,displayedValidityHours);
        notificationService.queueNotification(new NotificationDTO(body,rider.getPhoneNumber(),NotificationType.SOKO_RIDER_ASSIGNMENT_SMS));
        audit(order,newAssignment?"SOKO_RIDER_ASSIGNMENT_LINK_SENT":"SOKO_RIDER_ASSIGNMENT_LINK_RESENT");
    }
    private SokoModels.PublicRiderAssignment publicRiderView(SokoOrder order){return publicRiderView(order,true);}
    private SokoModels.PublicRiderAssignment publicRiderView(SokoOrder order,boolean allowDeliveryDetails){
        SokoStore store=storeRepo.findById(order.getStoreId()).orElseThrow(this::notFound);
        List<SokoOrderItem> rows=itemRepo.findAllByOrderIdAndActiveTrueOrderById(order.getId());
        List<SokoModels.PublicRiderItem> publicItems=rows.stream().map(SokoModels.PublicRiderItem::from).toList();
        int itemCount=rows.stream().mapToInt(SokoOrderItem::getQuantity).sum();
        boolean accepted=allowDeliveryDetails&&order.getAssignmentAcceptedAt()!=null&&!"DELIVERY_ASSIGNED".equals(order.getStatus());
        SokoModels.PublicRiderDelivery delivery=accepted
                ?new SokoModels.PublicRiderDelivery(order.getDeliveryAddress(),order.getDeliveryLatitude(),order.getDeliveryLongitude(),order.getCustomerPhone())
                :null;
        return new SokoModels.PublicRiderAssignment(order.getOrderNumber(),order.getStatus(),storeName(order,store),
                storeAddress(order,store),order.getExpectedArrivalAt(),itemCount,publicItems,
                StringUtils.isNotBlank(order.getDeliveryProofReference()),delivery);
    }
    private SokoOrder publicAssignmentOrder(String token,boolean forUpdate){
        String hash=assignmentTokenHash(token);
        SokoOrder order=(forUpdate?orderRepo.findByRiderAssignmentTokenHashForUpdate(hash)
                :orderRepo.findByRiderAssignmentTokenHashAndActiveTrue(hash)).orElseThrow(this::invalidAssignmentAccess);
        if(forUpdate&&expireRiderAssignmentIfNeeded(order))throw invalidAssignmentAccess();
        requireCurrentAssignmentAccess(order);
        return order;
    }
    private SokoOrder publicAssignmentOrderForInspect(String token){
        SokoOrder order=orderRepo.findByRiderAssignmentTokenHashAndActiveTrue(assignmentTokenHash(token))
                .orElseThrow(this::invalidAssignmentAccess);
        if(terminalAssignmentReceipt(order))return order;
        requireCurrentAssignmentAccess(order);return order;
    }
    private SokoOrder publicAssignmentOrderForCompletion(String token){
        SokoOrder order=orderRepo.findByRiderAssignmentTokenHashForUpdate(assignmentTokenHash(token))
                .orElseThrow(this::invalidAssignmentAccess);
        if(terminalAssignmentReceipt(order))return order;
        if(expireRiderAssignmentIfNeeded(order))throw invalidAssignmentAccess();
        requireCurrentAssignmentAccess(order);return order;
    }
    private boolean terminalAssignmentReceipt(SokoOrder order){
        return "COMPLETED".equals(order.getStatus())&&order.isDeliveryCodeVerified()
                &&order.getRiderAssignmentTokenRevokedAt()!=null&&order.getRiderAssignmentTokenExpiresAt()!=null
                &&order.getRiderAssignmentTokenExpiresAt().isAfter(now());
    }
    private SokoOrder publicAssignmentOrderForDecline(String token){
        SokoOrder order=orderRepo.findByRiderAssignmentTokenHashForUpdate(assignmentTokenHash(token)).orElseThrow(this::invalidAssignmentAccess);
        if(order.getRiderAssignmentDeclinedAt()!=null){
            boolean currentReceipt="PACKED".equals(order.getStatus())&&order.getRiderAssignmentTokenRevokedAt()!=null
                    &&order.getRiderAssignmentTokenExpiresAt()!=null&&order.getRiderAssignmentTokenExpiresAt().isAfter(now());
            if(currentReceipt)return order;
            throw invalidAssignmentAccess();
        }
        if(expireRiderAssignmentIfNeeded(order))throw invalidAssignmentAccess();
        requireCurrentAssignmentAccess(order);
        return order;
    }
    private void requireCurrentAssignmentAccess(SokoOrder order){
        if(order.getRiderId()==null||order.getRiderAssignmentTokenRevokedAt()!=null
                ||order.getRiderAssignmentTokenExpiresAt()==null||!order.getRiderAssignmentTokenExpiresAt().isAfter(now()))
            throw invalidAssignmentAccess();
        SokoRider rider=riderRepo.findByIdAndStoreIdAndActiveTrue(order.getRiderId(),order.getStoreId()).orElseThrow(this::invalidAssignmentAccess);
        if(!rider.isPhoneConfirmed()||!rider.isVerified()||!"ACTIVE".equals(rider.getStatus()))throw invalidAssignmentAccess();
    }
    private void requirePersonalRiderAcceptance(SokoOrder order){
        if(order.getAssignmentAcceptedAt()==null)throw invalidAssignmentAccess();
    }
    private void expireRiderAssignments(List<Long> storeIds){
        Pageable batch=PageRequest.of(0,100,Sort.by("riderAssignmentTokenExpiresAt"));
        for(int scan=0;scan<10;scan++){
            List<SokoOrder> expired=orderRepo.findExpiredRiderAssignmentsForStoresForUpdate(storeIds,now(),batch);
            expired.forEach(this::expireRiderAssignmentIfNeeded);
            if(expired.size()<100)break;
        }
    }
    private boolean expireRiderAssignmentIfNeeded(SokoOrder order){
        if(!List.of("DELIVERY_ASSIGNED","ASSIGNMENT_ACCEPTED").contains(order.getStatus())
                ||order.getRiderAssignmentTokenRevokedAt()!=null||order.getRiderAssignmentTokenExpiresAt()==null
                ||order.getRiderAssignmentTokenExpiresAt().isAfter(now()))return false;
        releaseRider(order,false);order.setStatus("PACKED");order.setDeliveryFailedAt(now());
        order.setDeliveryExceptionReason("Rider assignment response window expired");
        revokeRiderAssignmentAccess(order);clearPriorAssignmentDisplay(order);orderRepo.save(order);
        audit(order,"SOKO_RIDER_ASSIGNMENT_EXPIRED");notifyOrder(order,"Rider response expired","Choose an available rider and send a new private assignment link.");
        return true;
    }
    private RawAssignmentToken newAssignmentToken(){
        byte[] bytes=new byte[32];SECURE_RANDOM.nextBytes(bytes);
        String raw=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new RawAssignmentToken(raw,hashAssignmentToken(raw));
    }
    private String assignmentTokenHash(String token){
        String raw=StringUtils.trimToNull(token);
        if(raw==null||raw.length()>64)throw invalidAssignmentAccess();
        try{if(java.util.Base64.getUrlDecoder().decode(raw).length!=32)throw invalidAssignmentAccess();}
        catch(IllegalArgumentException invalid){throw invalidAssignmentAccess();}
        return hashAssignmentToken(raw);
    }
    private String hashAssignmentToken(String raw){
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 is unavailable",impossible);}
    }
    private String assignmentLinkBase(){
        String configured=StringUtils.defaultIfBlank(riderAssignmentUrl,"https://app.slickhood.com/soko/rider-assignment").trim();
        try{
            URI uri=URI.create(configured);
            if(!"https".equalsIgnoreCase(uri.getScheme())||StringUtils.isBlank(uri.getHost())||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)
                throw new IllegalStateException("The Soko rider assignment URL must be an HTTPS URL without query or fragment data.");
            return configured.endsWith("/")?configured.substring(0,configured.length()-1):configured;
        }catch(IllegalArgumentException invalid){throw new IllegalStateException("The Soko rider assignment URL is invalid.",invalid);}
    }
    private void resetRiderAssignmentAccess(SokoOrder order){
        order.setRiderAssignmentTokenHash(null);order.setRiderAssignmentTokenIssuedAt(null);order.setRiderAssignmentTokenExpiresAt(null);
        order.setRiderAssignmentTokenRevokedAt(null);order.setRiderAssignmentLinkRequestedAt(null);order.setRiderAssignmentLinkRequestCount(0);
        order.setRiderAssignmentDeclinedAt(null);order.setRiderAssignmentDeclineReason(null);
    }
    private void revokeRiderAssignmentAccess(SokoOrder order){
        if(order.getRiderAssignmentTokenHash()!=null&&order.getRiderAssignmentTokenRevokedAt()==null)order.setRiderAssignmentTokenRevokedAt(now());
    }
    private PMSCustomException invalidAssignmentAccess(){return new SokoRiderAssignmentAccessException();}
    private record RawAssignmentToken(String raw,String hash){}
    private void generateDeliveryCode(SokoOrder o){String code=generateHandoverCode(o);notifyDeliveryCode(o,code);}
    private void generatePickupCode(SokoOrder o){generateHandoverCode(o);}
    private String generateHandoverCode(SokoOrder o){String code=String.format(Locale.ROOT,"%06d",SECURE_RANDOM.nextInt(1_000_000));o.setDeliveryCode(null);o.setEncryptedDeliveryCode(encryptionService.encrypt(code));o.setDeliveryCodeAttempts(0);o.setDeliveryCodeLockedAt(null);o.setDeliveryCodeVerified(false);o.setDeliveryCodeExpiresAt(now().plusHours(Math.max(1,Math.min(deliveryCodeValidHours,72))));return code;}
    private void clearDeliveryCode(SokoOrder o){o.setDeliveryCode(null);o.setEncryptedDeliveryCode(null);o.setDeliveryCodeExpiresAt(null);o.setDeliveryCodeLockedAt(null);}
    private boolean handoverCodeRecoveryAllowed(SokoOrder o){return !o.isDeliveryCodeVerified()&&(("DELIVERY".equals(o.getDeliveryMethod())&&"DISPATCHED".equals(o.getStatus()))||("PICKUP".equals(o.getDeliveryMethod())&&"READY_FOR_PICKUP".equals(o.getStatus())));}
    // Financial finality invalidates delivery authorization, but does not imply
    // returned groceries are saleable or that a rider has relinquished custody.
    private void clearDeliveryAuthorization(SokoOrder o){clearDeliveryCode(o);revokeRiderAssignmentAccess(o);cancelDeliveryVisitor(o);o.setDeliveryRecoveryOtp(null);o.setDeliveryRecoveryOtpExpiresAt(null);}
    private boolean codeExpired(SokoOrder o){return o.getDeliveryCodeExpiresAt()==null||!o.getDeliveryCodeExpiresAt().isAfter(now());}
    private SokoOrder assignedOrder(long id,String required){SokoOrder o=orderRepo.findByIdForUpdate(id).orElseThrow(this::notFound);if(o.getRiderId()==null)throw forbidden();SokoRider r=riderRepo.findForUpdate(o.getRiderId(),o.getStoreId()).orElseThrow(this::notFound);if(!r.isPhoneConfirmed()||!r.isVerified()||!"ACTIVE".equals(r.getStatus())||!java.util.Objects.equals(r.getUserId(),userDao.getUserId()))throw forbidden();if(required!=null)requireState(o,required);return o;}
    private SokoRider requireMerchantManagedRider(SokoOrder o){if(o.getRiderId()==null)throw forbidden();SokoRider rider=riderRepo.findForUpdate(o.getRiderId(),o.getStoreId()).orElseThrow(this::notFound);if(rider.getUserId()!=null)throw forbidden();return rider;}
    private SokoOrder deliveryExceptionOrder(long id,String required){
        SokoOrder o=orderRepo.findByIdForUpdate(id).orElseThrow(this::notFound);if(o.getRiderId()==null)throw forbidden();
        SokoRider rider=riderRepo.findForUpdate(o.getRiderId(),o.getStoreId()).orElseThrow(this::notFound);long actor=userDao.getUserId();
        boolean assignedRider=rider.isPhoneConfirmed()&&rider.isVerified()&&"ACTIVE".equals(rider.getStatus())&&java.util.Objects.equals(rider.getUserId(),actor);
        boolean owningMerchant=storeRepo.findByIdAndActiveTrue(o.getStoreId()).filter(store->store.getOwnerUserId()==actor).isPresent();
        if(!assignedRider&&!owningMerchant)throw forbidden();if(required!=null)requireState(o,required);return o;
    }
    private SokoOrder financeHoldOrder(long id){
        SokoOrder o=orderRepo.findByIdForUpdate(id).orElseThrow(this::notFound);
        if(!List.of("FINANCE_HOLD_RETURN_REQUIRED","FINANCE_HOLD_RETURNED").contains(o.getStatus()))throw invalid();
        if(o.getRiderId()==null)throw forbidden();
        SokoRider rider=riderRepo.findForUpdate(o.getRiderId(),o.getStoreId()).orElseThrow(this::notFound);
        long actor=userDao.getUserId();boolean assigned=rider.isPhoneConfirmed()&&rider.isVerified()&&"ACTIVE".equals(rider.getStatus())
                &&java.util.Objects.equals(rider.getUserId(),actor);
        boolean merchant=storeRepo.findByIdAndActiveTrue(o.getStoreId()).filter(store->store.getOwnerUserId()==actor).isPresent();
        if(!assigned&&!merchant)throw forbidden();return o;
    }
    private SokoRider requireAssignedRiderForDoorstep(SokoOrder o){
        if(o.getRiderId()==null)throw forbidden();
        SokoRider rider=riderRepo.findForUpdate(o.getRiderId(),o.getStoreId()).orElseThrow(this::notFound);
        if(rider.isPhoneConfirmed()&&rider.isVerified()&&"ACTIVE".equals(rider.getStatus())
                &&rider.getUserId()!=null&&rider.getUserId().equals(userDao.getUserId()))return rider;
        throw forbidden();
    }
    private void releaseRider(SokoOrder o,boolean completed){if(o.getRiderId()==null)return;riderRepo.findForUpdate(o.getRiderId(),o.getStoreId()).ifPresent(r->{if("BUSY".equals(r.getAvailability()))r.setAvailability(r.isVerified()&&"ACTIVE".equals(r.getStatus())?"AVAILABLE":"OFFLINE");if(completed)r.setCompletedDeliveries(r.getCompletedDeliveries()+1);riderRepo.save(r);});}
    private void clearPriorAssignmentDisplay(SokoOrder order){revokeRiderAssignmentAccess(order);cancelDeliveryVisitor(order);order.setCourierName(null);order.setCourierPhone(null);order.setCourierVehiclePlate(null);order.setAssignedAt(null);order.setAssignmentAcceptedAt(null);order.setExpectedArrivalAt(null);}
    private void cancelDeliveryVisitor(SokoOrder order){if(order.getDeliveryVisitorId()!=null
            &&visitorService.revokeSokoDeliveryVisitor(order.getDeliveryVisitorId(),order.getCustomerUserId()))order.setDeliveryVisitorId(null);}
    private boolean validProof(String type,byte[] b){if(b==null)return false;return switch(type){case "image/jpeg"->b.length>3&&(b[0]&255)==255&&(b[1]&255)==216;case "image/png"->b.length>8&&(b[0]&255)==137&&b[1]=='P'&&b[2]=='N'&&b[3]=='G';default->false;};}
    private String decryptDeliveryCode(SokoOrder order){if(order.getEncryptedDeliveryCode()!=null){var decrypted=encryptionService.decrypt(order.getEncryptedDeliveryCode());return decrypted==null?null:decrypted.decryptedValue();}return order.getDeliveryCode();}
    private boolean constantTimeEquals(String expected,String actual){return java.security.MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),actual.getBytes(StandardCharsets.UTF_8));}
    private String catalogOption(String raw,String fallback,Set<String> allowed,String message){String value=raw==null?fallback:StringUtils.trimToNull(raw);if(value==null||!allowed.contains(value.toUpperCase(Locale.ROOT)))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,message);return value.toUpperCase(Locale.ROOT);}
    private void validateCatalogLocation(Double latitude,Double longitude,Double radiusKm,String sortMode){
        if((latitude==null)!=(longitude==null))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Provide both latitude and longitude.");
        if(latitude!=null&&(!validLatitude(latitude)||!validLongitude(longitude)))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Choose valid latitude and longitude coordinates.");
        if(radiusKm!=null&&(!Double.isFinite(radiusKm)||radiusKm<1d||radiusKm>100d))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Search radius must be between 1 km and 100 km.");
        if("NEAREST".equals(sortMode)&&latitude==null)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Share a location before sorting by nearest.");
    }
    private Pageable catalogPageable(Pageable p){return p==null?PageRequest.of(0,20):PageRequest.of(Math.max(0,p.getPageNumber()),Math.min(100,Math.max(1,p.getPageSize())));}
    private Pageable bounded(Pageable p){return PageRequest.of(Math.max(0,p.getPageNumber()),Math.min(100,Math.max(1,p.getPageSize())),p.getSort().isSorted()?p.getSort():Sort.by(Sort.Direction.DESC,"createdOn"));}
    private void restoreStock(SokoOrder o){if(o.isStockReleased())return;for(SokoOrderItem i:itemRepo.findAllByOrderIdAndActiveTrueOrderById(o.getId())){SokoProduct p=productRepo.findByIdForUpdate(i.getProductId()).orElse(null);if(p!=null){p.setStockQuantity(p.getStockQuantity()+i.getQuantity());if(OUT_OF_STOCK.equals(p.getStatus()))p.setStatus(PUBLISHED);productRepo.save(p);}if(i.getVariationId()!=null)productVariationRepo.findForUpdate(i.getVariationId(),i.getProductId()).ifPresent(v->{v.setStockQuantity(v.getStockQuantity()+i.getQuantity());productVariationRepo.save(v);});}o.setStockReleased(true);}
    private OrderDetail detail(SokoOrder o){SokoStore s=storeRepo.findById(o.getStoreId()).orElseThrow(this::notFound);return detail(o,s,itemRepo.findAllByOrderIdAndActiveTrueOrderById(o.getId()));}
    private OrderDetail detail(SokoOrder o,SokoStore s,List<SokoOrderItem> items){return detail(o,s,items,invoiceId(o));}
    private OrderDetail detail(SokoOrder o,SokoStore s,List<SokoOrderItem> items,Long invoiceId){Long account=o.getPaymentAccountId()!=null?o.getPaymentAccountId():s.getPaymentAccountId();String channel=o.getPaymentChannel()!=null?o.getPaymentChannel():safeChannel(account);return new OrderDetail(SokoModels.OrderView.from(o),storeName(o,s),storeAddress(o,s),storePhone(o,s),storeLatitude(o,s),storeLongitude(o,s),account,channel,invoiceId,items.stream().map(SokoModels.OrderItemView::from).toList());}
    private Long invoiceId(SokoOrder order){if(StringUtils.isBlank(order.getInvoiceRef()))return null;return invoiceDao.getInvoiceIdsByRefs(List.of(order.getInvoiceRef())).get(order.getInvoiceRef());}
    private String safeChannel(Long id){if(id==null)return null;try{var account=accountDao.getAccountById(id);return account==null||account.getChannel()==null?null:account.getChannel().name();}catch(PMSCustomException missingAccount){return null;}}
    private Page<OrderDetail> hydrate(Page<SokoOrder> page){
        if(page.isEmpty())return new PageImpl<>(List.of(),page.getPageable(),page.getTotalElements());
        List<Long> orderIds=page.stream().map(SokoOrder::getId).toList();
        Map<Long,List<SokoOrderItem>> itemsByOrder=itemRepo.findAllByOrderIdInAndActiveTrueOrderByOrderIdAscIdAsc(orderIds).stream().collect(Collectors.groupingBy(SokoOrderItem::getOrderId));
        Map<Long,SokoStore> stores=storeRepo.findAllById(page.stream().map(SokoOrder::getStoreId).distinct().toList()).stream().collect(Collectors.toMap(SokoStore::getId,Function.identity()));
        Map<String,Long> invoiceIds=invoiceDao.getInvoiceIdsByRefs(page.stream().map(SokoOrder::getInvoiceRef).filter(StringUtils::isNotBlank).distinct().toList());
        Map<Long,String> channels=new java.util.HashMap<>();page.forEach(o->{SokoStore s=stores.get(o.getStoreId());Long id=o.getPaymentAccountId()!=null?o.getPaymentAccountId():s==null?null:s.getPaymentAccountId();if(id!=null&&!channels.containsKey(id))channels.put(id,safeChannel(id));});
        List<OrderDetail> content=page.stream().map(o->{SokoStore s=stores.get(o.getStoreId());if(s==null)throw notFound();Long account=o.getPaymentAccountId()!=null?o.getPaymentAccountId():s.getPaymentAccountId();Long invoiceId=StringUtils.isBlank(o.getInvoiceRef())?null:invoiceIds.get(o.getInvoiceRef());return new OrderDetail(SokoModels.OrderView.from(o),storeName(o,s),storeAddress(o,s),storePhone(o,s),storeLatitude(o,s),storeLongitude(o,s),account,o.getPaymentChannel()!=null?o.getPaymentChannel():channels.get(account),invoiceId,itemsByOrder.getOrDefault(o.getId(),List.of()).stream().map(SokoModels.OrderItemView::from).toList());}).toList();
        return new PageImpl<>(content,page.getPageable(),page.getTotalElements());
    }
    private Page<SokoModels.RiderAssignment> hydrateRiderAssignments(Page<SokoOrder> page){
        if(page.isEmpty())return new PageImpl<>(List.of(),page.getPageable(),page.getTotalElements());
        List<Long> orderIds=page.stream().map(SokoOrder::getId).toList();
        Map<Long,List<SokoModels.RiderItem>> itemsByOrder=itemRepo.findAllByOrderIdInAndActiveTrueOrderByOrderIdAscIdAsc(orderIds).stream().collect(Collectors.groupingBy(SokoOrderItem::getOrderId,Collectors.mapping(SokoModels.RiderItem::from,Collectors.toList())));
        Map<Long,SokoStore> stores=storeRepo.findAllById(page.stream().map(SokoOrder::getStoreId).distinct().toList()).stream().collect(Collectors.toMap(SokoStore::getId,Function.identity()));
        List<SokoModels.RiderAssignment> content=page.stream().map(order->{SokoStore store=stores.get(order.getStoreId());if(store==null)throw notFound();return new SokoModels.RiderAssignment(SokoModels.RiderOrder.from(order),storeName(order,store),storeAddress(order,store),storePhone(order,store),storeLatitude(order,store),storeLongitude(order,store),itemsByOrder.getOrDefault(order.getId(),List.of()));}).toList();
        return new PageImpl<>(content,page.getPageable(),page.getTotalElements());
    }
    private String storeName(SokoOrder order,SokoStore store){return StringUtils.defaultIfBlank(order.getStoreNameSnapshot(),store.getName());}
    private String storeAddress(SokoOrder order,SokoStore store){return StringUtils.defaultIfBlank(order.getStoreAddressSnapshot(),store.getAddress());}
    private String storePhone(SokoOrder order,SokoStore store){return StringUtils.defaultIfBlank(order.getStorePhoneSnapshot(),store.getPhoneNumber());}
    private Double storeLatitude(SokoOrder order,SokoStore store){return order.getStoreLatitudeSnapshot()!=null?order.getStoreLatitudeSnapshot():store.getLatitude();}
    private Double storeLongitude(SokoOrder order,SokoStore store){return order.getStoreLongitudeSnapshot()!=null?order.getStoreLongitudeSnapshot():store.getLongitude();}
    private SokoStore ownedStore(long id){return storeRepo.findByIdAndOwnerUserIdAndActiveTrue(id,userDao.getUserId()).orElseThrow(this::notFound);}
    private void requireMerchantRole(){if(!userDao.hasRole(PMSRole.SERVICE_PROVIDER)&&!userDao.hasRole(PMSRole.SUPER_ADMIN))throw new PMSCustomException(ResponseCode.INVALID_ROLE);}
    private void requireFinanceRole(){if(!userDao.hasRole(PMSRole.FINANCE)&&!userDao.hasRole(PMSRole.SUPER_ADMIN))throw forbidden();}
    private void requireSuperAdmin(){if(!userDao.hasRole(PMSRole.SUPER_ADMIN))throw forbidden();}
    private void notifyModeration(long ownerId,String name,String status,String reason){
        userDao.findById(ownerId).map(user->user.getEmail()).filter(StringUtils::isNotBlank).ifPresent(email->{
            String body=String.format(i18n.getLocalizedMessage(NotificationType.SOKO_MODERATION_EMAIL.getBody()),HtmlUtils.htmlEscape(name),HtmlUtils.htmlEscape(status.replace('_',' ')),HtmlUtils.htmlEscape(StringUtils.defaultIfBlank(reason,"No action is required.")));
            notificationService.queueEmailAndInApp(email,NotificationType.SOKO_MODERATION_EMAIL,body,"SOKO_MODERATION","A Soko listing review was updated. Open /dashboard/soko to review the status and any next step.");
        });
    }
    private void notifyOrder(SokoOrder order,String status,String detail){
        // One durable event per recipient and state; callbacks/replays cannot create
        // duplicate alerts. Private exception notes and buyer codes are never copied.
        String key="soko-order:"+order.getId()+":"+order.getStatus()+":"+order.getPaymentStatus()+":"+order.getRefundStatus()+":"+zero(order.getRefundedAmount());
        String message="Soko order "+order.getOrderNumber()+": "+status+".";
        java.util.Map<Long,String> recipients=new java.util.LinkedHashMap<>();
        recipients.put(order.getCustomerUserId(),"/dashboard/soko");
        storeRepo.findByIdAndActiveTrue(order.getStoreId()).ifPresent(store->recipients.put(store.getOwnerUserId(),"/dashboard/soko"));
        if(order.getRiderId()!=null)riderRepo.findById(order.getRiderId()).filter(SokoRider::isActive).filter(rider->rider.getUserId()!=null)
                .ifPresent(rider->recipients.putIfAbsent(rider.getUserId(),"/dashboard/soko-deliveries"));
        recipients.forEach((recipient,path)->businessAlerts.publish(recipient,key,"SOKO_ORDER_STATUS",message,path));
    }
    private void notifyFinanceRecord(SokoOrder order,boolean refund){
        String key="soko-finance:"+order.getId()+":"+(refund?"refund":"settlement")+":"+(refund?order.getRefundStatus():order.getSettlementStatus())+":"+(refund?order.getRefundedAmount():order.getSettledAmount());
        String message=refund?"The refund record for Soko order "+order.getOrderNumber()+" was updated.":"The receiving-payment record for Soko order "+order.getOrderNumber()+" was updated.";
        if(refund)businessAlerts.publish(order.getCustomerUserId(),key,"SOKO_ORDER_STATUS",message,"/dashboard/soko");
        storeRepo.findByIdAndActiveTrue(order.getStoreId()).filter(store->!refund||store.getOwnerUserId()!=order.getCustomerUserId())
                .ifPresent(store->businessAlerts.publish(store.getOwnerUserId(),key,"SOKO_ORDER_STATUS",message,"/dashboard/soko"));
    }
    private void notifyDeliveryCode(SokoOrder order,String code){userDao.findById(order.getCustomerUserId()).map(u->u.getEmail()).filter(StringUtils::isNotBlank).ifPresent(email->{String body=String.format(i18n.getLocalizedMessage(NotificationType.SOKO_DELIVERY_CODE_EMAIL.getBody()),HtmlUtils.htmlEscape(order.getOrderNumber()),code,order.getDeliveryCodeExpiresAt());notificationService.queueNotification(new NotificationDTO(body,email,NotificationType.SOKO_DELIVERY_CODE_EMAIL));});}
    private boolean productIsInOpenOrder(long productId){return itemRepo.countOpenOrdersForProduct(productId)>0;}
    private void audit(Object entity,String action){if(auditLogService!=null)auditLogService.createAuditLog(entity,action);}
    private void requireState(SokoOrder o,String state){if(!state.equals(o.getStatus()))throw invalid();}
    private BigDecimal zero(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
    private boolean sameAmount(BigDecimal left,BigDecimal right){return left==null?right==null:right!=null&&left.compareTo(right)==0;}
    private boolean hasRefundableBalance(SokoOrder order){return List.of("PAID","PARTIALLY_PAID","PARTIALLY_REFUNDED").contains(order.getPaymentStatus())&&zero(order.getTotal()).subtract(zero(order.getRefundedAmount())).signum()>0;}
    private BigDecimal authoritativeCollected(SokoOrder order){
        PMSInvoice invoice=invoiceDao.getInvoiceByRefForUpdate(order.getInvoiceRef()).orElseThrow(this::notFound);
        BigDecimal collected=invoice.moneyAmount().subtract(invoice.moneyPendingAmount()).max(BigDecimal.ZERO);
        return collected.min(zero(order.getTotal())).setScale(2,java.math.RoundingMode.HALF_UP);
    }
    private BigDecimal confirmedFinancialAdjustments(SokoOrder order){
        return zero(order.getRefundedAmount()).add(zero(order.getReversedAmount())).add(zero(order.getChargedBackAmount()));
    }
    private SokoModels.RefundQueueItem refundQueueItem(SokoOrder order){
        BigDecimal collected=invoiceDao.getInvoiceByRef(order.getInvoiceRef())
                .map(invoice->invoice.moneyAmount().subtract(invoice.moneyPendingAmount()).max(BigDecimal.ZERO))
                .orElse(BigDecimal.ZERO).min(zero(order.getTotal()));
        BigDecimal remaining=collected.subtract(confirmedFinancialAdjustments(order)).max(BigDecimal.ZERO);
        String storeName=storeRepo.findById(order.getStoreId()).map(SokoStore::getName).orElse("Soko shop");
        ZonedDateTime requestedAt=order.getCancelledAt()!=null?order.getCancelledAt():order.getReturnedAt()!=null?order.getReturnedAt():order.getCreatedOn();
        return new SokoModels.RefundQueueItem(order.getId(),order.getOrderNumber(),storeName,order.getInvoiceRef(),
                order.getStatus(),order.getPaymentStatus(),order.getRefundStatus(),order.getCurrency(),order.getTotal(),
                zero(order.getRefundedAmount()),order.getRefundRequestedAmount(),remaining,
                StringUtils.defaultIfBlank(order.getCancellationReason(),order.getDeliveryExceptionReason()),
                requestedAt,order.getLastModifiedDate());
    }
    private void requestOutstandingRefund(SokoOrder order,BigDecimal collected){
        BigDecimal target=collected.subtract(zero(order.getReversedAmount())).subtract(zero(order.getChargedBackAmount())).max(BigDecimal.ZERO);
        if(target.compareTo(zero(order.getRefundedAmount()))<=0)return;
        if(!"PROCESSING".equals(order.getRefundStatus())||order.getRefundRequestedAmount()==null||order.getRefundRequestedAmount().compareTo(target)<0){
            order.setRefundStatus("REQUESTED");order.setRefundRequestedAmount(target);
        }
    }
    private OrderDetail updateManualRefund(SokoOrder order,SokoRequests.FinanceUpdate request){
        BigDecimal collected=authoritativeCollected(order),confirmed=zero(order.getRefundedAmount());
        BigDecimal maximum=collected.subtract(zero(order.getReversedAmount())).subtract(zero(order.getChargedBackAmount())).max(BigDecimal.ZERO);
        BigDecimal requested=request.amount();String next=request.status().name(),current=StringUtils.defaultIfBlank(order.getRefundStatus(),"NOT_REQUIRED");
        String reference=StringUtils.left(StringUtils.trimToNull(request.providerReference()),120);
        if(current.equals(next)&&"CONFIRMED".equals(next)&&confirmed.compareTo(requested)==0&&java.util.Objects.equals(order.getRefundReference(),reference)){
            confirmedRefundReplay(order,reference);return detail(order);
        }
        if(current.equals(next)&&!"CONFIRMED".equals(next)&&zero(order.getRefundRequestedAmount()).compareTo(requested)==0)return detail(order);
        if("CONFIRMED".equals(next)&&confirmed.compareTo(requested)==0&&reference!=null
                &&confirmedRefundReplay(order,reference))return detail(order);
        if(requested.compareTo(confirmed)<0||requested.compareTo(maximum)>0||requested.compareTo(confirmed)==0)throw invalid();
        boolean valid="PROCESSING".equals(next)&&List.of("REQUESTED","FAILED").contains(current)
                ||List.of("CONFIRMED","FAILED").contains(next)&&"PROCESSING".equals(current);
        if(!valid)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Move refunds from requested to processing, then confirm or fail them.");
        if(order.getRefundRequestedAmount()!=null&&List.of("REQUESTED","PROCESSING","FAILED").contains(current)
                &&order.getRefundRequestedAmount().compareTo(requested)!=0)throw invalid();
        if("CONFIRMED".equals(next)&&reference==null)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Enter the provider refund reference before confirming the refund.");
        BigDecimal confirmedDelta=requested.subtract(confirmed);
        if("CONFIRMED".equals(next)&&financeOperationReplay(order.getId(),"REFUND",reference,confirmedDelta))return detail(order);
        order.setRefundStatus(next);
        if("PROCESSING".equals(next)){order.setRefundRequestedAmount(requested);order.setRefundReference(null);}
        else if("FAILED".equals(next)){order.setRefundRequestedAmount(requested);order.setRefundReference(reference);}
        else{
            order.setRefundedAmount(requested);order.setRefundReference(reference);order.setRefundRequestedAmount(null);
            if(confirmedFinancialAdjustments(order).compareTo(collected)>=0){order.setPaymentStatus("REFUNDED");applyFinancialFinality(order,"REFUNDED");}
            else{order.setPaymentStatus("PARTIALLY_REFUNDED");applyFinancialReductionHold(order,collected);}
        }
        orderRepo.save(order);
        if("CONFIRMED".equals(next))financeOperationRepo.save(new SokoFinanceOperation(order.getId(),"REFUND",reference,confirmedDelta));
        audit(order,"SOKO_FINANCE_RECORD_REFUND_"+next);notifyFinanceRecord(order,true);return detail(order);
    }
    private boolean financeOperationReplay(long orderId,String operation,String reference,BigDecimal amount){
        var replay=financeOperationRepo.findByOrderIdAndProviderReference(orderId,reference);
        if(replay.isEmpty())return false;
        SokoFinanceOperation recorded=replay.get();
        if(recorded.getOperationType().equals(operation)&&recorded.getAmount().compareTo(amount)==0)return true;
        throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"This provider reference was already recorded for a different finance operation.");
    }
    private boolean confirmedRefundReplay(SokoOrder order,String reference){
        var replay=financeOperationRepo.findByOrderIdAndProviderReference(order.getId(),reference);
        if(replay.isEmpty())return false;
        if("REFUND".equals(replay.get().getOperationType())&&java.util.Objects.equals(order.getRefundReference(),reference))return true;
        throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"This provider reference was already recorded for a different finance operation.");
    }
    private void applyFinancialFinality(SokoOrder order,String terminalStatus){
        clearDeliveryAuthorization(order);order.setSettlementStatus("BLOCKED");
        if(riderHasCustody(order)){order.setStatus("FINANCE_HOLD_RETURN_REQUIRED");return;}
        String previous=order.getStatus();
        if(!List.of("COMPLETED","RETURNED","FINANCE_HOLD_RETURNED","REFUNDED","PAYMENT_REVERSED").contains(previous))restoreStock(order);
        releaseRider(order,false);order.setStatus(terminalStatus);
    }
    private void applyFinancialReductionHold(SokoOrder order,BigDecimal collected){
        clearDeliveryAuthorization(order);order.setSettlementStatus("BLOCKED");
        if("COMPLETED".equals(order.getStatus()))return;
        if(List.of("CANCELLED","EXPIRED","RETURNED","FINANCE_HOLD_RETURNED","REFUNDED","PAYMENT_REVERSED").contains(order.getStatus())){
            requestOutstandingRefund(order,collected);return;
        }
        if(riderHasCustody(order))order.setStatus("FINANCE_HOLD_RETURN_REQUIRED");
        else{
            restoreStock(order);releaseRider(order,false);clearPriorAssignmentDisplay(order);order.setStatus("FINANCE_HOLD");
        }
        requestOutstandingRefund(order,collected);
    }
    private boolean riderHasCustody(SokoOrder order){return order.getRiderId()!=null&&order.getCollectedAt()!=null
            &&List.of("DISPATCHED","DELIVERY_RETURN_REQUIRED","FINANCE_HOLD_RETURN_REQUIRED").contains(order.getStatus());}
    private String financialTerminalStatus(SokoOrder order){return zero(order.getReversedAmount()).add(zero(order.getChargedBackAmount())).signum()>0?"PAYMENT_REVERSED":"REFUNDED";}
    private ZonedDateTime now(){return ZonedDateTime.now(ZoneId.of("UTC"));}
    private PMSCustomException notFound(){return new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND);}
    private PMSCustomException invalid(){return new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);}
    private PMSCustomException forbidden(){return new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS);}
    private static Double distanceKm(Double lat1,Double lng1,Double lat2,Double lng2){if(lat1==null||lng1==null||lat2==null||lng2==null)return null;double a=Math.sin(Math.toRadians(lat2-lat1)/2)*Math.sin(Math.toRadians(lat2-lat1)/2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(Math.toRadians(lng2-lng1)/2)*Math.sin(Math.toRadians(lng2-lng1)/2);return Math.round(6371*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a))*10.0)/10.0;}
    private static boolean validLatitude(Double value){return value!=null&&Double.isFinite(value)&&value>=-90&&value<=90;}
    private static boolean validLongitude(Double value){return value!=null&&Double.isFinite(value)&&value>=-180&&value<=180;}
    private static boolean sameCurrency(String first,String second){try{return org.pms.silverocean.service.payment.money.MonetaryPolicy.currency(first).equals(org.pms.silverocean.service.payment.money.MonetaryPolicy.currency(second));}catch(IllegalArgumentException invalid){return false;}}
    private static double exactDistanceKm(double lat1,double lng1,double lat2,double lng2){double latDelta=Math.toRadians(lat2-lat1),lngDelta=Math.toRadians(lng2-lng1);double a=Math.sin(latDelta/2)*Math.sin(latDelta/2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(lngDelta/2)*Math.sin(lngDelta/2);return 6371.0088*2*Math.atan2(Math.sqrt(a),Math.sqrt(Math.max(0,1-a)));}
    private record SystemDestination(long unitId,long propertyId,String label,String address,double latitude,double longitude,String source) {}
    private record CheckoutDestination(String address,Double latitude,Double longitude,Long unitId) {}
    private record GeoBounds(Double minLatitude,Double maxLatitude,Double minLongitude,Double maxLongitude,boolean wrapLongitude){
        private static GeoBounds of(Double latitude,Double longitude,Double radiusKm){
            if(latitude==null||longitude==null||radiusKm==null)return new GeoBounds(null,null,null,null,false);
            double angularDistance=radiusKm/6371.0088d;
            double latitudeDelta=Math.toDegrees(angularDistance);
            double minLatitude=Math.max(-90d,latitude-latitudeDelta),maxLatitude=Math.min(90d,latitude+latitudeDelta);
            if(minLatitude<=-90d||maxLatitude>=90d)return new GeoBounds(minLatitude,maxLatitude,-180d,180d,false);
            double longitudeRatio=Math.sin(angularDistance)/Math.cos(Math.toRadians(latitude));
            if(Math.abs(longitudeRatio)>=1d)return new GeoBounds(minLatitude,maxLatitude,-180d,180d,false);
            double longitudeDelta=Math.toDegrees(Math.asin(Math.abs(longitudeRatio)));
            double rawMin=longitude-longitudeDelta,rawMax=longitude+longitudeDelta;
            return new GeoBounds(minLatitude,maxLatitude,normalizeLongitude(rawMin),normalizeLongitude(rawMax),rawMin< -180d||rawMax>180d);
        }
        private static double normalizeLongitude(double longitude){double normalized=(longitude+540d)%360d-180d;return normalized==0d?0d:normalized;}
    }
}
