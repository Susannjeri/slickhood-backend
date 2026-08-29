package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.utils.OutputStreamErrorHandler;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.sp.ComplaintCaseService;
import org.pms.silverocean.service.sp.ProviderDocumentService;
import org.pms.silverocean.service.sp.ProviderProfileService;
import org.pms.silverocean.service.sp.ProviderServiceService;
import org.pms.silverocean.service.sp.RefereeService;
import org.pms.silverocean.service.sp.ServiceCategoryService;
import org.pms.silverocean.service.sp.dao.ServiceTierDao;
import org.pms.silverocean.service.sp.enums.DocumentStatus;
import org.pms.silverocean.service.sp.enums.RefereeStatus;
import org.pms.silverocean.service.sp.wrappers.AssignTierRequest;
import org.pms.silverocean.service.sp.wrappers.CreateCategoryRequest;
import org.pms.silverocean.service.sp.wrappers.ReviewComplaintRequest;
import org.pms.silverocean.service.sp.wrappers.ServiceTierDTO;
import org.pms.silverocean.service.wrappers.EnumWrapper;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/sp/admin")
@Validated
@Slf4j
public class ServiceProviderAdminController extends OutputStreamErrorHandler {

    private final ProviderProfileService profileService;
    private final ProviderServiceService serviceService;
    private final ProviderDocumentService documentService;
    private final RefereeService refereeService;
    private final ComplaintCaseService complaintService;
    private final ServiceTierDao tierDao;
    private final ServiceCategoryService categoryService;

    public ServiceProviderAdminController(I18NService i18NService,
                                          ProviderProfileService profileService,
                                          ProviderServiceService serviceService,
                                          ProviderDocumentService documentService,
                                          RefereeService refereeService,
                                          ComplaintCaseService complaintService,
                                          ServiceTierDao tierDao,
                                          ServiceCategoryService categoryService) {
        super(i18NService);
        this.profileService = profileService;
        this.serviceService = serviceService;
        this.documentService = documentService;
        this.refereeService = refereeService;
        this.complaintService = complaintService;
        this.tierDao = tierDao;
        this.categoryService = categoryService;
    }

