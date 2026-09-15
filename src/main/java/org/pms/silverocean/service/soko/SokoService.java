package org.pms.silverocean.service.soko;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.SokoOrderItemRepo;
import org.pms.silverocean.database.pms.SokoOrderRepo;
import org.pms.silverocean.database.pms.SokoProductRepo;
import org.pms.silverocean.database.pms.SokoProductImageRepo;
import org.pms.silverocean.database.pms.SokoProductVariationRepo;
import org.pms.silverocean.database.pms.SokoRiderRepo;
import org.pms.silverocean.database.pms.SokoStoreRepo;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.database.pms.entities.SokoOrder;
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
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.filestorage.UploadMalwarePolicy;
import org.pms.silverocean.service.security.EncryptionService;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.kyc.MarketplaceKycGate;
import org.pms.silverocean.service.users.ProfileType;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.soko.SokoModels.CatalogProduct;
import org.pms.silverocean.service.soko.SokoModels.OrderDetail;
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
    private static final SecureRandom SECURE_RANDOM=new SecureRandom();
    private static final java.util.regex.Pattern RESTRICTED_PRODUCT=java.util.regex.Pattern.compile("(?i)\\b(alcohol|beer|wine|spirit|cigarette|tobacco|vape|cannabis|marijuana|firearm|ammunition|weapon)\\b");
    private final SokoStoreRepo storeRepo;
    private final SokoProductRepo productRepo;
    private final SokoProductImageRepo productImageRepo;
    private final SokoProductVariationRepo productVariationRepo;
    private final SokoOrderRepo orderRepo;
    private final SokoOrderItemRepo itemRepo;
    private final SokoRiderRepo riderRepo;
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
    @Value("${soko.stock-reservation-minutes:20}") private long reservationMinutes;
    @Value("${soko.delivery-code-valid-hours:24}") private long deliveryCodeValidHours;
    @Value("${soko.delivery-recovery-otp-valid-minutes:10}") private long deliveryRecoveryOtpValidMinutes;
    @Value("${soko.delivery-recovery-cooldown-seconds:60}") private long deliveryRecoveryCooldownSeconds;
    @Value("${soko.delivery-recovery-max-requests-per-day:3}") private int deliveryRecoveryMaxRequestsPerDay;
    private static final ObjectMapper JSON = new ObjectMapper();
    @Autowired(required=false) private AuditLogService auditLogService;

    public Page<CatalogProduct> catalog(Pageable pageable, Long storeId, String category, String query, Double latitude, Double longitude, Double radiusKm) {
        String cleanCategory=StringUtils.trimToNull(category), cleanQuery=StringUtils.trimToNull(query);
        GeoBounds bounds=GeoBounds.of(latitude,longitude,radiusKm);
        Page<SokoProduct> page=productRepo.searchCatalog(bounded(pageable),storeId,cleanCategory,cleanQuery,latitude,longitude,bounds.minLat(),bounds.maxLat(),bounds.minLng(),bounds.maxLng());
        List<Long> productIds=page.stream().map(SokoProduct::getId).toList();
        Map<Long,List<String>> images=imageUrlsByProduct(productIds);
        hydrateVariationStock(page.getContent());
        Map<Long,SokoStore> stores=storeRepo.findAllById(page.stream().map(SokoProduct::getStoreId).distinct().toList())
                .stream().collect(Collectors.toMap(SokoStore::getId, Function.identity()));
        return page.map(p->{
            SokoStore s=stores.get(p.getStoreId());
            return new CatalogProduct(p,s==null?"Soko merchant":s.getName(),s!=null&&s.isDeliveryEnabled(),s!=null&&s.isPickupEnabled(),s==null?null:distanceKm(latitude,longitude,s.getLatitude(),s.getLongitude()),images.getOrDefault(p.getId(),legacyImage(p)),s==null?BigDecimal.ZERO:zero(s.getDeliveryFee()));
        });
    }

    public StoreDetail storeDetail(long id) {
        SokoStore s=storeRepo.findByIdAndActiveTrue(id).filter(x->PUBLISHED.equals(x.getStatus())).orElseThrow(this::notFound);
        List<SokoProduct> products=productRepo.findAllByStoreIdAndActiveTrueOrderByName(id).stream().filter(p->PUBLISHED.equals(p.getStatus())&&p.getStockQuantity()>0).toList();
        hydrateVariationStock(products);
        return new StoreDetail(s,products);
    }

    @Transactional
    public SokoStore createStore(SokoRequests.StoreUpsert request) {
        requireMerchantRole();
        SokoStore s=new SokoStore(); s.setOwnerUserId(userDao.getUserId()); s.setCreatedBy(userDao.getUserId()); s.setActive(true); s.setStatus(DRAFT);
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
        SokoProduct p=productRepo.findById(id).filter(SokoProduct::isActive).orElseThrow(this::notFound); SokoStore store=ownedStore(p.getStoreId()); if(store.getId()!=request.storeId())throw invalid();if(productIsInOpenOrder(id))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"This product is part of an open order. Finish or cancel that order before changing product stock or variations.");applyProduct(p,request,store);if(request.variations()!=null)syncVariations(p,request.variations());return productRepo.save(p);
    }

    @Transactional
    public SokoProduct publishProduct(long id){
        SokoProduct p=productRepo.findById(id).filter(SokoProduct::isActive).orElseThrow(this::notFound); SokoStore store=ownedStore(p.getStoreId());if(!PUBLISHED.equals(store.getStatus())||SUSPENDED.equals(p.getStatus()))throw invalid();
        marketplaceKycGate.require(store.getOwnerUserId(),"SOKO_CATEGORY",p.getCategory(),profileType(store.getOwnerUserId()));
        if(StringUtils.isBlank(p.getImageUrl())&&productImageRepo.findAllByProductIdAndActiveTrueOrderByDisplayOrderAsc(id).isEmpty())throw new PMSCustomException(ResponseCode.INVALID_IMAGE);
        p.setStatus(p.getStockQuantity()>0?PUBLISHED:OUT_OF_STOCK); return productRepo.save(p);
    }

    @Transactional
    public SokoProduct pauseProduct(long id){SokoProduct p=productRepo.findById(id).filter(SokoProduct::isActive).orElseThrow(this::notFound);ownedStore(p.getStoreId());if(!List.of(PUBLISHED,OUT_OF_STOCK).contains(p.getStatus()))throw invalid();p.setStatus("PAUSED");p=productRepo.save(p);audit(p,"SOKO_PRODUCT_PAUSED");return p;}

    @Transactional
    public void removeProduct(long id){SokoProduct p=productRepo.findById(id).filter(SokoProduct::isActive).orElseThrow(this::notFound);ownedStore(p.getStoreId());if(productIsInOpenOrder(id))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"This product is part of an open order. Pause it instead.");p.setActive(false);p.setStatus("REMOVED");productRepo.save(p);audit(p,"SOKO_PRODUCT_REMOVED");}

    public List<SokoProduct> myProducts(long storeId){ownedStore(storeId);List<SokoProduct> products=productRepo.findAllByStoreIdAndActiveTrueOrderByName(storeId);hydrateVariationStock(products);return products;}

    public SokoModels.AdminSummary adminSummary(){requireSuperAdmin();long orders=orderRepo.countByActiveTrue();long complete=orderRepo.countByStatusAndActiveTrue("COMPLETED")+orderRepo.countByStatusAndActiveTrue("CANCELLED")+orderRepo.countByStatusAndActiveTrue("EXPIRED");return new SokoModels.AdminSummary(storeRepo.countByActiveTrue(),storeRepo.countByStatusAndActiveTrue(PENDING_REVIEW),storeRepo.countByStatusAndActiveTrue(PUBLISHED),productRepo.countByActiveTrue(),productRepo.countByStatusAndActiveTrue(PUBLISHED),orders,Math.max(0,orders-complete));}
    public Page<SokoStore> adminStores(String status,Pageable pageable){requireSuperAdmin();Pageable safe=bounded(pageable);return StringUtils.isBlank(status)?storeRepo.findAllByActiveTrue(safe):storeRepo.findAllByStatusAndActiveTrue(status.trim().toUpperCase(Locale.ROOT),safe);}
    public Page<SokoProduct> adminProducts(Pageable pageable){requireSuperAdmin();return productRepo.findAllByActiveTrue(bounded(pageable));}
    public Page<OrderDetail> adminOrders(Pageable pageable){requireSuperAdmin();return hydrate(orderRepo.findAllByActiveTrue(bounded(pageable)));}
    @Transactional public SokoStore moderateStore(long id,SokoRequests.ModerationDecision request){requireSuperAdmin();SokoStore store=storeRepo.findByIdAndActiveTrue(id).orElseThrow(this::notFound);String decision=request.decision().toUpperCase(Locale.ROOT);if(("REJECT".equals(decision)||"SUSPEND".equals(decision))&&StringUtils.isBlank(request.reason()))throw invalid();switch(decision){case "APPROVE"->{if(!PENDING_REVIEW.equals(store.getStatus()))throw invalid();validatePublishable(store);marketplaceKycGate.require(store.getOwnerUserId(),"PROVIDER_TYPE","SOKO_MERCHANT",profileType(store.getOwnerUserId()));store.setStatus(PUBLISHED);}case "REJECT"->{if(!PENDING_REVIEW.equals(store.getStatus()))throw invalid();store.setStatus(REJECTED);}case "SUSPEND"->{if(!PUBLISHED.equals(store.getStatus()))throw invalid();store.setStatus(SUSPENDED);}case "REACTIVATE"->{if(!SUSPENDED.equals(store.getStatus()))throw invalid();validatePublishable(store);marketplaceKycGate.require(store.getOwnerUserId(),"PROVIDER_TYPE","SOKO_MERCHANT",profileType(store.getOwnerUserId()));store.setStatus(PUBLISHED);}default->throw invalid();}store.setReviewedAt(now());store.setReviewedByUserId(userDao.getUserId());store.setReviewReason(StringUtils.left(StringUtils.trimToNull(request.reason()),1000));store=storeRepo.save(store);notifyModeration(store.getOwnerUserId(),store.getName(),store.getStatus(),store.getReviewReason());return store;}
    @Transactional public SokoProduct moderateProduct(long id,SokoRequests.ModerationDecision request){requireSuperAdmin();SokoProduct product=productRepo.findById(id).filter(SokoProduct::isActive).orElseThrow(this::notFound);String decision=request.decision().toUpperCase(Locale.ROOT);if("SUSPEND".equals(decision)){if(StringUtils.isBlank(request.reason())||!List.of(PUBLISHED,OUT_OF_STOCK).contains(product.getStatus()))throw invalid();product.setStatus(SUSPENDED);}else if("REACTIVATE".equals(decision)){if(!SUSPENDED.equals(product.getStatus()))throw invalid();SokoStore store=storeRepo.findByIdAndActiveTrue(product.getStoreId()).orElseThrow(this::notFound);if(!PUBLISHED.equals(store.getStatus()))throw invalid();marketplaceKycGate.require(store.getOwnerUserId(),"SOKO_CATEGORY",product.getCategory(),profileType(store.getOwnerUserId()));product.setStatus(product.getStockQuantity()>0?PUBLISHED:OUT_OF_STOCK);}else throw invalid();product.setModeratedAt(now());product.setModeratedByUserId(userDao.getUserId());product.setModerationReason(StringUtils.left(StringUtils.trimToNull(request.reason()),1000));product=productRepo.save(product);SokoStore owner=storeRepo.findByIdAndActiveTrue(product.getStoreId()).orElseThrow(this::notFound);notifyModeration(owner.getOwnerUserId(),product.getName(),product.getStatus(),product.getModerationReason());return product;}

    @Transactional
    public SokoModels.ProductImages replaceProductImages(long productId, List<MultipartFile> images) throws IOException {
        SokoProduct product=productRepo.findById(productId).filter(SokoProduct::isActive).orElseThrow(this::notFound);
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
    public SokoRider createRider(SokoRequests.RiderUpsert request){
        ownedStore(request.storeId());
        String phone=request.phoneNumber().trim();
        if(riderRepo.existsByStoreIdAndPhoneNumberAndActiveTrue(request.storeId(),phone))throw invalid();
        SokoRider rider=new SokoRider();rider.setStoreId(request.storeId());rider.setCreatedBy(userDao.getUserId());rider.setActive(true);rider.setStatus("PENDING_VERIFICATION");rider.setVerificationStatus("PENDING_VERIFICATION");rider.setAvailability("OFFLINE");rider.setCompletedDeliveries(0);rider.setVerified(false);applyRider(rider,request);linkRiderUser(rider);return riderRepo.save(rider);
    }

    @Transactional
    public SokoRider updateRider(long id,SokoRequests.RiderUpsert request){
        ownedStore(request.storeId());SokoRider rider=riderRepo.findForUpdate(id,request.storeId()).orElseThrow(this::notFound);
        requireRiderIdle(rider);
        String phone=request.phoneNumber().trim();if(!phone.equals(rider.getPhoneNumber())&&riderRepo.existsByStoreIdAndPhoneNumberAndActiveTrue(request.storeId(),phone))throw invalid();
        boolean identityChanged=!java.util.Objects.equals(rider.getDisplayName(),request.displayName().trim())
                ||!java.util.Objects.equals(rider.getPhoneNumber(),phone)
                ||!java.util.Objects.equals(StringUtils.lowerCase(StringUtils.trimToNull(rider.getEmail())),StringUtils.lowerCase(StringUtils.trimToNull(request.email())))
                ||!java.util.Objects.equals(rider.getRiderType(),request.riderType().trim().toUpperCase(Locale.ROOT))
                ||!java.util.Objects.equals(rider.getVehicleType(),StringUtils.trimToNull(request.vehicleType()))
                ||!java.util.Objects.equals(rider.getVehiclePlate(),StringUtils.trimToNull(request.vehiclePlate()));
        applyRider(rider,request);if(identityChanged)linkRiderUser(rider);
        if(identityChanged){rider.setVerificationStatus("PENDING_VERIFICATION");rider.setVerified(false);rider.setStatus("PENDING_VERIFICATION");rider.setAvailability("OFFLINE");rider.setVerifiedAt(null);rider.setVerifiedByUserId(null);}
        rider=riderRepo.save(rider);audit(rider,"SOKO_RIDER_UPDATED");return rider;
    }

    public List<SokoRider> myRiders(long storeId){ownedStore(storeId);return riderRepo.findAllByStoreIdAndActiveTrueOrderByDisplayName(storeId);}

    public Page<OrderDetail> riderAssignments(Pageable pageable){List<Long> ids=riderRepo.findAllByUserIdAndActiveTrue(userDao.getUserId()).stream().map(SokoRider::getId).toList();return ids.isEmpty()?Page.empty(bounded(pageable)):hydrate(orderRepo.findAllByRiderIdInAndActiveTrue(ids,bounded(pageable)));}
    public Page<SokoRider> adminRiders(Pageable pageable){requireSuperAdmin();return riderRepo.findAllByActiveTrue(bounded(pageable));}

    @Transactional public SokoRider riderDecision(long id,SokoRequests.RiderDecision request){
        requireSuperAdmin();SokoRider r=riderRepo.findByIdForUpdate(id).orElseThrow(this::notFound);
        String decision=request.decision().toUpperCase(Locale.ROOT);requireRiderIdle(r);
        if(("SUSPEND".equals(decision)||"REJECT".equals(decision))&&StringUtils.isBlank(request.reason()))throw invalid();
        switch(decision){
            case "VERIFY","ACTIVATE"->{
                linkRiderUser(r);
                if(r.getUserId()==null)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Ask the rider to register or sign in using the email recorded by the merchant, then retry verification.");
                var riderUser=userDao.findById(r.getUserId()).filter(u->u.isActive()&&u.isVerified()&&u.isEmailVerified()&&"ACTIVE".equals(u.getAccountStatus())).orElseThrow(()->new PMSCustomException(ResponseCode.KYC_MISSING_DOCUMENTS,"The rider must complete their existing account KYC and verify their email before activation."));
                marketplaceKycGate.require(r.getUserId(),"PROVIDER_TYPE","DELIVERY_RIDER",profileType(r.getUserId()));
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

    private void requireRiderIdle(SokoRider rider){if("BUSY".equals(rider.getAvailability())||orderRepo.existsByRiderIdAndStatusInAndActiveTrue(rider.getId(),List.of("DELIVERY_ASSIGNED","ASSIGNMENT_ACCEPTED","DISPATCHED")))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Finish the rider's active delivery before editing, verifying, suspending or removing this rider.");}

    @Transactional
    public SokoRider setRiderAvailability(long id,String requested){
        SokoRider rider=riderRepo.findByIdForUpdate(id).orElseThrow(this::notFound);ownedStore(rider.getStoreId());String availability=requested.toUpperCase(Locale.ROOT);
        requireRiderIdle(rider);
        if(!rider.isVerified()||!"ACTIVE".equals(rider.getStatus())||!List.of("AVAILABLE","OFFLINE").contains(availability)||"BUSY".equals(rider.getAvailability()))throw invalid();rider.setAvailability(availability);return riderRepo.save(rider);
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
        String cleanKey=StringUtils.defaultIfBlank(StringUtils.trimToNull(idempotencyKey),UUID.randomUUID().toString());
        if(cleanKey.length()>80||!cleanKey.matches("[A-Za-z0-9._:-]+"))throw invalid();
        var existing=orderRepo.findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(customerUserId,cleanKey);
        if(existing.isPresent())return detail(existing.get());
        SokoStore store=storeRepo.findByIdAndActiveTrue(request.storeId()).filter(s->PUBLISHED.equals(s.getStatus())).orElseThrow(this::notFound);
        validateDelivery(store,request);
        validatePublishable(store);
        if(request.items().stream().map(line->line.productId()+":"+String.valueOf(line.variationId())).distinct().count()!=request.items().size())throw invalid();
        List<SokoProduct> products=new ArrayList<>();List<SokoProductVariation> selectedVariations=new ArrayList<>();List<BigDecimal> unitPrices=new ArrayList<>(); BigDecimal subtotal=BigDecimal.ZERO;
        for(SokoRequests.CheckoutItem line:request.items()){
            SokoProduct p=productRepo.findByIdForUpdate(line.productId()).filter(x->x.getStoreId()==store.getId()&&PUBLISHED.equals(x.getStatus())).orElseThrow(this::notFound);
            if(p.getStockQuantity()<line.quantity())throw new PMSCustomException(ResponseCode.INVALID_AMOUNT,"Insufficient stock for "+p.getName());
            List<SokoProductVariation> available=productVariationRepo.findAllByProductIdAndActiveTrueOrderByNameAscValueAsc(p.getId());SokoProductVariation selected=null;
            if(!available.isEmpty()){if(line.variationId()==null)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Choose a variation for "+p.getName()+".");selected=productVariationRepo.findForUpdate(line.variationId(),p.getId()).orElseThrow(this::notFound);if(selected.getStockQuantity()<line.quantity())throw new PMSCustomException(ResponseCode.INVALID_AMOUNT,"Insufficient stock for "+p.getName()+" ("+selected.getValue()+")");selected.setStockQuantity(selected.getStockQuantity()-line.quantity());productVariationRepo.save(selected);}else if(line.variationId()!=null)throw invalid();
            BigDecimal unitPrice=p.getPrice().add(selected==null?BigDecimal.ZERO:zero(selected.getPriceAdjustment()));p.setStockQuantity(p.getStockQuantity()-line.quantity()); if(p.getStockQuantity()==0)p.setStatus(OUT_OF_STOCK); productRepo.save(p); products.add(p);selectedVariations.add(selected);unitPrices.add(unitPrice);
            subtotal=subtotal.add(unitPrice.multiply(BigDecimal.valueOf(line.quantity())));
        }
        BigDecimal fee="DELIVERY".equalsIgnoreCase(request.deliveryMethod())?zero(store.getDeliveryFee()):BigDecimal.ZERO;
        SokoOrder o=new SokoOrder(); o.setOrderNumber("SOKO-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT)); o.setStoreId(store.getId()); o.setCustomerUserId(customerUserId); o.setCreatedBy(customerUserId); o.setCheckoutIdempotencyKey(cleanKey); o.setActive(true); o.setStatus("PENDING_PAYMENT"); o.setPaymentStatus("UNPAID");o.setRefundStatus("NOT_REQUIRED");o.setSettlementStatus("PENDING");o.setReservationExpiresAt(now().plusMinutes(reservationMinutes)); o.setDeliveryMethod(request.deliveryMethod().toUpperCase(Locale.ROOT)); o.setDeliveryAddress(StringUtils.trimToNull(request.deliveryAddress())); o.setCustomerPhone(request.customerPhone()); o.setNotes(StringUtils.trimToNull(request.notes())); o.setDestinationUnitId(request.destinationUnitId()); o.setSubtotal(subtotal); o.setDeliveryFee(fee); o.setTotal(subtotal.add(fee)); o.setCurrency(store.getCurrency());o.setPaymentAccountId(store.getPaymentAccountId());o.setPaymentChannel(accountDao.getAccountById(store.getPaymentAccountId()).getChannel().name()); o.setPlacedAt(now()); orderRepo.save(o);
        List<SokoOrderItem> items=new ArrayList<>();
        for(int i=0;i<products.size();i++){SokoProduct p=products.get(i);SokoProductVariation v=selectedVariations.get(i);BigDecimal unitPrice=unitPrices.get(i);int qty=request.items().get(i).quantity();SokoOrderItem it=new SokoOrderItem();it.setOrderId(o.getId());it.setProductId(p.getId());it.setProductName(p.getName());if(v!=null){it.setVariationId(v.getId());it.setVariationName(v.getName());it.setVariationValue(v.getValue());}it.setUnit(p.getUnit());it.setUnitPrice(unitPrice);it.setQuantity(qty);it.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(qty)));it.setCreatedBy(userDao.getUserId());it.setActive(true);items.add(itemRepo.save(it));}
        PMSInvoice invoice=createInvoice(o,store,items);o.setInvoiceRef(invoice.getRef());orderRepo.save(o);notifyOrder(o,"Awaiting payment","");return detail(o,store,items);
    }

    public Page<OrderDetail> myOrders(Pageable pageable){return hydrate(orderRepo.findAllByCustomerUserIdAndActiveTrue(userDao.getUserId(),bounded(pageable)));}
    public Page<OrderDetail> merchantOrders(Pageable pageable){List<Long> ids=myStores().stream().map(SokoStore::getId).toList();if(ids.isEmpty())return Page.empty(bounded(pageable));return hydrate(orderRepo.findAllByStoreIdInAndActiveTrue(ids,bounded(pageable)));}

    public String deliveryCode(long orderId){SokoOrder o=orderRepo.findById(orderId).orElseThrow(this::notFound);if(o.getCustomerUserId()!=userDao.getUserId())throw forbidden();String code=decryptDeliveryCode(o);if(!"DELIVERY".equals(o.getDeliveryMethod())||StringUtils.isBlank(code)||!"DISPATCHED".equals(o.getStatus())||o.isDeliveryCodeVerified()||codeExpired(o)||o.getDeliveryCodeLockedAt()!=null)throw invalid();return code;}

    @Transactional public OrderDetail uploadDeliveryProof(long orderId,MultipartFile proof)throws IOException{SokoOrder o=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);requireMerchantOrAssignedRider(o);if(!"DISPATCHED".equals(o.getStatus())||StringUtils.isNotBlank(o.getDeliveryProofReference())||proof==null||proof.isEmpty()||proof.getSize()>5L*1024*1024)throw invalid();String type=StringUtils.defaultString(proof.getContentType()).toLowerCase(Locale.ROOT);byte[] bytes=proof.getBytes();if(!validProof(type,bytes))throw new PMSCustomException(ResponseCode.INVALID_IMAGE);malwarePolicy.requireSafe(bytes);String extension="image/png".equals(type)?"png":"jpg";String ref="soko/delivery-proof/"+o.getId()+"/"+UUID.randomUUID()+"."+extension;garageService.uploadBytes(ref,bytes,type);o.setDeliveryProofReference(ref);o.setDeliveryProofContentType(type);o.setDeliveryProofSize((long)bytes.length);o.setDeliveryProofAt(now());orderRepo.save(o);return detail(o);}
    public String deliveryProof(long orderId){SokoOrder o=orderRepo.findById(orderId).filter(x->x.isActive()).orElseThrow(this::notFound);SokoStore store=storeRepo.findByIdAndActiveTrue(o.getStoreId()).orElseThrow(this::notFound);if(o.getCustomerUserId()!=userDao.getUserId()&&store.getOwnerUserId()!=userDao.getUserId())throw forbidden();if(StringUtils.isBlank(o.getDeliveryProofReference()))throw notFound();return garageService.getPresignedUrlForStoredObject(o.getDeliveryProofReference());}

    @Transactional(noRollbackFor=PMSCustomException.class)
    public OrderDetail confirmDelivery(long orderId,SokoRequests.DeliveryConfirmation request){SokoOrder o=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);SokoStore store=requireMerchantOrAssignedRider(o);String code=decryptDeliveryCode(o);if(!"DELIVERY".equals(o.getDeliveryMethod())||!"DISPATCHED".equals(o.getStatus())||o.isDeliveryCodeVerified()||StringUtils.isBlank(code)||StringUtils.isBlank(o.getDeliveryProofReference())||codeExpired(o)||o.getDeliveryCodeLockedAt()!=null)throw invalid();if(o.getDeliveryCodeAttempts()>=5){o.setDeliveryCodeLockedAt(now());orderRepo.save(o);throw forbidden();}o.setDeliveryCodeAttempts(o.getDeliveryCodeAttempts()+1);if(!constantTimeEquals(code,request.code())){if(o.getDeliveryCodeAttempts()>=5)o.setDeliveryCodeLockedAt(now());orderRepo.save(o);throw invalid();}o.setDeliveryCodeVerified(true);o.setDeliveryCode(null);o.setEncryptedDeliveryCode(null);o.setDeliveryRecipientName(StringUtils.left(StringUtils.trimToNull(request.recipientName()),160));o.setDeliveryProofAt(now());o.setStatus("COMPLETED");o.setCompletedAt(now());releaseRider(o,true);orderRepo.save(o);audit(o,"SOKO_DELIVERY_COMPLETED");notifyOrder(o,"Delivered","Delivery was verified using the buyer's single-use code.");return detail(o,store,itemRepo.findAllByOrderIdAndActiveTrueOrderById(o.getId()));}

    @Transactional
    public OrderDetail transition(long orderId,String requested,SokoRequests.Dispatch dispatch){
        SokoOrder o=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);SokoStore store=storeRepo.findByIdAndActiveTrue(o.getStoreId()).orElseThrow(this::notFound);String next=requested.toUpperCase(Locale.ROOT);
        boolean customer=o.getCustomerUserId()==userDao.getUserId(),merchant=store.getOwnerUserId()==userDao.getUserId();
        if("CANCELLED".equals(next))throw invalid();
        else {if(!merchant)throw forbidden(); switch(next){case "CONFIRMED"->{requireState(o,"PAID");o.setConfirmedAt(now());}case "PACKED"->requireState(o,"CONFIRMED");case "DISPATCHED"->{requireState(o,"PACKED");if(!"DELIVERY".equals(o.getDeliveryMethod()))throw invalid();boolean managed=assignAndRegisterDelivery(o,dispatch);if(managed){next="DELIVERY_ASSIGNED";o.setAssignedAt(now());}else{generateDeliveryCode(o);o.setDispatchedAt(now());}}case "COMPLETED"->{requireState(o,"READY_FOR_PICKUP");if(!"PICKUP".equals(o.getDeliveryMethod()))throw invalid();o.setCompletedAt(now());}case "READY_FOR_PICKUP"->{requireState(o,"PACKED");if(!"PICKUP".equals(o.getDeliveryMethod()))throw invalid();}default->throw invalid();}o.setStatus(next);}
        orderRepo.save(o);audit(o,"SOKO_ORDER_"+next);notifyOrder(o,o.getStatus().replace('_',' '),"Your order progress was updated by the merchant.");return detail(o);
    }

    @Transactional public OrderDetail acceptAssignment(long orderId){SokoOrder o=assignedOrder(orderId,"DELIVERY_ASSIGNED");o.setStatus("ASSIGNMENT_ACCEPTED");o.setAssignmentAcceptedAt(now());orderRepo.save(o);audit(o,"SOKO_DELIVERY_ACCEPTED");notifyOrder(o,"Rider assigned","Your verified rider accepted the delivery assignment.");return detail(o);}
    @Transactional public OrderDetail confirmCollection(long orderId){SokoOrder o=assignedOrder(orderId,"ASSIGNMENT_ACCEPTED");o.setStatus("DISPATCHED");o.setCollectedAt(now());o.setDispatchedAt(now());generateDeliveryCode(o);orderRepo.save(o);audit(o,"SOKO_DELIVERY_COLLECTED");notifyOrder(o,"Out for delivery","The rider collected your order and is on the way.");return detail(o);}
    @Transactional public OrderDetail failDelivery(long orderId,SokoRequests.DeliveryException request){SokoOrder o=assignedOrder(orderId,null);if(!List.of("DELIVERY_ASSIGNED","ASSIGNMENT_ACCEPTED","DISPATCHED").contains(o.getStatus()))throw invalid();o.setStatus("DELIVERY_FAILED");o.setDeliveryFailedAt(now());o.setDeliveryExceptionReason(request.reason().trim());clearDeliveryCode(o);releaseRider(o,false);orderRepo.save(o);audit(o,"SOKO_DELIVERY_FAILED");notifyOrder(o,"Delivery attempt failed",request.reason());return detail(o);}
    @Transactional public OrderDetail returnDelivery(long orderId,SokoRequests.DeliveryException request){SokoOrder o=assignedOrder(orderId,"DELIVERY_FAILED");o.setStatus("RETURNED");o.setReturnedAt(now());o.setDeliveryExceptionReason(request.reason().trim());if("PAID".equals(o.getPaymentStatus()))o.setRefundStatus("REQUESTED");orderRepo.save(o);audit(o,"SOKO_DELIVERY_RETURNED");notifyOrder(o,"Order returned",request.reason());return detail(o);}
    @Transactional public OrderDetail reissueDeliveryCode(long orderId,SokoRequests.CodeReissue request){requireSuperAdmin();return requestDeliveryCodeRecovery(orderId,request.reason());}

    @Transactional
    public OrderDetail requestDeliveryCodeRecovery(long orderId,String supportReason){
        SokoOrder o=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);
        long actor=userDao.getUserId();boolean support=StringUtils.isNotBlank(supportReason);
        if(support)requireSuperAdmin();else if(o.getCustomerUserId()!=actor)throw forbidden();
        if(!"DELIVERY".equals(o.getDeliveryMethod())||!"DISPATCHED".equals(o.getStatus())||o.isDeliveryCodeVerified())throw invalid();
        var buyer=userDao.findById(o.getCustomerUserId()).filter(u->u.isActive()&&u.isEmailVerified()&&StringUtils.isNotBlank(u.getEmail()))
                .orElseThrow(()->new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Verify the buyer's email before recovering a delivery code."));
        ZonedDateTime timestamp=now();
        if(o.getDeliveryRecoveryWindowStartedAt()==null||o.getDeliveryRecoveryWindowStartedAt().plusHours(24).isBefore(timestamp)){
            o.setDeliveryRecoveryWindowStartedAt(timestamp);o.setDeliveryRecoveryRequestCount(0);
        }
        if(o.getDeliveryRecoveryRequestedAt()!=null&&o.getDeliveryRecoveryRequestedAt().plusSeconds(Math.max(30,deliveryRecoveryCooldownSeconds)).isAfter(timestamp))
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"A verification code was just sent. Wait one minute before requesting another.");
        if(o.getDeliveryRecoveryRequestCount()>=Math.max(1,deliveryRecoveryMaxRequestsPerDay))
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Delivery-code recovery has reached today's limit. Contact support and try again after 24 hours.");
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
        if(!"DISPATCHED".equals(o.getStatus())||o.isDeliveryCodeVerified()||o.getDeliveryRecoveryOtp()==null||o.getDeliveryRecoveryOtpExpiresAt()==null||!o.getDeliveryRecoveryOtpExpiresAt().isAfter(now()))throw invalid();
        if(o.getDeliveryRecoveryOtpAttempts()>=5)throw forbidden();
        o.setDeliveryRecoveryOtpAttempts(o.getDeliveryRecoveryOtpAttempts()+1);
        String expected=encryptionService.decrypt(o.getDeliveryRecoveryOtp()).decryptedValue();
        if(!constantTimeEquals(expected,request.otp())){orderRepo.save(o);throw invalid();}
        generateDeliveryCode(o);o.setDeliveryCodeReissuedAt(now());o.setDeliveryCodeReissuedBy(userDao.getUserId());o.setDeliveryCodeReissueReason("Buyer identity confirmed using a fresh email OTP.");
        o.setDeliveryRecoveryOtp(null);o.setDeliveryRecoveryOtpExpiresAt(null);o.setDeliveryRecoveryCompletedAt(now());
        orderRepo.save(o);audit(o,"SOKO_DELIVERY_CODE_RECOVERED");return detail(o);
    }

    @Transactional
    public void completePaidInvoice(String invoiceRef,String providerReference){orderRepo.findByInvoiceRefAndActiveTrue(invoiceRef).ifPresent(o->{if("UNPAID".equals(o.getPaymentStatus())){o.setPaymentStatus("PAID");if("EXPIRED".equals(o.getStatus())||"CANCELLED".equals(o.getStatus()))o.setRefundStatus("REQUESTED");else o.setStatus("PAID");orderRepo.save(o);audit(o,"SOKO_PAYMENT_CONFIRMED");notifyOrder(o,"Payment confirmed","");}});}

    @Transactional public void completeFinanceOperation(String invoiceRef,String type,BigDecimal amount,String providerReference){orderRepo.findByInvoiceRefAndActiveTrue(invoiceRef).ifPresent(o->{BigDecimal total=zero(o.getRefundedAmount()).add(amount);o.setRefundedAmount(total);o.setRefundReference(StringUtils.left(providerReference,120));o.setRefundStatus("CONFIRMED");if("REFUND".equals(type)){boolean full=total.compareTo(o.getTotal())>=0;o.setPaymentStatus(full?"REFUNDED":"PARTIALLY_REFUNDED");if(full)o.setStatus("REFUNDED");notifyOrder(o,full?"Refund completed":"Partial refund completed",o.getCurrency()+" "+amount.toPlainString()+" was refunded.");}else{o.setPaymentStatus("REVERSED");o.setStatus("PAYMENT_REVERSED");o.setSettlementStatus("BLOCKED");notifyOrder(o,"Payment reversed","The payment provider confirmed a "+type.toLowerCase(Locale.ROOT)+". The order is no longer treated as paid.");}orderRepo.save(o);audit(o,"SOKO_PAYMENT_"+type);});}

    @Transactional public OrderDetail cancel(long orderId,SokoRequests.Cancellation request){SokoOrder o=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);SokoStore s=storeRepo.findByIdAndActiveTrue(o.getStoreId()).orElseThrow(this::notFound);boolean allowed=o.getCustomerUserId()==userDao.getUserId()||s.getOwnerUserId()==userDao.getUserId();if(!allowed)throw forbidden();if(!List.of("PENDING_PAYMENT","PAID","CONFIRMED").contains(o.getStatus()))throw invalid();restoreStock(o);releaseRider(o,false);o.setCancellationReason(request.reason().trim());o.setCancelledAt(now());o.setStatus("CANCELLED");if("PAID".equals(o.getPaymentStatus()))o.setRefundStatus("REQUESTED");orderRepo.save(o);audit(o,"SOKO_ORDER_CANCELLED");notifyOrder(o,"Cancelled","");return detail(o);}
    @Transactional public OrderDetail finance(long orderId,SokoRequests.FinanceUpdate r){if(!userDao.hasRole(PMSRole.FINANCE)&&!userDao.hasRole(PMSRole.SUPER_ADMIN))throw forbidden();SokoOrder o=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);
        boolean refund=r.type()==SokoRequests.FinanceType.REFUND;
        var previous=new org.pms.silverocean.service.payment.MarketplaceFinanceGuard.Entry(refund?o.getRefundStatus():o.getSettlementStatus(),refund?o.getRefundedAmount():o.getSettledAmount(),refund?o.getRefundReference():o.getSettlementReference());
        var other=new org.pms.silverocean.service.payment.MarketplaceFinanceGuard.Entry(refund?o.getSettlementStatus():o.getRefundStatus(),refund?o.getSettledAmount():o.getRefundedAmount(),null);
        if(org.pms.silverocean.service.payment.MarketplaceFinanceGuard.validate(o.getTotal(),"PAID".equals(o.getPaymentStatus()),"COMPLETED".equals(o.getStatus()),refund,new org.pms.silverocean.service.payment.MarketplaceFinanceGuard.Entry(r.status().name(),r.amount(),StringUtils.trimToNull(r.providerReference())),previous,other))return detail(o);
        if(r.status()==SokoRequests.FinanceStatus.CONFIRMED&&StringUtils.isBlank(r.providerReference()))throw invalid();if(r.type()==SokoRequests.FinanceType.REFUND){if(!"PAID".equals(o.getPaymentStatus())||r.amount().compareTo(o.getTotal())>0)throw invalid();o.setRefundStatus(r.status().name());o.setRefundedAmount(r.amount());o.setRefundReference(StringUtils.left(StringUtils.trimToNull(r.providerReference()),120));}else{BigDecimal refundable=o.getRefundedAmount()==null?BigDecimal.ZERO:o.getRefundedAmount();if(!"COMPLETED".equals(o.getStatus())||r.amount().compareTo(o.getTotal().subtract(refundable))>0)throw invalid();o.setSettlementStatus(r.status().name());o.setSettledAmount(r.amount());o.setSettlementReference(StringUtils.left(StringUtils.trimToNull(r.providerReference()),120));}orderRepo.save(o);notifyFinanceRecord(o,refund);return detail(o);}
    @Scheduled(fixedDelayString="${soko.reservation-expiry-scan-ms:300000}") @Transactional public void expireReservations(){for(SokoOrder o:orderRepo.findExpiredReservations(now(),PageRequest.of(0,100,Sort.by("reservationExpiresAt")))){restoreStock(o);o.setStatus("EXPIRED");o.setCancelledAt(now());o.setCancellationReason("Payment reservation expired");orderRepo.save(o);audit(o,"SOKO_RESERVATION_EXPIRED");notifyOrder(o,"Payment reservation expired","");}}

    private void applyStore(SokoStore s,SokoRequests.StoreUpsert r){s.setName(r.name().trim());s.setDescription(StringUtils.trimToNull(r.description()));s.setPhoneNumber(StringUtils.trimToNull(r.phoneNumber()));s.setAddress(StringUtils.trimToNull(r.address()));s.setLatitude(r.latitude());s.setLongitude(r.longitude());s.setServiceRadiusKm(r.serviceRadiusKm()==null?BigDecimal.valueOf(25):r.serviceRadiusKm());s.setPickupEnabled(r.pickupEnabled());s.setDeliveryEnabled(r.deliveryEnabled());s.setDeliveryFee(zero(r.deliveryFee()));s.setCurrency(r.currency().trim().toUpperCase(Locale.ROOT));s.setPaymentAccountId(r.paymentAccountId());if(!s.isPickupEnabled()&&!s.isDeliveryEnabled())throw invalid();if((s.getLatitude()==null)!=(s.getLongitude()==null))throw invalid();}
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
    private void applyRider(SokoRider rider,SokoRequests.RiderUpsert r){String type=r.riderType().trim().toUpperCase(Locale.ROOT);if(!List.of("INDIVIDUAL","DELIVERY_COMPANY").contains(type))throw invalid();rider.setRiderType(type);rider.setDisplayName(r.displayName().trim());rider.setPhoneNumber(r.phoneNumber().trim());rider.setEmail(StringUtils.trimToNull(r.email()));rider.setVehicleType(StringUtils.trimToNull(r.vehicleType()));rider.setVehiclePlate(StringUtils.trimToNull(r.vehiclePlate()));rider.setNotes(StringUtils.trimToNull(r.notes()));}
    private void linkRiderUser(SokoRider rider){rider.setUserId(StringUtils.isBlank(rider.getEmail())?null:userDao.findByEmail(rider.getEmail().trim().toLowerCase(Locale.ROOT)).map(u->u.getId()).orElse(null));}
    private ProfileType profileType(long userId){return userDao.findById(userId).map(u->{try{return ProfileType.valueOf(u.getProfileType());}catch(Exception ignored){return ProfileType.INDIVIDUAL;}}).orElse(ProfileType.INDIVIDUAL);}
    private void validatePublishable(SokoStore s){if(s.getPaymentAccountId()==null)throw new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND);PaymentAccount a=accountDao.getAccountByIdAndCreatedBy(s.getPaymentAccountId(),s.getOwnerUserId());if(!a.isVerified()||!a.isActive()||a.getCategory()!=AccountCategory.MERCHANT||a.getChannel()==null)throw new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND);}
    private void validateDelivery(SokoStore s,SokoRequests.Checkout r){if("DELIVERY".equalsIgnoreCase(r.deliveryMethod())){if(!s.isDeliveryEnabled()||StringUtils.isBlank(r.deliveryAddress()))throw invalid();}else if("PICKUP".equalsIgnoreCase(r.deliveryMethod())){if(!s.isPickupEnabled())throw invalid();}else throw invalid();}
    private PMSInvoice createInvoice(SokoOrder o,SokoStore s,List<SokoOrderItem> items){PMSInvoice inv=new PMSInvoice();inv.setUnitId(o.getDestinationUnitId()==null?0:o.getDestinationUnitId());inv.setPropertyId(0);inv.setDescription(("Soko order "+o.getOrderNumber()).getBytes(StandardCharsets.UTF_8));String html=items.stream().map(i->"<tr><td><span>"+HtmlUtils.htmlEscape(i.getProductName())+" x "+i.getQuantity()+"</span></td><td class='amount-col'>"+i.getLineTotal()+"</td></tr>").collect(Collectors.joining());inv.setHtmlDescription(html.getBytes(StandardCharsets.UTF_8));inv.setAmount(o.getTotal().doubleValue());inv.setPendingAmount(o.getTotal().doubleValue());inv.setCurrency(o.getCurrency());inv.setBilledUserId(o.getCustomerUserId());inv.setPayToUserId(s.getOwnerUserId());inv.setActive(true);inv.setBillingType("SOKO");inv.setCustomerPhoneNumber(o.getCustomerPhone());userDao.findById(o.getCustomerUserId()).ifPresent(u->inv.setCustomerEmail(u.getEmail()));invoiceDao.createInvoice(inv);return inv;}
    private boolean assignAndRegisterDelivery(SokoOrder o,SokoRequests.Dispatch d){
        if(d==null||d.riderId()==null)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Select a verified, registered and available SlickHood rider. One-off courier dispatch is no longer available.");
        ZonedDateTime expectedArrival=d.expectedArrivalTime().atZone(ZoneId.of("Africa/Nairobi")).withZoneSameInstant(ZoneId.of("UTC"));
        if(!expectedArrival.isAfter(now()))throw invalid();
        if(d.riderId()!=null){SokoRider rider=riderRepo.findForUpdate(d.riderId(),o.getStoreId()).orElseThrow(this::notFound);if(!rider.isVerified()||rider.getUserId()==null||!"ACTIVE".equals(rider.getStatus())||!"AVAILABLE".equals(rider.getAvailability()))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Choose a verified, active and available rider.");marketplaceKycGate.require(rider.getUserId(),"PROVIDER_TYPE","DELIVERY_RIDER",profileType(rider.getUserId()));rider.setAvailability("BUSY");riderRepo.save(rider);o.setRiderId(rider.getId());o.setCourierName(rider.getDisplayName());o.setCourierPhone(rider.getPhoneNumber());o.setCourierVehiclePlate(rider.getVehiclePlate());}
        o.setExpectedArrivalAt(expectedArrival);
        if(o.getDestinationUnitId()!=null){var visitor=visitorService.preRegisterDeliveryForHost(o.getCustomerUserId(),new CreateVisitorRequest(o.getCourierName(),o.getCourierVehiclePlate(),d.expectedArrivalTime(),null,false,o.getDestinationUnitId(),o.getCourierPhone(), VisitorCategory.DELIVERY));o.setDeliveryVisitorId(visitor.getId());}
        return d.riderId()!=null;
    }
    private void generateDeliveryCode(SokoOrder o){String code=String.format(Locale.ROOT,"%06d",SECURE_RANDOM.nextInt(1_000_000));o.setDeliveryCode(null);o.setEncryptedDeliveryCode(encryptionService.encrypt(code));o.setDeliveryCodeAttempts(0);o.setDeliveryCodeLockedAt(null);o.setDeliveryCodeVerified(false);o.setDeliveryCodeExpiresAt(now().plusHours(Math.max(1,Math.min(deliveryCodeValidHours,72))));notifyDeliveryCode(o,code);}
    private void clearDeliveryCode(SokoOrder o){o.setDeliveryCode(null);o.setEncryptedDeliveryCode(null);o.setDeliveryCodeExpiresAt(null);o.setDeliveryCodeLockedAt(null);}
    private boolean codeExpired(SokoOrder o){return o.getDeliveryCodeExpiresAt()==null||!o.getDeliveryCodeExpiresAt().isAfter(now());}
    private SokoOrder assignedOrder(long id,String required){SokoOrder o=orderRepo.findByIdForUpdate(id).orElseThrow(this::notFound);if(o.getRiderId()==null)throw forbidden();SokoRider r=riderRepo.findForUpdate(o.getRiderId(),o.getStoreId()).orElseThrow(this::notFound);if(!r.isVerified()||!"ACTIVE".equals(r.getStatus())||!java.util.Objects.equals(r.getUserId(),userDao.getUserId()))throw forbidden();if(required!=null)requireState(o,required);return o;}
    private SokoStore requireMerchantOrAssignedRider(SokoOrder o){SokoStore store=storeRepo.findByIdAndActiveTrue(o.getStoreId()).orElseThrow(this::notFound);if(o.getRiderId()!=null){if(riderRepo.findForUpdate(o.getRiderId(),o.getStoreId()).filter(r->r.isVerified()&&"ACTIVE".equals(r.getStatus())&&r.getUserId()!=null&&r.getUserId().equals(userDao.getUserId())).isPresent())return store;throw forbidden();}if(store.getOwnerUserId()==userDao.getUserId())return store;throw forbidden();}
    private void releaseRider(SokoOrder o,boolean completed){if(o.getRiderId()==null)return;riderRepo.findForUpdate(o.getRiderId(),o.getStoreId()).ifPresent(r->{if("BUSY".equals(r.getAvailability()))r.setAvailability(r.isVerified()&&"ACTIVE".equals(r.getStatus())?"AVAILABLE":"OFFLINE");if(completed)r.setCompletedDeliveries(r.getCompletedDeliveries()+1);riderRepo.save(r);});}
    private boolean validProof(String type,byte[] b){if(b==null)return false;return switch(type){case "image/jpeg"->b.length>3&&(b[0]&255)==255&&(b[1]&255)==216;case "image/png"->b.length>8&&(b[0]&255)==137&&b[1]=='P'&&b[2]=='N'&&b[3]=='G';default->false;};}
    private String decryptDeliveryCode(SokoOrder order){if(order.getEncryptedDeliveryCode()!=null)return encryptionService.decrypt(order.getEncryptedDeliveryCode()).decryptedValue();return order.getDeliveryCode();}
    private boolean constantTimeEquals(String expected,String actual){return java.security.MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),actual.getBytes(StandardCharsets.UTF_8));}
    private Pageable bounded(Pageable p){return PageRequest.of(Math.max(0,p.getPageNumber()),Math.min(100,Math.max(1,p.getPageSize())),p.getSort().isSorted()?p.getSort():Sort.by(Sort.Direction.DESC,"createdOn"));}
    private void restoreStock(SokoOrder o){if(o.isStockReleased())return;for(SokoOrderItem i:itemRepo.findAllByOrderIdAndActiveTrueOrderById(o.getId())){SokoProduct p=productRepo.findByIdForUpdate(i.getProductId()).orElse(null);if(p!=null){p.setStockQuantity(p.getStockQuantity()+i.getQuantity());if(OUT_OF_STOCK.equals(p.getStatus()))p.setStatus(PUBLISHED);productRepo.save(p);}if(i.getVariationId()!=null)productVariationRepo.findForUpdate(i.getVariationId(),i.getProductId()).ifPresent(v->{v.setStockQuantity(v.getStockQuantity()+i.getQuantity());productVariationRepo.save(v);});}o.setStockReleased(true);}
    private OrderDetail detail(SokoOrder o){SokoStore s=storeRepo.findById(o.getStoreId()).orElseThrow(this::notFound);return detail(o,s,itemRepo.findAllByOrderIdAndActiveTrueOrderById(o.getId()));}
    private OrderDetail detail(SokoOrder o,SokoStore s,List<SokoOrderItem> items){Long account=o.getPaymentAccountId()!=null?o.getPaymentAccountId():s.getPaymentAccountId();String channel=o.getPaymentChannel()!=null?o.getPaymentChannel():safeChannel(account);return new OrderDetail(o,s.getName(),account,channel,items);}
    private String safeChannel(Long id){if(id==null)return null;try{var account=accountDao.getAccountById(id);return account==null||account.getChannel()==null?null:account.getChannel().name();}catch(PMSCustomException missingAccount){return null;}}
    private Page<OrderDetail> hydrate(Page<SokoOrder> page){
        if(page.isEmpty())return new PageImpl<>(List.of(),page.getPageable(),page.getTotalElements());
        List<Long> orderIds=page.stream().map(SokoOrder::getId).toList();
        Map<Long,List<SokoOrderItem>> itemsByOrder=itemRepo.findAllByOrderIdInAndActiveTrueOrderByOrderIdAscIdAsc(orderIds).stream().collect(Collectors.groupingBy(SokoOrderItem::getOrderId));
        Map<Long,SokoStore> stores=storeRepo.findAllById(page.stream().map(SokoOrder::getStoreId).distinct().toList()).stream().collect(Collectors.toMap(SokoStore::getId,Function.identity()));
        Map<Long,String> channels=new java.util.HashMap<>();page.forEach(o->{SokoStore s=stores.get(o.getStoreId());Long id=o.getPaymentAccountId()!=null?o.getPaymentAccountId():s==null?null:s.getPaymentAccountId();if(id!=null&&!channels.containsKey(id))channels.put(id,safeChannel(id));});
        List<OrderDetail> content=page.stream().map(o->{SokoStore s=stores.get(o.getStoreId());if(s==null)throw notFound();Long account=o.getPaymentAccountId()!=null?o.getPaymentAccountId():s.getPaymentAccountId();return new OrderDetail(o,s.getName(),account,o.getPaymentChannel()!=null?o.getPaymentChannel():channels.get(account),itemsByOrder.getOrDefault(o.getId(),List.of()));}).toList();
        return new PageImpl<>(content,page.getPageable(),page.getTotalElements());
    }
    private SokoStore ownedStore(long id){return storeRepo.findByIdAndOwnerUserIdAndActiveTrue(id,userDao.getUserId()).orElseThrow(this::notFound);}
    private void requireMerchantRole(){if(!userDao.hasRole(PMSRole.SERVICE_PROVIDER)&&!userDao.hasRole(PMSRole.SUPER_ADMIN))throw new PMSCustomException(ResponseCode.INVALID_ROLE);}
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
    private ZonedDateTime now(){return ZonedDateTime.now(ZoneId.of("UTC"));}
    private PMSCustomException notFound(){return new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND);}
    private PMSCustomException invalid(){return new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);}
    private PMSCustomException forbidden(){return new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS);}
    private static Double distanceKm(Double lat1,Double lng1,Double lat2,Double lng2){if(lat1==null||lng1==null||lat2==null||lng2==null)return null;double a=Math.sin(Math.toRadians(lat2-lat1)/2)*Math.sin(Math.toRadians(lat2-lat1)/2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(Math.toRadians(lng2-lng1)/2)*Math.sin(Math.toRadians(lng2-lng1)/2);return Math.round(6371*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a))*10.0)/10.0;}
    private record GeoBounds(Double minLat,Double maxLat,Double minLng,Double maxLng){static GeoBounds of(Double lat,Double lng,Double requested){if(lat==null||lng==null)return new GeoBounds(null,null,null,null);double radius=Math.max(1,Math.min(requested==null?25:requested,100));double latDelta=radius/111.0;double lngDelta=radius/(111.0*Math.max(.2,Math.cos(Math.toRadians(lat))));return new GeoBounds(lat-latDelta,lat+latDelta,lng-lngDelta,lng+lngDelta);}}
}
