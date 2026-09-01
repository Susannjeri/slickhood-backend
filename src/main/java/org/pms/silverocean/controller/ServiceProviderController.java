package org.pms.silverocean.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.utils.OutputStreamErrorHandler;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.sp.ComplaintCaseService;
import org.pms.silverocean.service.sp.ProviderDocumentService;
import org.pms.silverocean.service.sp.ProviderProfileService;
import org.pms.silverocean.service.sp.ProviderServiceService;
import org.pms.silverocean.service.sp.RefereeService;
import org.pms.silverocean.service.sp.ServiceBookingService;
import org.pms.silverocean.service.sp.ServiceCategoryService;
import org.pms.silverocean.service.sp.ServiceRatingService;
import org.pms.silverocean.service.sp.ServiceDirectoryService;
import org.pms.silverocean.service.sp.wrappers.AddRefereeRequest;
import org.pms.silverocean.service.sp.wrappers.AddServiceRequest;
import org.pms.silverocean.service.sp.wrappers.CreateBookingRequest;
import org.pms.silverocean.service.sp.wrappers.FileComplaintRequest;
import org.pms.silverocean.service.sp.wrappers.RateServiceRequest;
import org.pms.silverocean.service.sp.wrappers.SetupProfileRequest;
import org.pms.silverocean.service.sp.wrappers.PaymentAccountRequest;
import org.pms.silverocean.service.sp.wrappers.CompletionEvidenceRequest;
import org.pms.silverocean.service.sp.wrappers.MarketplaceFinanceRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.math.BigDecimal;

@RestController
@RequestMapping("/sp")
@PreAuthorize("isAuthenticated()")
@Validated
@Slf4j
public class ServiceProviderController extends OutputStreamErrorHandler {

    private final ProviderProfileService profileService;
    private final ServiceCategoryService categoryService;
    private final ProviderServiceService serviceService;
    private final ProviderDocumentService documentService;
    private final RefereeService refereeService;
    private final ServiceBookingService bookingService;
    private final ServiceRatingService ratingService;
    private final ComplaintCaseService complaintService;
    private final ServiceDirectoryService directoryService;

    public ServiceProviderController(I18NService i18NService,
                                     ProviderProfileService profileService,
                                     ServiceCategoryService categoryService,
                                     ProviderServiceService serviceService,
                                     ProviderDocumentService documentService,
                                     RefereeService refereeService,
                                     ServiceBookingService bookingService,
                                     ServiceRatingService ratingService,
                                     ComplaintCaseService complaintService,
                                     ServiceDirectoryService directoryService) {
        super(i18NService);
        this.profileService = profileService;
        this.categoryService = categoryService;
        this.serviceService = serviceService;
        this.documentService = documentService;
        this.refereeService = refereeService;
        this.bookingService = bookingService;
        this.ratingService = ratingService;
        this.complaintService = complaintService;
        this.directoryService = directoryService;
    }