    @GetMapping("/profile/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_SP_LIST)")
    public ResponseEntity<ResponseDTO> getProfileByList(Pageable pageable) {
        var profilePage = profileService.getProfileList(pageable);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_PROFILE_DETAILS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_PROFILE_DETAILS),
                profilePage.getContent(), profilePage.getTotalPages(), profilePage.getTotalElements(), profilePage.getSize()));
    }

    @GetMapping("/profile/{profileId}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_SP_LIST)")
    public ResponseEntity<ResponseDTO> getProfileById(@PathVariable long profileId) {
        var profile = profileService.getProfileById(profileId);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_PROFILE_DETAILS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_PROFILE_DETAILS), profile));
    }

    @GetMapping("/service/pending")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).APPROVE_SP_SERVICE)")
    public ResponseEntity<ResponseDTO> listPendingAdminReview(Pageable pageable) {
        var page = serviceService.listPendingAdminReview(pageable);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_SERVICE_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_SERVICE_LIST),
                page.getContent(), page.getTotalPages(), page.getTotalElements(), page.getSize()));
    }

    @PutMapping("/service/{serviceId}/approve")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).APPROVE_SP_SERVICE)")
    public ResponseEntity<ResponseDTO> approveService(@PathVariable long serviceId,
                                                      @RequestParam(required = false) String adminNotes) {
        serviceService.approveService(serviceId, adminNotes);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_SERVICE_STATUS_UPDATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_SERVICE_STATUS_UPDATED)));
    }

    @PutMapping("/service/{serviceId}/reject")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).APPROVE_SP_SERVICE)")
    public ResponseEntity<ResponseDTO> rejectService(@PathVariable long serviceId,
                                                     @RequestParam(required = false) String adminNotes) {
        serviceService.rejectService(serviceId, adminNotes);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_SERVICE_STATUS_UPDATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_SERVICE_STATUS_UPDATED)));
    }

    @PutMapping("/service/{serviceId}/suspend")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).SUSPEND_SP_SERVICE)")
    public ResponseEntity<ResponseDTO> suspendService(@PathVariable long serviceId,
                                                      @RequestParam(required = false) String reason) {
        serviceService.suspendService(serviceId, reason);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_SERVICE_STATUS_UPDATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_SERVICE_STATUS_UPDATED)));
    }

    @PutMapping("/service/{serviceId}/tier")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).ASSIGN_SP_TIER)")
    public ResponseEntity<ResponseDTO> assignTier(@PathVariable long serviceId,
                                                  @RequestBody @Valid AssignTierRequest request) {
        serviceService.assignTier(serviceId, request);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_TIER_ASSIGNED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_TIER_ASSIGNED)));
    }

    @GetMapping("/document/{serviceId}/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_SP_DOCUMENT)")
    public ResponseEntity<ResponseDTO> listDocuments(@PathVariable long serviceId, Pageable pageable) {
        var page = documentService.listDocumentsForService(serviceId, pageable);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_DOCUMENT_UPLOADED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_DOCUMENT_UPLOADED),
                page.getContent(), page.getTotalPages(), page.getTotalElements(), page.getSize()));
    }

    @PutMapping("/document/{documentId}/verify")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VERIFY_SP_DOCUMENT)")
    public ResponseEntity<ResponseDTO> verifyDocument(@PathVariable long documentId,
                                                      @RequestParam DocumentStatus status) {
        documentService.verifyDocument(documentId, status);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_DOCUMENT_VERIFIED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_DOCUMENT_VERIFIED)));
    }

    @GetMapping("/referee/{profileId}/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VERIFY_SP_REFEREE)")
    public ResponseEntity<ResponseDTO> listRefereesForProfile(@PathVariable long profileId, Pageable pageable) {
        var page = refereeService.listRefereesForProfile(profileId, pageable);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_REFEREE_ADDED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_REFEREE_ADDED),
                page.getContent(), page.getTotalPages(), page.getTotalElements(), page.getSize()));
    }

    @PutMapping("/referee/{refereeId}/verify")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VERIFY_SP_REFEREE)")
    public ResponseEntity<ResponseDTO> verifyReferee(@PathVariable long refereeId,
                                                     @RequestParam RefereeStatus status) {
        refereeService.verifyReferee(refereeId, status);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_REFEREE_VERIFIED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_REFEREE_VERIFIED)));
    }

    @GetMapping("/complaint/open")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).REVIEW_SP_COMPLAINT)")
    public ResponseEntity<ResponseDTO> listOpenComplaints(Pageable pageable) {
        var page = complaintService.listOpenComplaints(pageable);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_COMPLAINT_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_COMPLAINT_LIST),
                page.getContent(), page.getTotalPages(), page.getTotalElements(), page.getSize()));
    }

    @PutMapping("/complaint/{complaintId}/review")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).REVIEW_SP_COMPLAINT)")
    public ResponseEntity<ResponseDTO> assignReview(@PathVariable long complaintId) {
        complaintService.assignReview(complaintId);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_COMPLAINT_UPDATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_COMPLAINT_UPDATED)));
    }

    @PutMapping("/complaint/{complaintId}/resolve")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).RESOLVE_SP_COMPLAINT)")
    public ResponseEntity<ResponseDTO> resolveComplaint(@PathVariable long complaintId,
                                                        @RequestBody @Valid ReviewComplaintRequest request) {
        complaintService.resolveComplaint(complaintId, request);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_COMPLAINT_UPDATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_COMPLAINT_UPDATED)));
    }

    @PutMapping("/profile/{profileId}/blacklist")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).BLACKLIST_SP)")
    public ResponseEntity<ResponseDTO> blacklistProfile(@PathVariable long profileId) {
        profileService.blacklistProfile(profileId);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_BLACKLISTED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_BLACKLISTED)));
    }

    @GetMapping("/tier/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).ASSIGN_SP_TIER)")
    public ResponseEntity<ResponseDTO> listTiers(Pageable pageable) {
        var page = tierDao.findAllActive(pageable).map(ServiceTierDTO::new);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_TIER_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_TIER_LIST),
                page.getContent(), page.getTotalPages(), page.getTotalElements(), page.getSize()));
    }

    @PostMapping("/category/create")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_SP_CATEGORIES)")
    public ResponseEntity<ResponseDTO> createCategory(@RequestBody @Valid CreateCategoryRequest request) {
        var category = categoryService.createCategory(request);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SP_CATEGORY_CREATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SP_CATEGORY_CREATED), category));
    }

    @PutMapping("/category/{categoryId}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_SP_CATEGORIES)")
    public ResponseEntity<ResponseDTO> updateCategory(@PathVariable long categoryId,
                                                      @RequestBody @Valid CreateCategoryRequest request) {
        var category = categoryService.updateCategory(categoryId, request);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), category));
    }

    @DeleteMapping("/category/{categoryId}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_SP_CATEGORIES)")
    public ResponseEntity<ResponseDTO> deactivateCategory(@PathVariable long categoryId) {
        categoryService.deactivateCategory(categoryId);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS)));
    }

    @GetMapping("/document/type/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_SP_CATEGORIES)")
    public ResponseEntity<ResponseDTO> listRequiredDocumentTypeList() {
        Set<EnumWrapper> tiers = categoryService.listRequiredDocumentType();
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), tiers));
    }
}
