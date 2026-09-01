package org.pms.silverocean.service.soko;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.SokoOrderItemRepo;
import org.pms.silverocean.database.pms.SokoOrderRepo;
import org.pms.silverocean.database.pms.SokoProductRepo;
import org.pms.silverocean.database.pms.SokoProductImageRepo;
import org.pms.silverocean.database.pms.SokoRiderRepo;
import org.pms.silverocean.database.pms.SokoStoreRepo;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.database.pms.entities.SokoOrder;
import org.pms.silverocean.database.pms.entities.SokoOrderItem;
import org.pms.silverocean.database.pms.entities.SokoProduct;
import org.pms.silverocean.database.pms.entities.SokoProductImage;
import org.pms.silverocean.database.pms.entities.SokoRider;
import org.pms.silverocean.database.pms.entities.SokoStore;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.security.EncryptionService;
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

@Service
@RequiredArgsConstructor
public class SokoService {
    private static final String DRAFT="DRAFT", PUBLISHED="PUBLISHED", OUT_OF_STOCK="OUT_OF_STOCK";
    private static final SecureRandom SECURE_RANDOM=new SecureRandom();
    private final SokoStoreRepo storeRepo;
    private final SokoProductRepo productRepo;
    private final SokoProductImageRepo productImageRepo;
    private final SokoOrderRepo orderRepo;
    private final SokoOrderItemRepo itemRepo;
    private final SokoRiderRepo riderRepo;
    private final InvoiceDao invoiceDao;
    private final AccountDao accountDao;
    private final UserDao userDao;
    private final VisitorService visitorService;
    private final GarageService garageService;
    private final EncryptionService encryptionService;
    @Value("${soko.stock-reservation-minutes:20}") private long reservationMinutes;

    public Page<CatalogProduct> catalog(Pageable pageable, Long storeId, String category, String query, Double latitude, Double longitude, Double radiusKm) {
        String cleanCategory=StringUtils.trimToNull(category), cleanQuery=StringUtils.trimToNull(query);
        GeoBounds bounds=GeoBounds.of(latitude,longitude,radiusKm);
        Page<SokoProduct> page=productRepo.searchCatalog(bounded(pageable),storeId,cleanCategory,cleanQuery,latitude,longitude,bounds.minLat(),bounds.maxLat(),bounds.minLng(),bounds.maxLng());
        List<Long> productIds=page.stream().map(SokoProduct::getId).toList();
        Map<Long,List<String>> images=imageUrlsByProduct(productIds);
        Map<Long,SokoStore> stores=storeRepo.findAllById(page.stream().map(SokoProduct::getStoreId).distinct().toList())
                .stream().collect(Collectors.toMap(SokoStore::getId, Function.identity()));
        return page.map(p->{
            SokoStore s=stores.get(p.getStoreId());
            return new CatalogProduct(p,s==null?"Soko merchant":s.getName(),s!=null&&s.isDeliveryEnabled(),s!=null&&s.isPickupEnabled(),s==null?null:distanceKm(latitude,longitude,s.getLatitude(),s.getLongitude()),images.getOrDefault(p.getId(),legacyImage(p)));
        });
    }

    public StoreDetail storeDetail(long id) {
        SokoStore s=storeRepo.findByIdAndActiveTrue(id).filter(x->PUBLISHED.equals(x.getStatus())).orElseThrow(this::notFound);
        List<SokoProduct> products=productRepo.findAllByStoreIdAndActiveTrueOrderByName(id).stream().filter(p->PUBLISHED.equals(p.getStatus())&&p.getStockQuantity()>0).toList();
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
        SokoStore s=ownedStore(id); applyStore(s,request); if(PUBLISHED.equals(s.getStatus()))validatePublishable(s); return storeRepo.save(s);
    }

    @Transactional
    public SokoStore publishStore(long id) {
        SokoStore s=ownedStore(id); validatePublishable(s); s.setStatus(PUBLISHED); return storeRepo.save(s);
    }

    public List<SokoStore> myStores(){return storeRepo.findAllByOwnerUserIdAndActiveTrueOrderByName(userDao.getUserId());}

