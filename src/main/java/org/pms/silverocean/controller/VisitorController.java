package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.utils.OutputStreamErrorHandler;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.visitor.VisitorService;
import org.pms.silverocean.service.visitor.VisitorAccessService;
import org.pms.silverocean.service.visitor.enums.VisitorStatus;
import org.pms.silverocean.service.visitor.wrappers.CreateVisitorRequest;
import org.pms.silverocean.service.visitor.wrappers.UpdateVisitorStatusRequest;
import org.pms.silverocean.service.visitor.wrappers.VisitorDTO;
import org.pms.silverocean.service.visitor.wrappers.RegisterVisitRequest;
import org.pms.silverocean.service.visitor.wrappers.VisitorDecisionRequest;
import org.springframework.data.domain.Page;
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

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/visitor")
@Validated
@Slf4j
public class VisitorController extends OutputStreamErrorHandler {

    private final VisitorService visitorService;
    private final VisitorAccessService visitorAccessService;

    public VisitorController(I18NService i18NService, VisitorService visitorService, VisitorAccessService visitorAccessService) {
        super(i18NService);
        this.visitorService = visitorService;
        this.visitorAccessService = visitorAccessService;
    }

    @PostMapping("/access/register")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).REGISTER_VISITOR)")
    public ResponseEntity<ResponseDTO> registerAccessVisit(@RequestBody @Valid RegisterVisitRequest request) {
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.VISITOR_REGISTERED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.VISITOR_REGISTERED), visitorAccessService.registerExpected(request)));
    }

    @PostMapping("/access/walk-in")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).UPDATE_VISITOR_STATUS)")
    public ResponseEntity<ResponseDTO> registerWalkIn(@RequestBody @Valid RegisterVisitRequest request) {
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.VISITOR_REGISTERED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.VISITOR_REGISTERED), visitorAccessService.registerUnplanned(request)));
    }

    @GetMapping("/access/guard-options")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).UPDATE_VISITOR_STATUS)")
    public ResponseEntity<ResponseDTO> guardOptions() {
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), visitorAccessService.guardHostOptions()));
    }

    @PutMapping("/{visitorId}/decision")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).REGISTER_VISITOR)")
    public ResponseEntity<ResponseDTO> decideVisit(@PathVariable long visitorId,
                                                    @RequestBody @Valid VisitorDecisionRequest request) {
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.VISITOR_STATUS_UPDATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.VISITOR_STATUS_UPDATED), visitorAccessService.decide(visitorId, request)));
    }

    @GetMapping("/category")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_VISITOR_LIST)")
    public ResponseEntity<ResponseDTO> getVisitorCategory() {
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), visitorService.getVisitorCategoryList()));
    }

    @PostMapping("/register")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).REGISTER_VISITOR)")
    public ResponseEntity<ResponseDTO> registerVisitor(@RequestBody @Valid CreateVisitorRequest request) {
        visitorService.preRegisterVisitor(request);
        return ResponseEntity.ok(new ResponseDTO(
                true,
                ResponseCode.VISITOR_REGISTERED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.VISITOR_REGISTERED)
        ));
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_VISITOR_LIST)")
    public ResponseEntity<ResponseDTO> listMyVisitors(Pageable pageable,
                                                      @RequestParam(required = false) Optional<String> phoneNumber) {
        List<VisitorDTO> visitors = visitorService.listMyVisitors(pageable, phoneNumber);
        return ResponseEntity.ok(new ResponseDTO(
                true,
                ResponseCode.VISITOR_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.VISITOR_LIST),
                visitors
        ));
    }

    @GetMapping("/unit/{unitId}/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_VISITOR_LIST)")
    public ResponseEntity<ResponseDTO> listVisitorsByUnit(Pageable pageable,
                                                          @PathVariable long unitId,
                                                          @RequestParam Optional<VisitorStatus> status) {
        Page<VisitorDTO> visitors = visitorService.listVisitorsByUnit(pageable, unitId, status);
        return ResponseEntity.ok(new ResponseDTO(
                true,
                ResponseCode.VISITOR_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.VISITOR_LIST),
                visitors.getContent(),
                visitors.getTotalPages(),
                visitors.getTotalElements(),
                visitors.getSize()
        ));
    }

    @PutMapping("/{visitorId}/status")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).UPDATE_VISITOR_STATUS)")
    public ResponseEntity<ResponseDTO> checkInOrCheckOutVisitor(@PathVariable long visitorId,
                                                                @RequestBody @Valid UpdateVisitorStatusRequest request) {
        visitorService.updateVisitorStatus(visitorId, request);
        return ResponseEntity.ok(new ResponseDTO(
                true,
                ResponseCode.VISITOR_STATUS_UPDATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.VISITOR_STATUS_UPDATED)
        ));
    }

    @PutMapping("/{visitorId}/cancel")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).CANCEL_VISITOR)")
    public ResponseEntity<ResponseDTO> cancelVisitor(@PathVariable long visitorId) {
        visitorService.cancelVisitor(visitorId);
        return ResponseEntity.ok(new ResponseDTO(
                true,
                ResponseCode.VISITOR_CANCELLED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.VISITOR_CANCELLED)
        ));
    }

    @DeleteMapping("/{visitorId}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).DELETE_VISITOR)")
    public ResponseEntity<ResponseDTO> deleteVisitor(@PathVariable long visitorId) {
        visitorService.deleteVisitor(visitorId);
        return ResponseEntity.ok(new ResponseDTO(
                true,
                ResponseCode.VISITOR_DELETED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.VISITOR_DELETED)
        ));
    }
}
