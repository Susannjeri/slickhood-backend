package org.pms.silverocean.service.property.listing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.database.pms.PropertyListingInquiryRepo;
import org.pms.silverocean.database.pms.PropertyListingRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.PropertyListing;
import org.pms.silverocean.database.pms.entities.PropertyListingInquiry;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.helpdesk.HelpDeskRateLimiter;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.pms.silverocean.service.property.listing.PropertyListingModels.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyListingService {
    private final PropertyListingRepo listings;
    private final PropertyListingInquiryRepo inquiries;
    private final UnitRepo units;
    private final UserDao users;
    private final GarageService garage;
    private final HelpDeskRateLimiter rateLimiter;
    private final NotificationService notifications;

    @Value("${property-listings.expiry-days:90}") private int expiryDays;
    @Value("${property-listings.inquiries-per-minute:5}") private int inquiryLimit;
    @Value("${property-listings.public-api-prefix:/public/property-listings}") private String publicApiPrefix;

    @Transactional
    public Publication publish(long unitId, PublishRequest request) {
        long userId = requireUser();
        Unit unit = units.findAdvertisableByUser(unitId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit not found"));
        PropertyListing listing = listings.findByUnitId(unitId).orElseGet(PropertyListing::new);
        if (!request.published()) return unpublish(unit, listing);
        validateForPublication(unit);
        ZonedDateTime now = now();
        if (listing.getId() == null) {
            listing.setUnitId(unitId);
            listing.setPublicSlug(slug(unit));
            listing.setCreatedBy(userId);
            listing.setActive(true);
        }
        listing.setListingType(listingType(unit.getProperty()));
        listing.setHeadline(StringUtils.defaultIfBlank(StringUtils.trim(request.headline()), defaultHeadline(unit)));
        listing.setDescription(StringUtils.defaultIfBlank(StringUtils.trim(request.description()), defaultDescription(unit)));
        listing.setImageManifest(buildImageManifest(unit));
        listing.setPublisherUserId(userId);
        listing.setStatus("PUBLISHED");
        listing.setPublishedAt(listing.getPublishedAt() == null ? now : listing.getPublishedAt());
        listing.setExpiresAt(now.plusDays(Math.max(1, Math.min(expiryDays, 365))));
        listing.setUnpublishedAt(null);
        listing.setSuspendedAt(null);
        listing.setSuspendedBy(null);
        listing.setSuspensionReason(null);
        unit.setAdvertise(true);
        units.save(unit);
        listings.save(listing);
        return publication(listing);
    }

    @Transactional
    public Publication toggleLegacy(long unitId) {
        long userId = requireUser();
        Unit unit = units.findAdvertisableByUser(unitId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit not found"));
        return publish(unitId, new PublishRequest(!unit.isAdvertise(), null, null));
    }

    private Publication unpublish(Unit unit, PropertyListing listing) {
        unit.setAdvertise(false);
        units.save(unit);
        if (listing.getId() == null) return new Publication(null, "PAUSED", null, null);
        listing.setStatus("PAUSED");
        listing.setUnpublishedAt(now());
        listings.save(listing);
        return publication(listing);
    }

    @Transactional(readOnly = true)
    public ListingPage search(String rawType, String location, String unitType, Double minPrice,
                              Double maxPrice, int page, int size) {
        String type = normalizeType(rawType);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 24));
        if (minPrice != null && minPrice < 0 || maxPrice != null && maxPrice < 0 ||
                minPrice != null && maxPrice != null && minPrice > maxPrice) badRequest("Invalid price range");
        Page<PropertyListing> result = listings.searchPublic(type, cleanFilter(location), cleanFilter(unitType),
                minPrice, maxPrice, now(), PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "publishedAt")));
        return new ListingPage(result.stream().map(this::card).toList(), result.getNumber(), result.getSize(),
                result.getTotalPages(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ListingDetail detail(String slug) {
        PropertyListing listing = publicListing(slug);
        Unit unit = listing.getUnit();
        Property property = unit.getProperty();
        List<String> images = imageKeys(listing).stream()
                .map(key -> publicApiPrefix + "/" + listing.getPublicSlug() + "/images/" + key.index()).toList();
        return new ListingDetail(listing.getPublicSlug(), listing.getListingType(), listing.getHeadline(),
                listing.getDescription(), property.getName(), property.getType(), readable(unit.getUnitType()),
                unit.getSize(), unit.getPrice(), unit.getCurrency(), approximate(property.getAddress()),
                amenities(unit), images, listing.getPublishedAt(), listing.getExpiresAt(), true);
    }

    @Transactional(readOnly = true)
    public GarageService.StoredObject image(String slug, int index) {
        if (index < 0 || index > 20) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        PropertyListing listing = publicListing(slug);
        List<ImageKey> keys = imageKeys(listing);
        if (index >= keys.size()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        GarageService.StoredObject object = garage.download(keys.get(index).path());
        String contentType = StringUtils.defaultString(object.contentType()).toLowerCase(Locale.ROOT);
        if (!List.of("image/jpeg", "image/png", "image/webp").contains(contentType))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        return object;
    }

    @Transactional
    public void inquire(String slug, InquiryRequest request, String clientFingerprint) {
        if (StringUtils.isNotBlank(request.website())) return; // honeypot: acknowledge without persisting spam
        String fingerprint = hash(StringUtils.defaultString(clientFingerprint, "unknown") + ":" + request.email().toLowerCase(Locale.ROOT));
        try { rateLimiter.check("property-inquiry:" + fingerprint, Math.max(1, inquiryLimit)); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please wait before sending another enquiry"); }
        PropertyListing listing = publicListing(slug);
        PropertyListingInquiry inquiry = new PropertyListingInquiry();
        inquiry.setListingId(listing.getId());
        inquiry.setName(request.name().trim());
        inquiry.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        inquiry.setPhone(StringUtils.trimToNull(request.phone()));
        inquiry.setMessage(request.message().trim());
        inquiry.setStatus("NEW");
        inquiry.setFingerprintHash(fingerprint);
        inquiry.setActive(true);
        inquiries.save(inquiry);
        users.findById(listing.getPublisherUserId()).map(u -> u.getEmail()).filter(StringUtils::isNotBlank).ifPresent(email -> {
            String body = "New property enquiry for <strong>" + HtmlUtils.htmlEscape(listing.getHeadline()) +
                    "</strong><br>Name: " + HtmlUtils.htmlEscape(inquiry.getName()) +
                    "<br>Email: " + HtmlUtils.htmlEscape(inquiry.getEmail()) +
                    "<br>Phone: " + HtmlUtils.htmlEscape(StringUtils.defaultString(inquiry.getPhone(), "Not provided")) +
                    "<br>Message: " + HtmlUtils.htmlEscape(inquiry.getMessage());
            try { notifications.queueNotification(new NotificationDTO(body, email, NotificationType.PROPERTY_LISTING_INQUIRY_EMAIL)); }
            catch (RuntimeException ex) { log.warn("Property listing enquiry notification could not be queued for listing {}", listing.getId(), ex); }
        });
    }

    @Transactional(readOnly = true)
    public Page<AdminListing> adminList(int page, int size) {
        return listings.findAllByOrderByCreatedOnDesc(PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 50))))
                .map(this::admin);
    }

    @Transactional(readOnly = true)
    public Page<InquiryView> myInquiries(int page, int size) {
        return inquiries.findAccessible(requireUser(), PageRequest.of(Math.max(0,page), Math.max(1,Math.min(size,50)),
                Sort.by(Sort.Direction.DESC,"createdOn"))).map(this::inquiryView);
    }

    @Transactional
    public InquiryView updateInquiry(long id, InquiryStatusRequest request) {
        PropertyListingInquiry inquiry=inquiries.findAccessibleById(id,requireUser())
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        String status=request.status().trim().toUpperCase(Locale.ROOT);
        if(!List.of("NEW","CONTACTED","CLOSED").contains(status))badRequest("Unsupported enquiry status");
        inquiry.setStatus(status); return inquiryView(inquiries.save(inquiry));
    }

    @Transactional
    public AdminListing moderate(long id, ModerateRequest request) {
        PropertyListing listing = listings.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String action = request.action().trim().toUpperCase(Locale.ROOT);
        if ("SUSPEND".equals(action)) {
            if (StringUtils.isBlank(request.reason())) badRequest("A suspension reason is required");
            listing.setStatus("SUSPENDED"); listing.setSuspendedAt(now()); listing.setSuspendedBy(requireUser());
            listing.setSuspensionReason(request.reason().trim()); listing.getUnit().setAdvertise(false);
        } else if ("REACTIVATE".equals(action)) {
            validateForPublication(listing.getUnit());
            listing.setStatus("PUBLISHED"); listing.setSuspendedAt(null); listing.setSuspendedBy(null);
            listing.setSuspensionReason(null); listing.setExpiresAt(now().plusDays(Math.max(1, Math.min(expiryDays, 365))));
            listing.getUnit().setAdvertise(true);
        } else badRequest("Unsupported moderation action");
        units.save(listing.getUnit());
        return admin(listings.save(listing));
    }

    @Scheduled(cron = "${property-listings.expiry-cron:0 */15 * * * *}")
    @Transactional
    public void expirePublishedListings() {
        var expired = listings.findByStatusAndExpiresAtBeforeOrderByExpiresAtAsc("PUBLISHED", now(), PageRequest.of(0, 100));
        for (PropertyListing listing : expired) {
            listing.setStatus("EXPIRED");
            listing.setUnpublishedAt(now());
            listing.getUnit().setAdvertise(false);
            units.save(listing.getUnit());
        }
        listings.saveAll(expired.getContent());
    }

    private void validateForPublication(Unit unit) {
        Property property = unit.getProperty();
        if (!unit.isActive() || property == null || !property.isActive()) badRequest("Only active properties and units can be published");
        if (unit.isOccupied()) badRequest("An occupied unit cannot be published");
        if (unit.getPrice() <= 0 || StringUtils.isBlank(unit.getCurrency())) badRequest("Add a valid price and currency before publishing");
        if (StringUtils.isBlank(unit.getUnitType())) badRequest("Select a unit type before publishing");
        if (coverPath(unit, property) == null) badRequest("Upload a property or unit cover image before publishing");
    }

    private PropertyListing publicListing(String slug) {
        if (StringUtils.isBlank(slug) || slug.length() > 180) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return listings.findPublicBySlug(slug, now()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    private ListingCard card(PropertyListing l) { Unit u=l.getUnit(); Property p=u.getProperty(); return new ListingCard(l.getPublicSlug(),l.getListingType(),l.getHeadline(),p.getType(),readable(u.getUnitType()),u.getSize(),u.getPrice(),u.getCurrency(),approximate(p.getAddress()),publicApiPrefix+"/"+l.getPublicSlug()+"/images/0",l.getPublishedAt()); }
    private AdminListing admin(PropertyListing l) { return new AdminListing(l.getId(),l.getPublicSlug(),l.getUnitId(),l.getStatus(),l.getListingType(),l.getHeadline(),l.getPublisherUserId(),l.getPublishedAt(),l.getExpiresAt(),l.getSuspensionReason()); }
    private InquiryView inquiryView(PropertyListingInquiry i) { PropertyListing l=i.getListing(); return new InquiryView(i.getId(),l.getId(),l.getPublicSlug(),l.getHeadline(),i.getName(),i.getEmail(),i.getPhone(),i.getMessage(),i.getStatus(),i.getCreatedOn()); }
    private Publication publication(PropertyListing l) { return new Publication(l.getPublicSlug(),l.getStatus(),l.getPublishedAt(),l.getExpiresAt()); }
    private String defaultHeadline(Unit u) { return readable(u.getUnitType())+" at "+u.getProperty().getName()+("SALE".equals(listingType(u.getProperty()))?" for sale":" to rent"); }
    private String defaultDescription(Unit u) { return "Discover this "+readable(u.getUnitType()).toLowerCase(Locale.ROOT)+" in "+approximate(u.getProperty().getAddress())+". Contact the owner or appointed agent through Slickhood to arrange a viewing."; }
    private String listingType(Property p) { return "SALE".equalsIgnoreCase(String.valueOf(p.getManagementMode())) ? "SALE" : "RENT"; }
    private String normalizeType(String type) { if (StringUtils.isBlank(type)) return null; String value=type.trim().toUpperCase(Locale.ROOT); if (!List.of("RENT","SALE").contains(value)) badRequest("Listing type must be RENT or SALE"); return value; }
    private String cleanFilter(String value) { value=StringUtils.trimToNull(value); if(value!=null&&value.length()>80) badRequest("Filter is too long"); return value; }
    private String slug(Unit u) { String base=(u.getProperty().getName()+"-"+u.getUnitType()).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("(^-|-$)",""); if(base.length()>130)base=base.substring(0,130); return base+"-"+UUID.randomUUID().toString().substring(0,8); }
    private String readable(String value) {
        String[] words = StringUtils.defaultString(value, "Property").replace('_', ' ').toLowerCase(Locale.ROOT).split("\\s+");
        return Arrays.stream(words).filter(StringUtils::isNotBlank)
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1)).reduce((a, b) -> a + " " + b).orElse("Property");
    }
    private String approximate(String address) { String clean=StringUtils.defaultIfBlank(StringUtils.trim(address),"Location available on request"); String[] parts=clean.split(","); return String.join(", ", Arrays.stream(parts).map(String::trim).filter(StringUtils::isNotBlank).limit(2).toList()); }
    private List<String> amenities(Unit u) { if(StringUtils.isBlank(u.getUtilities())) return List.of(); return Arrays.stream(u.getUtilities().split(",")).map(String::trim).filter(StringUtils::isNotBlank).limit(12).toList(); }
    private String coverPath(Unit u, Property p) { if(StringUtils.isNotBlank(u.getImagePath())&&StringUtils.isNotBlank(u.getThumbnail()))return join(u.getImagePath(),u.getThumbnail()); if(StringUtils.isNotBlank(p.getImagePath())&&StringUtils.isNotBlank(p.getThumbnail()))return join(p.getImagePath(),p.getThumbnail()); return null; }
    private String buildImageManifest(Unit unit) {
        java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>();
        String cover = coverPath(unit, unit.getProperty());
        if (cover != null) paths.add(cover);
        if (StringUtils.isNotBlank(unit.getImagePath())) {
            try {
                garage.listFiles(join(unit.getImagePath(), "sliderImages")).stream()
                        .filter(this::supportedImagePath).sorted().limit(19).forEach(paths::add);
            } catch (RuntimeException exception) {
                log.warn("Slider images could not be indexed while publishing unit {}", unit.getId());
            }
        }
        return String.join("\n", paths);
    }
    private List<ImageKey> imageKeys(PropertyListing listing) {
        List<String> paths = StringUtils.isBlank(listing.getImageManifest())
                ? java.util.stream.Stream.of(coverPath(listing.getUnit(), listing.getUnit().getProperty())).filter(java.util.Objects::nonNull).toList()
                : Arrays.stream(listing.getImageManifest().split("\\n")).filter(StringUtils::isNotBlank).limit(20).toList();
        return java.util.stream.IntStream.range(0, paths.size()).mapToObj(i -> new ImageKey(i, paths.get(i))).toList();
    }
    private boolean supportedImagePath(String path) { String lower=StringUtils.defaultString(path).toLowerCase(Locale.ROOT); return lower.endsWith(".jpg")||lower.endsWith(".jpeg")||lower.endsWith(".png")||lower.endsWith(".webp"); }
    private String join(String a,String b){return StringUtils.removeEnd(a,"/")+"/"+StringUtils.removeStart(b,"/");}
    private long requireUser(){Long id=users.getUserId();if(id==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return id;}
    private ZonedDateTime now(){return ZonedDateTime.now(ZoneOffset.UTC);}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private void badRequest(String message){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private record ImageKey(int index,String path) { }
}