    @Transactional
    public SokoProduct createProduct(SokoRequests.ProductUpsert request){
        SokoStore store=ownedStore(request.storeId()); SokoProduct p=new SokoProduct(); p.setStoreId(store.getId()); p.setCreatedBy(userDao.getUserId()); p.setActive(true); p.setStatus(DRAFT); applyProduct(p,request,store); return productRepo.save(p);
    }

    @Transactional
    public SokoProduct updateProduct(long id,SokoRequests.ProductUpsert request){
        SokoProduct p=productRepo.findById(id).orElseThrow(this::notFound); SokoStore store=ownedStore(p.getStoreId()); if(store.getId()!=request.storeId())throw invalid(); applyProduct(p,request,store); return productRepo.save(p);
    }

    @Transactional
    public SokoProduct publishProduct(long id){
        SokoProduct p=productRepo.findById(id).orElseThrow(this::notFound); ownedStore(p.getStoreId());
        if(StringUtils.isBlank(p.getImageUrl())&&productImageRepo.findAllByProductIdAndActiveTrueOrderByDisplayOrderAsc(id).isEmpty())throw new PMSCustomException(ResponseCode.INVALID_IMAGE);
        p.setStatus(p.getStockQuantity()>0?PUBLISHED:OUT_OF_STOCK); return productRepo.save(p);
    }

    public List<SokoProduct> myProducts(long storeId){ownedStore(storeId);return productRepo.findAllByStoreIdAndActiveTrueOrderByName(storeId);}

