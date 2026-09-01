package org.pms.silverocean.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.property.listing.PropertyListingModels.*;
import org.pms.silverocean.service.property.listing.PropertyListingService;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class PropertyListingController {
    private final PropertyListingService service;

    @GetMapping("/public/property-listings")
    public ResponseEntity<ListingPage> search(@RequestParam(required = false) String type,
                              @RequestParam(required = false) String location,
                              @RequestParam(required = false) String unitType,
                              @RequestParam(required = false) Double minPrice,
                              @RequestParam(required = false) Double maxPrice,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "12") int size) {
        return publicJson(service.search(type, location, unitType, minPrice, maxPrice, page, size));
    }

    @GetMapping("/public/property-listings/{slug}")
    public ResponseEntity<ListingDetail> detail(@PathVariable String slug) { return publicJson(service.detail(slug)); }

    @GetMapping("/public/property-listings/filters")
    public ResponseEntity<ListingFilters> filters(@RequestParam(required = false) String type) {
        return publicJson(service.filters(type));
    }

    @GetMapping("/public/property-listings/{slug}/images/{index}")
    public ResponseEntity<byte[]> image(@PathVariable String slug, @PathVariable int index) {
        GarageService.StoredObject image = service.image(slug, index);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic().mustRevalidate())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.bytes().length)
                .body(image.bytes());
    }

    @PostMapping("/public/property-listings/{slug}/inquiries")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void inquire(@PathVariable String slug, @Valid @RequestBody InquiryRequest request,
                        HttpServletRequest servletRequest) {
        service.inquire(slug, request, clientFingerprint(servletRequest));
    }

    @PutMapping("/property/listings/unit/{unitId}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).ADVERTISE_UNIT)")
    public Publication publish(@PathVariable long unitId, @Valid @RequestBody PublishRequest request) {
        return service.publish(unitId, request);
    }

    @GetMapping("/property/listings/inquiries")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).ADVERTISE_UNIT)")
    public Page<InquiryView> inquiries(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return service.myInquiries(page,size);}

    @PutMapping("/property/listings/inquiries/{id}/status")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).ADVERTISE_UNIT)")
    public InquiryView inquiryStatus(@PathVariable long id,@Valid @RequestBody InquiryStatusRequest request){return service.updateInquiry(id,request);}

    @GetMapping("/property/listings/admin")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_PROPERTY_LISTINGS)")
    public Page<AdminListing> admin(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "25") int size) {
        return service.adminList(page, size);
    }

    @PutMapping("/property/listings/admin/{id}/moderation")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_PROPERTY_LISTINGS)")
    public AdminListing moderate(@PathVariable long id, @Valid @RequestBody ModerateRequest request) {
        return service.moderate(id, request);
    }

    private String clientFingerprint(HttpServletRequest request) {
        return PMSUtils.getIPAddress(request);
    }

    private <T> ResponseEntity<T> publicJson(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "public, max-age=60, stale-while-revalidate=120")
                .header("X-Content-Type-Options", "nosniff").body(body);
    }
}