    @GetMapping("/directory")
    public ResponseEntity<ResponseDTO> directory(Pageable pageable,
                                                  @RequestParam(required = false) Long categoryId,
                                                  @RequestParam(required = false) String query,
                                                  @RequestParam(required = false) BigDecimal minAmount,
                                                  @RequestParam(required = false) BigDecimal maxAmount,
                                                  @RequestParam(required = false) Double latitude,
                                                  @RequestParam(required = false) Double longitude,
                                                  @RequestParam(required = false) Double radiusKm) {
        var page = directoryService.searchDirectory(pageable, categoryId, query, minAmount, maxAmount, latitude, longitude, radiusKm);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_SERVICE_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_SERVICE_LIST), page.getContent(),
                page.getTotalPages(), page.getTotalElements(), page.getSize()));
    }

    @GetMapping("/directory/{serviceId}")
    public ResponseEntity<ResponseDTO> directoryDetails(@PathVariable long serviceId) {
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_SERVICE_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_SERVICE_LIST), directoryService.getServiceDetails(serviceId)));
    }

    @PostMapping("/profile/setup")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).SETUP_SP_PROFILE)")
    public ResponseEntity<ResponseDTO> setupProfile(@RequestBody @Valid SetupProfileRequest request, HttpServletRequest httpRequest) {
        var profile = profileService.setupProfile(request, PMSUtils.getIPAddress(httpRequest));
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_PROFILE_CREATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_PROFILE_CREATED), profile));
    }

    @GetMapping("/profile")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_SP_PROFILE)")
    public ResponseEntity<ResponseDTO> getMyProfile() {
        var profile = profileService.getMyProfile();
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_PROFILE_DETAILS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_PROFILE_DETAILS), profile));
    }

    @PutMapping("/profile/payment-account")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).SETUP_SP_PROFILE)")
    public ResponseEntity<ResponseDTO> setPaymentAccount(@RequestBody @Valid PaymentAccountRequest request) {
        var profile = profileService.setPaymentAccount(request);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_PROFILE_DETAILS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_PROFILE_DETAILS), profile));
    }

    @GetMapping("/category/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_SP_CATEGORY_LIST)")
    public ResponseEntity<ResponseDTO> listCategories(Pageable pageable) {
        var page = categoryService.listCategories(pageable);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_CATEGORY_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_CATEGORY_LIST),
                page.getContent(), page.getTotalPages(), page.getTotalElements(), page.getSize()));
    }

    @GetMapping("/pricing/unit/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_SP_CATEGORY_LIST)")
    public ResponseEntity<ResponseDTO> listPricingUnitList() {
        var pricingList = categoryService.listPricingUnitList();
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_PRICING_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_PRICING_LIST), pricingList));
    }

    @PostMapping("/service/add")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).ADD_SP_SERVICE)")
    public ResponseEntity<ResponseDTO> addService(@RequestBody @Valid AddServiceRequest request) {
        var service = serviceService.addService(request);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_SERVICE_ADDED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_SERVICE_ADDED), service));
    }

    @PutMapping("/service/{serviceId}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_SP_SERVICE)")
    public ResponseEntity<ResponseDTO> editService(@PathVariable long serviceId,
                                                   @RequestBody @Valid AddServiceRequest request) {
        var service = serviceService.editService(serviceId, request);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_SERVICE_UPDATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_SERVICE_UPDATED), service));
    }

    @PutMapping("/service/{serviceId}/submit")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_SP_SERVICE)")
    public ResponseEntity<ResponseDTO> submitServiceForReview(@PathVariable long serviceId) {
        serviceService.submitForReview(serviceId);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_SERVICE_SUBMITTED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_SERVICE_SUBMITTED)));
    }

    @GetMapping("/service/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_SP_SERVICE)")
    public ResponseEntity<ResponseDTO> listMyServices(Pageable pageable) {
        var page = serviceService.listMyServices(pageable);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_SERVICE_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_SERVICE_LIST),
                page.getContent(), page.getTotalPages(), page.getTotalElements(), page.getSize()));
    }

    @DeleteMapping("/service/{serviceId}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).REMOVE_SP_SERVICE)")
    public ResponseEntity<ResponseDTO> removeService(@PathVariable long serviceId) {
        serviceService.removeService(serviceId);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_SERVICE_STATUS_UPDATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_SERVICE_STATUS_UPDATED)));
    }

    @PostMapping("/document/{serviceId}/upload")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).UPLOAD_SP_DOCUMENT)")
    public ResponseEntity<ResponseDTO> uploadDocument(@PathVariable long serviceId,
                                                      @RequestParam MultipartFile file,
                                                      @RequestParam String documentType,
                                                      @RequestParam(required = false) String expiryDate) throws IOException {
        ZonedDateTime expiry = expiryDate != null ? ZonedDateTime.parse(expiryDate) : null;
        var document = documentService.uploadDocument(serviceId, file, documentType, expiry);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_DOCUMENT_UPLOADED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_DOCUMENT_UPLOADED), document));
    }

    @GetMapping("/document/{serviceId}/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).UPLOAD_SP_DOCUMENT)")
    public ResponseEntity<ResponseDTO> listDocuments(@PathVariable long serviceId, Pageable pageable) {
        var page = documentService.listMyDocumentsForService(serviceId, pageable);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_DOCUMENT_UPLOADED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_DOCUMENT_UPLOADED),
                page.getContent(), page.getTotalPages(), page.getTotalElements(), page.getSize()));
    }

    @PostMapping("/referee/add")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).ADD_SP_REFEREE)")
    public ResponseEntity<ResponseDTO> addReferee(@RequestBody @Valid AddRefereeRequest request) {
        var referee = refereeService.addReferee(request);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_REFEREE_ADDED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_REFEREE_ADDED), referee));
    }

    @GetMapping("/referee/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).ADD_SP_REFEREE)")
    public ResponseEntity<ResponseDTO> listMyReferees(Pageable pageable) {
        var page = refereeService.listMyReferees(pageable);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_REFEREE_ADDED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_REFEREE_ADDED),
                page.getContent(), page.getTotalPages(), page.getTotalElements(), page.getSize()));
    }

    @PutMapping("/referee/{refereeId}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_SP_REFEREE)")
    public ResponseEntity<ResponseDTO> editReferee(@PathVariable long refereeId,
                                                   @RequestBody @Valid AddRefereeRequest request) {
        var referee = refereeService.editReferee(refereeId, request);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_REFEREE_UPDATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_REFEREE_UPDATED), referee));
    }

    @DeleteMapping("/referee/{refereeId}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).REMOVE_SP_REFEREE)")
    public ResponseEntity<ResponseDTO> removeReferee(@PathVariable long refereeId) {
        refereeService.removeReferee(refereeId);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_REFEREE_REMOVED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_REFEREE_REMOVED)));
    }

    @PostMapping("/booking/create")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).CREATE_SP_BOOKING)")
    public ResponseEntity<ResponseDTO> createBooking(@RequestBody @Valid CreateBookingRequest request) {
        var booking = bookingService.createBooking(request);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_BOOKING_CREATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_BOOKING_CREATED), booking));
    }

    @PutMapping("/booking/{bookingId}/confirm")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).CONFIRM_SP_BOOKING)")
    public ResponseEntity<ResponseDTO> confirmBooking(@PathVariable long bookingId) {
        bookingService.confirmBooking(bookingId);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_BOOKING_UPDATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_BOOKING_UPDATED)));
    }

    @PutMapping("/booking/{bookingId}/complete")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).COMPLETE_SP_BOOKING)")
    public ResponseEntity<ResponseDTO> completeBooking(@PathVariable long bookingId,
                                                       @RequestBody @Valid CompletionEvidenceRequest request) {
        bookingService.completeBooking(bookingId, request.evidenceReference());
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_BOOKING_UPDATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_BOOKING_UPDATED)));
    }

    @PutMapping("/booking/{bookingId}/start")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).COMPLETE_SP_BOOKING)")
    public ResponseEntity<ResponseDTO> startBooking(@PathVariable long bookingId) {
        bookingService.startBooking(bookingId);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_BOOKING_UPDATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_BOOKING_UPDATED)));
    }

    @PutMapping("/booking/{bookingId}/cancel")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).CANCEL_SP_BOOKING)")
    public ResponseEntity<ResponseDTO> cancelBooking(@PathVariable long bookingId,
                                                     @RequestParam(required = false) String reason) {
        bookingService.cancelBooking(bookingId, reason);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_BOOKING_UPDATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_BOOKING_UPDATED)));
    }

    @PutMapping("/booking/{bookingId}/finance")
    @PreAuthorize("hasAnyRole('FINANCE','SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO> updateBookingFinance(@PathVariable long bookingId,
                                                            @RequestBody @Valid MarketplaceFinanceRequest request) {
        var booking = bookingService.updateFinance(bookingId, request);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_BOOKING_UPDATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_BOOKING_UPDATED), booking));
    }

    @GetMapping("/booking/my")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_SP_BOOKING)")
    public ResponseEntity<ResponseDTO> listMyBookings(Pageable pageable) {
        var page = bookingService.listMyBookings(pageable);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_BOOKING_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_BOOKING_LIST),
                page.getContent(), page.getTotalPages(), page.getTotalElements(), page.getSize()));
    }

    @GetMapping("/booking/service/{serviceId}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_SP_BOOKING)")
    public ResponseEntity<ResponseDTO> listBookingsForService(@PathVariable long serviceId, Pageable pageable) {
        var page = bookingService.listBookingsForService(serviceId, pageable);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_BOOKING_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_BOOKING_LIST),
                page.getContent(), page.getTotalPages(), page.getTotalElements(), page.getSize()));
    }

    @PostMapping("/rating/submit")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).RATE_SP_SERVICE)")
    public ResponseEntity<ResponseDTO> rateService(@RequestBody @Valid RateServiceRequest request) {
        var rating = ratingService.rateService(request);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_RATING_SUBMITTED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_RATING_SUBMITTED), rating));
    }

    @GetMapping("/rating/{serviceId}/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_SP_RATINGS)")
    public ResponseEntity<ResponseDTO> listRatings(@PathVariable long serviceId, Pageable pageable) {
        var page = ratingService.listRatingsForService(serviceId, pageable);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_RATING_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_RATING_LIST),
                page.getContent(), page.getTotalPages(), page.getTotalElements(), page.getSize()));
    }

    @PostMapping("/complaint/file")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).FILE_SP_COMPLAINT)")
    public ResponseEntity<ResponseDTO> fileComplaint(@RequestBody @Valid FileComplaintRequest request) {
        var complaint = complaintService.fileComplaint(request);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_COMPLAINT_FILED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_COMPLAINT_FILED), complaint));
    }

    @GetMapping("/complaint/service/{serviceId}/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).FILE_SP_COMPLAINT)")
    public ResponseEntity<ResponseDTO> listComplaintsForService(@PathVariable long serviceId, Pageable pageable) {
        var page = complaintService.listComplaintsForService(serviceId, pageable);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_COMPLAINT_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_COMPLAINT_LIST),
                page.getContent(), page.getTotalPages(), page.getTotalElements(), page.getSize()));
    }
}