    @Transactional
    public SokoModels.ProductImages replaceProductImages(long productId, List<MultipartFile> images) throws IOException {
        SokoProduct product=productRepo.findById(productId).orElseThrow(this::notFound);
        ownedStore(product.getStoreId());
        if(images==null||images.isEmpty()||images.size()>5)throw new PMSCustomException(ResponseCode.INVALID_IMAGE);
        List<PendingImage> pending=new ArrayList<>(images.size());
        long total=0;
        for(MultipartFile image:images){
            byte[] bytes=validatedImageBytes(image);
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
        SokoProduct product=productRepo.findById(productId).orElseThrow(this::notFound);
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
        SokoRider rider=new SokoRider();rider.setStoreId(request.storeId());rider.setCreatedBy(userDao.getUserId());rider.setActive(true);rider.setStatus("ACTIVE");rider.setAvailability("AVAILABLE");rider.setCompletedDeliveries(0);rider.setVerified(false);applyRider(rider,request);return riderRepo.save(rider);
    }

    @Transactional
    public SokoRider updateRider(long id,SokoRequests.RiderUpsert request){
        ownedStore(request.storeId());SokoRider rider=riderRepo.findByIdAndStoreIdAndActiveTrue(id,request.storeId()).orElseThrow(this::notFound);
        String phone=request.phoneNumber().trim();if(!phone.equals(rider.getPhoneNumber())&&riderRepo.existsByStoreIdAndPhoneNumberAndActiveTrue(request.storeId(),phone))throw invalid();
        applyRider(rider,request);return riderRepo.save(rider);
    }

    public List<SokoRider> myRiders(long storeId){ownedStore(storeId);return riderRepo.findAllByStoreIdAndActiveTrueOrderByDisplayName(storeId);}

    @Transactional
    public SokoRider setRiderAvailability(long id,String requested){
        SokoRider rider=riderRepo.findById(id).filter(SokoRider::isActive).orElseThrow(this::notFound);ownedStore(rider.getStoreId());String availability=requested.toUpperCase(Locale.ROOT);
        if(!List.of("AVAILABLE","OFFLINE").contains(availability)||"BUSY".equals(rider.getAvailability()))throw invalid();rider.setAvailability(availability);return riderRepo.save(rider);
    }

    @Transactional
    public void removeRider(long id){SokoRider rider=riderRepo.findById(id).filter(SokoRider::isActive).orElseThrow(this::notFound);ownedStore(rider.getStoreId());if("BUSY".equals(rider.getAvailability()))throw invalid();rider.setActive(false);rider.setStatus("INACTIVE");rider.setAvailability("OFFLINE");riderRepo.save(rider);}

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
        if(request.items().stream().map(SokoRequests.CheckoutItem::productId).distinct().count()!=request.items().size())throw invalid();
        List<SokoProduct> products=new ArrayList<>(); BigDecimal subtotal=BigDecimal.ZERO;
        for(SokoRequests.CheckoutItem line:request.items()){
            SokoProduct p=productRepo.findByIdForUpdate(line.productId()).filter(x->x.getStoreId()==store.getId()&&PUBLISHED.equals(x.getStatus())).orElseThrow(this::notFound);
            if(p.getStockQuantity()<line.quantity())throw new PMSCustomException(ResponseCode.INVALID_AMOUNT,"Insufficient stock for "+p.getName());
            p.setStockQuantity(p.getStockQuantity()-line.quantity()); if(p.getStockQuantity()==0)p.setStatus(OUT_OF_STOCK); productRepo.save(p); products.add(p);
            subtotal=subtotal.add(p.getPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        BigDecimal fee="DELIVERY".equalsIgnoreCase(request.deliveryMethod())?zero(store.getDeliveryFee()):BigDecimal.ZERO;
        SokoOrder o=new SokoOrder(); o.setOrderNumber("SOKO-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT)); o.setStoreId(store.getId()); o.setCustomerUserId(customerUserId); o.setCreatedBy(customerUserId); o.setCheckoutIdempotencyKey(cleanKey); o.setActive(true); o.setStatus("PENDING_PAYMENT"); o.setPaymentStatus("UNPAID");o.setRefundStatus("NOT_REQUIRED");o.setSettlementStatus("PENDING");o.setReservationExpiresAt(now().plusMinutes(reservationMinutes)); o.setDeliveryMethod(request.deliveryMethod().toUpperCase(Locale.ROOT)); o.setDeliveryAddress(StringUtils.trimToNull(request.deliveryAddress())); o.setCustomerPhone(request.customerPhone()); o.setNotes(StringUtils.trimToNull(request.notes())); o.setDestinationUnitId(request.destinationUnitId()); o.setSubtotal(subtotal); o.setDeliveryFee(fee); o.setTotal(subtotal.add(fee)); o.setCurrency(store.getCurrency()); o.setPlacedAt(now()); orderRepo.save(o);
        List<SokoOrderItem> items=new ArrayList<>();
        for(int i=0;i<products.size();i++){SokoProduct p=products.get(i);int qty=request.items().get(i).quantity();SokoOrderItem it=new SokoOrderItem();it.setOrderId(o.getId());it.setProductId(p.getId());it.setProductName(p.getName());it.setUnit(p.getUnit());it.setUnitPrice(p.getPrice());it.setQuantity(qty);it.setLineTotal(p.getPrice().multiply(BigDecimal.valueOf(qty)));it.setCreatedBy(userDao.getUserId());it.setActive(true);items.add(itemRepo.save(it));}
        PMSInvoice invoice=createInvoice(o,store,items);o.setInvoiceRef(invoice.getRef());orderRepo.save(o);return detail(o,store,items);
    }

    public Page<OrderDetail> myOrders(Pageable pageable){return hydrate(orderRepo.findAllByCustomerUserIdAndActiveTrue(userDao.getUserId(),bounded(pageable)));}
    public Page<OrderDetail> merchantOrders(Pageable pageable){List<Long> ids=myStores().stream().map(SokoStore::getId).toList();if(ids.isEmpty())return Page.empty(bounded(pageable));return hydrate(orderRepo.findAllByStoreIdInAndActiveTrue(ids,bounded(pageable)));}

    public String deliveryCode(long orderId){SokoOrder o=orderRepo.findById(orderId).orElseThrow(this::notFound);if(o.getCustomerUserId()!=userDao.getUserId())throw forbidden();String code=decryptDeliveryCode(o);if(!"DELIVERY".equals(o.getDeliveryMethod())||StringUtils.isBlank(code)||!List.of("DISPATCHED","COMPLETED").contains(o.getStatus()))throw invalid();return code;}

    @Transactional public OrderDetail uploadDeliveryProof(long orderId,MultipartFile proof)throws IOException{SokoOrder o=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);ownedStore(o.getStoreId());if(!"DISPATCHED".equals(o.getStatus())||StringUtils.isNotBlank(o.getDeliveryProofReference())||proof==null||proof.isEmpty()||proof.getSize()>5L*1024*1024)throw invalid();String type=StringUtils.defaultString(proof.getContentType()).toLowerCase(Locale.ROOT);byte[] bytes=proof.getBytes();if(!validProof(type,bytes))throw new PMSCustomException(ResponseCode.INVALID_IMAGE);String extension="image/png".equals(type)?"png":"jpg";String ref="soko/delivery-proof/"+o.getId()+"/"+UUID.randomUUID()+"."+extension;garageService.uploadBytes(ref,bytes,type);o.setDeliveryProofReference(ref);o.setDeliveryProofContentType(type);o.setDeliveryProofSize((long)bytes.length);o.setDeliveryProofAt(now());orderRepo.save(o);return detail(o);}
    public String deliveryProof(long orderId){SokoOrder o=orderRepo.findById(orderId).filter(x->x.isActive()).orElseThrow(this::notFound);SokoStore store=storeRepo.findByIdAndActiveTrue(o.getStoreId()).orElseThrow(this::notFound);if(o.getCustomerUserId()!=userDao.getUserId()&&store.getOwnerUserId()!=userDao.getUserId())throw forbidden();if(StringUtils.isBlank(o.getDeliveryProofReference()))throw notFound();return garageService.getPresignedUrlForStoredObject(o.getDeliveryProofReference());}

    @Transactional(noRollbackFor=PMSCustomException.class)
    public OrderDetail confirmDelivery(long orderId,SokoRequests.DeliveryConfirmation request){SokoOrder o=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);SokoStore store=ownedStore(o.getStoreId());String code=decryptDeliveryCode(o);if(!"DELIVERY".equals(o.getDeliveryMethod())||!"DISPATCHED".equals(o.getStatus())||o.isDeliveryCodeVerified()||StringUtils.isBlank(code)||StringUtils.isBlank(o.getDeliveryProofReference()))throw invalid();if(o.getDeliveryCodeAttempts()>=5)throw forbidden();o.setDeliveryCodeAttempts(o.getDeliveryCodeAttempts()+1);if(!constantTimeEquals(code,request.code())){orderRepo.save(o);throw invalid();}o.setDeliveryCodeVerified(true);o.setDeliveryCode(null);o.setEncryptedDeliveryCode(null);o.setDeliveryRecipientName(StringUtils.left(StringUtils.trimToNull(request.recipientName()),160));o.setDeliveryProofAt(now());o.setStatus("COMPLETED");o.setCompletedAt(now());releaseRider(o,true);orderRepo.save(o);return detail(o,store,itemRepo.findAllByOrderIdAndActiveTrueOrderById(o.getId()));}

    @Transactional
    public OrderDetail transition(long orderId,String requested,SokoRequests.Dispatch dispatch){
        SokoOrder o=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);SokoStore store=storeRepo.findByIdAndActiveTrue(o.getStoreId()).orElseThrow(this::notFound);String next=requested.toUpperCase(Locale.ROOT);
        boolean customer=o.getCustomerUserId()==userDao.getUserId(),merchant=store.getOwnerUserId()==userDao.getUserId();
        if("CANCELLED".equals(next))throw invalid();
        else {if(!merchant)throw forbidden(); switch(next){case "CONFIRMED"->{requireState(o,"PAID");o.setConfirmedAt(now());}case "PACKED"->requireState(o,"CONFIRMED");case "DISPATCHED"->{requireState(o,"PACKED");if("DELIVERY".equals(o.getDeliveryMethod()))assignAndRegisterDelivery(o,dispatch);o.setDispatchedAt(now());}case "COMPLETED"->{requireState(o,"READY_FOR_PICKUP");o.setCompletedAt(now());}case "READY_FOR_PICKUP"->requireState(o,"PACKED");default->throw invalid();}o.setStatus(next);}
        orderRepo.save(o);return detail(o);
    }

    @Transactional
    public void completePaidInvoice(String invoiceRef,String providerReference){orderRepo.findByInvoiceRefAndActiveTrue(invoiceRef).ifPresent(o->{if("UNPAID".equals(o.getPaymentStatus())){o.setPaymentStatus("PAID");if("EXPIRED".equals(o.getStatus())||"CANCELLED".equals(o.getStatus()))o.setRefundStatus("REQUESTED");else o.setStatus("PAID");orderRepo.save(o);}});}

    @Transactional public OrderDetail cancel(long orderId,SokoRequests.Cancellation request){SokoOrder o=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);SokoStore s=storeRepo.findByIdAndActiveTrue(o.getStoreId()).orElseThrow(this::notFound);boolean allowed=o.getCustomerUserId()==userDao.getUserId()||s.getOwnerUserId()==userDao.getUserId();if(!allowed)throw forbidden();if(!List.of("PENDING_PAYMENT","PAID","CONFIRMED").contains(o.getStatus()))throw invalid();restoreStock(o);releaseRider(o,false);o.setCancellationReason(request.reason().trim());o.setCancelledAt(now());o.setStatus("CANCELLED");if("PAID".equals(o.getPaymentStatus()))o.setRefundStatus("REQUESTED");orderRepo.save(o);return detail(o);}
    @Transactional public OrderDetail finance(long orderId,SokoRequests.FinanceUpdate r){if(!userDao.hasRole(PMSRole.FINANCE)&&!userDao.hasRole(PMSRole.SUPER_ADMIN))throw forbidden();SokoOrder o=orderRepo.findByIdForUpdate(orderId).orElseThrow(this::notFound);if(r.status()==SokoRequests.FinanceStatus.CONFIRMED&&StringUtils.isBlank(r.providerReference()))throw invalid();if(r.type()==SokoRequests.FinanceType.REFUND){if(!"PAID".equals(o.getPaymentStatus())||r.amount().compareTo(o.getTotal())>0)throw invalid();o.setRefundStatus(r.status().name());o.setRefundedAmount(r.amount());o.setRefundReference(StringUtils.left(StringUtils.trimToNull(r.providerReference()),120));}else{BigDecimal refundable=o.getRefundedAmount()==null?BigDecimal.ZERO:o.getRefundedAmount();if(!"COMPLETED".equals(o.getStatus())||r.amount().compareTo(o.getTotal().subtract(refundable))>0)throw invalid();o.setSettlementStatus(r.status().name());o.setSettledAmount(r.amount());o.setSettlementReference(StringUtils.left(StringUtils.trimToNull(r.providerReference()),120));}orderRepo.save(o);return detail(o);}
    @Scheduled(fixedDelayString="${soko.reservation-expiry-scan-ms:300000}") @Transactional public void expireReservations(){for(SokoOrder o:orderRepo.findExpiredReservations(now(),PageRequest.of(0,100,Sort.by("reservationExpiresAt")))){restoreStock(o);o.setStatus("EXPIRED");o.setCancelledAt(now());o.setCancellationReason("Payment reservation expired");orderRepo.save(o);}}

    private void applyStore(SokoStore s,SokoRequests.StoreUpsert r){s.setName(r.name().trim());s.setDescription(StringUtils.trimToNull(r.description()));s.setPhoneNumber(StringUtils.trimToNull(r.phoneNumber()));s.setAddress(StringUtils.trimToNull(r.address()));s.setLatitude(r.latitude());s.setLongitude(r.longitude());s.setServiceRadiusKm(r.serviceRadiusKm()==null?BigDecimal.valueOf(25):r.serviceRadiusKm());s.setPickupEnabled(r.pickupEnabled());s.setDeliveryEnabled(r.deliveryEnabled());s.setDeliveryFee(zero(r.deliveryFee()));s.setCurrency(r.currency().trim().toUpperCase(Locale.ROOT));s.setPaymentAccountId(r.paymentAccountId());if(!s.isPickupEnabled()&&!s.isDeliveryEnabled())throw invalid();if((s.getLatitude()==null)!=(s.getLongitude()==null))throw invalid();}
    private void applyProduct(SokoProduct p,SokoRequests.ProductUpsert r,SokoStore store){p.setName(r.name().trim());p.setDescription(StringUtils.trimToNull(r.description()));p.setCategory(r.category().trim());p.setUnit(r.unit().trim());p.setPrice(r.price());p.setCurrency(store.getCurrency());p.setStockQuantity(r.stockQuantity());p.setImageUrl(safeLegacyImageUrl(r.imageUrl()));if(OUT_OF_STOCK.equals(p.getStatus())&&p.getStockQuantity()>0)p.setStatus(DRAFT);}
    private Map<Long,List<String>> imageUrlsByProduct(List<Long> ids){if(ids.isEmpty())return Map.of();return productImageRepo.findAllByProductIdInAndActiveTrueOrderByProductIdAscDisplayOrderAsc(ids).stream().collect(Collectors.groupingBy(SokoProductImage::getProductId,Collectors.mapping(i->garageService.getPresignedUrlForStoredObject(i.getFileRef()),Collectors.toList())));}
    private List<String> legacyImage(SokoProduct product){return StringUtils.isBlank(product.getImageUrl())?List.of():List.of(product.getImageUrl());}
    private String safeLegacyImageUrl(String value){String clean=StringUtils.trimToNull(value);if(clean==null)return null;try{URI uri=URI.create(clean);if(!"https".equalsIgnoreCase(uri.getScheme())||StringUtils.isBlank(uri.getHost()))throw invalid();return clean;}catch(IllegalArgumentException ex){throw invalid();}}
    private byte[] validatedImageBytes(MultipartFile image)throws IOException{if(image==null||image.isEmpty())throw new PMSCustomException(ResponseCode.INVALID_IMAGE);if(image.getSize()>8L*1024*1024)throw new PMSCustomException(ResponseCode.MAX_UPLOAD_SIZE_EXCEEDED);String type=StringUtils.defaultString(image.getContentType()).toLowerCase(Locale.ROOT);if(!Set.of("image/jpeg","image/png","image/webp").contains(type))throw new PMSCustomException(ResponseCode.INVALID_IMAGE);byte[] b=image.getBytes();boolean valid=switch(type){case "image/jpeg"->b.length>3&&(b[0]&255)==0xff&&(b[1]&255)==0xd8;case "image/png"->b.length>8&&(b[0]&255)==0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G';case "image/webp"->b.length>12&&b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'&&b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P';default->false;};if(!valid)throw new PMSCustomException(ResponseCode.INVALID_IMAGE);return b;}
    private String imageExtension(String type){return switch(type){case "image/png"->"png";case "image/webp"->"webp";default->"jpg";};}
    private record PendingImage(byte[] bytes,String contentType,String extension){}
    private void applyRider(SokoRider rider,SokoRequests.RiderUpsert r){String type=r.riderType().trim().toUpperCase(Locale.ROOT);if(!List.of("INDIVIDUAL","DELIVERY_COMPANY").contains(type))throw invalid();rider.setRiderType(type);rider.setDisplayName(r.displayName().trim());rider.setPhoneNumber(r.phoneNumber().trim());rider.setEmail(StringUtils.trimToNull(r.email()));rider.setVehicleType(StringUtils.trimToNull(r.vehicleType()));rider.setVehiclePlate(StringUtils.trimToNull(r.vehiclePlate()));rider.setNotes(StringUtils.trimToNull(r.notes()));}
    private void validatePublishable(SokoStore s){if(s.getPaymentAccountId()==null)throw new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND);PaymentAccount a=accountDao.getAccountByIdAndCreatedBy(s.getPaymentAccountId(),s.getOwnerUserId());if(!a.isVerified()||!a.isActive()||a.getCategory()==AccountCategory.SLICKHOOD)throw new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND);}
    private void validateDelivery(SokoStore s,SokoRequests.Checkout r){if("DELIVERY".equalsIgnoreCase(r.deliveryMethod())){if(!s.isDeliveryEnabled()||StringUtils.isBlank(r.deliveryAddress()))throw invalid();}else if("PICKUP".equalsIgnoreCase(r.deliveryMethod())){if(!s.isPickupEnabled())throw invalid();}else throw invalid();}
    private PMSInvoice createInvoice(SokoOrder o,SokoStore s,List<SokoOrderItem> items){PMSInvoice inv=new PMSInvoice();inv.setUnitId(o.getDestinationUnitId()==null?0:o.getDestinationUnitId());inv.setPropertyId(0);inv.setDescription(("Soko order "+o.getOrderNumber()).getBytes(StandardCharsets.UTF_8));String html=items.stream().map(i->"<tr><td><span>"+i.getProductName()+" x "+i.getQuantity()+"</span></td><td class='amount-col'>"+i.getLineTotal()+"</td></tr>").collect(Collectors.joining());inv.setHtmlDescription(html.getBytes(StandardCharsets.UTF_8));inv.setAmount(o.getTotal().doubleValue());inv.setPendingAmount(o.getTotal().doubleValue());inv.setCurrency(o.getCurrency());inv.setBilledUserId(o.getCustomerUserId());inv.setPayToUserId(s.getOwnerUserId());inv.setActive(true);inv.setBillingType("SOKO");inv.setCustomerPhoneNumber(o.getCustomerPhone());userDao.findById(o.getCustomerUserId()).ifPresent(u->inv.setCustomerEmail(u.getEmail()));invoiceDao.createInvoice(inv);return inv;}
    private void assignAndRegisterDelivery(SokoOrder o,SokoRequests.Dispatch d){
        if(d==null)throw invalid();
        ZonedDateTime expectedArrival=d.expectedArrivalTime().atZone(ZoneId.of("Africa/Nairobi")).withZoneSameInstant(ZoneId.of("UTC"));
        if(!expectedArrival.isAfter(now()))throw invalid();
        if(d.riderId()!=null){SokoRider rider=riderRepo.findForUpdate(d.riderId(),o.getStoreId()).orElseThrow(this::notFound);if(!"ACTIVE".equals(rider.getStatus())||!"AVAILABLE".equals(rider.getAvailability()))throw invalid();rider.setAvailability("BUSY");riderRepo.save(rider);o.setRiderId(rider.getId());o.setCourierName(rider.getDisplayName());o.setCourierPhone(rider.getPhoneNumber());o.setCourierVehiclePlate(rider.getVehiclePlate());}
        else {if(StringUtils.isBlank(d.courierName())||StringUtils.isBlank(d.courierPhone()))throw invalid();o.setRiderId(null);o.setCourierName(d.courierName().trim());o.setCourierPhone(d.courierPhone().trim());o.setCourierVehiclePlate(StringUtils.trimToNull(d.vehiclePlate()));}
        String deliveryCode=String.format(Locale.ROOT,"%06d",SECURE_RANDOM.nextInt(1_000_000));o.setDeliveryCode(null);o.setEncryptedDeliveryCode(encryptionService.encrypt(deliveryCode));o.setDeliveryCodeAttempts(0);o.setDeliveryCodeVerified(false);o.setExpectedArrivalAt(expectedArrival);
        if(o.getDestinationUnitId()==null)return;var visitor=visitorService.preRegisterDeliveryForHost(o.getCustomerUserId(),new CreateVisitorRequest(o.getCourierName(),o.getCourierVehiclePlate(),d.expectedArrivalTime(),null,false,o.getDestinationUnitId(),o.getCourierPhone(), VisitorCategory.DELIVERY));o.setDeliveryVisitorId(visitor.getId());
    }
    private void releaseRider(SokoOrder o,boolean completed){if(o.getRiderId()==null)return;riderRepo.findForUpdate(o.getRiderId(),o.getStoreId()).ifPresent(r->{if("BUSY".equals(r.getAvailability()))r.setAvailability("AVAILABLE");if(completed)r.setCompletedDeliveries(r.getCompletedDeliveries()+1);riderRepo.save(r);});}
    private boolean validProof(String type,byte[] b){if(b==null)return false;return switch(type){case "image/jpeg"->b.length>3&&(b[0]&255)==255&&(b[1]&255)==216;case "image/png"->b.length>8&&(b[0]&255)==137&&b[1]=='P'&&b[2]=='N'&&b[3]=='G';default->false;};}
    private String decryptDeliveryCode(SokoOrder order){if(order.getEncryptedDeliveryCode()!=null)return encryptionService.decrypt(order.getEncryptedDeliveryCode()).decryptedValue();return order.getDeliveryCode();}
    private boolean constantTimeEquals(String expected,String actual){return java.security.MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),actual.getBytes(StandardCharsets.UTF_8));}
    private Pageable bounded(Pageable p){return PageRequest.of(Math.max(0,p.getPageNumber()),Math.min(100,Math.max(1,p.getPageSize())),p.getSort().isSorted()?p.getSort():Sort.by(Sort.Direction.DESC,"createdOn"));}
    private void restoreStock(SokoOrder o){if(o.isStockReleased())return;for(SokoOrderItem i:itemRepo.findAllByOrderIdAndActiveTrueOrderById(o.getId())){SokoProduct p=productRepo.findByIdForUpdate(i.getProductId()).orElse(null);if(p!=null){p.setStockQuantity(p.getStockQuantity()+i.getQuantity());if(OUT_OF_STOCK.equals(p.getStatus()))p.setStatus(PUBLISHED);productRepo.save(p);}}o.setStockReleased(true);}
    private OrderDetail detail(SokoOrder o){SokoStore s=storeRepo.findById(o.getStoreId()).orElseThrow(this::notFound);return detail(o,s,itemRepo.findAllByOrderIdAndActiveTrueOrderById(o.getId()));}
    private OrderDetail detail(SokoOrder o,SokoStore s,List<SokoOrderItem> items){String channel=s.getPaymentAccountId()==null?null:accountDao.getAccountById(s.getPaymentAccountId()).getChannel().name();return new OrderDetail(o,s.getName(),s.getPaymentAccountId(),channel,items);}
    private Page<OrderDetail> hydrate(Page<SokoOrder> page){
        if(page.isEmpty())return new PageImpl<>(List.of(),page.getPageable(),page.getTotalElements());
        List<Long> orderIds=page.stream().map(SokoOrder::getId).toList();
        Map<Long,List<SokoOrderItem>> itemsByOrder=itemRepo.findAllByOrderIdInAndActiveTrueOrderByOrderIdAscIdAsc(orderIds).stream().collect(Collectors.groupingBy(SokoOrderItem::getOrderId));
        Map<Long,SokoStore> stores=storeRepo.findAllById(page.stream().map(SokoOrder::getStoreId).distinct().toList()).stream().collect(Collectors.toMap(SokoStore::getId,Function.identity()));
        Map<Long,String> channels=stores.values().stream().filter(s->s.getPaymentAccountId()!=null).collect(Collectors.toMap(SokoStore::getId,s->accountDao.getAccountById(s.getPaymentAccountId()).getChannel().name()));
        List<OrderDetail> content=page.stream().map(o->{SokoStore s=stores.get(o.getStoreId());if(s==null)throw notFound();return new OrderDetail(o,s.getName(),s.getPaymentAccountId(),channels.get(s.getId()),itemsByOrder.getOrDefault(o.getId(),List.of()));}).toList();
        return new PageImpl<>(content,page.getPageable(),page.getTotalElements());
    }
    private SokoStore ownedStore(long id){return storeRepo.findByIdAndOwnerUserIdAndActiveTrue(id,userDao.getUserId()).orElseThrow(this::notFound);}
    private void requireMerchantRole(){if(!userDao.hasRole(PMSRole.SERVICE_PROVIDER)&&!userDao.hasRole(PMSRole.SUPER_ADMIN))throw new PMSCustomException(ResponseCode.INVALID_ROLE);}
    private void requireState(SokoOrder o,String state){if(!state.equals(o.getStatus()))throw invalid();}
    private BigDecimal zero(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
    private ZonedDateTime now(){return ZonedDateTime.now(ZoneId.of("UTC"));}
    private PMSCustomException notFound(){return new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND);}
    private PMSCustomException invalid(){return new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);}
    private PMSCustomException forbidden(){return new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS);}
    private static Double distanceKm(Double lat1,Double lng1,Double lat2,Double lng2){if(lat1==null||lng1==null||lat2==null||lng2==null)return null;double a=Math.sin(Math.toRadians(lat2-lat1)/2)*Math.sin(Math.toRadians(lat2-lat1)/2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(Math.toRadians(lng2-lng1)/2)*Math.sin(Math.toRadians(lng2-lng1)/2);return Math.round(6371*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a))*10.0)/10.0;}
    private record GeoBounds(Double minLat,Double maxLat,Double minLng,Double maxLng){static GeoBounds of(Double lat,Double lng,Double requested){if(lat==null||lng==null)return new GeoBounds(null,null,null,null);double radius=Math.max(1,Math.min(requested==null?25:requested,100));double latDelta=radius/111.0;double lngDelta=radius/(111.0*Math.max(.2,Math.cos(Math.toRadians(lat))));return new GeoBounds(lat-latDelta,lat+latDelta,lng-lngDelta,lng+lngDelta);}}
}
