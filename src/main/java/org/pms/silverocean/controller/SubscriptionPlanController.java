package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.subscription.PlanCatalogUpsertDTO;
import org.pms.silverocean.service.subscription.PlanStatusUpdateDTO;
import org.pms.silverocean.service.subscription.SubscriptionPlanRequestDTO;
import org.pms.silverocean.service.subscription.SubscriptionPlanResponseDTO;
import org.pms.silverocean.service.subscription.SubscriptionPlanService;
import org.pms.silverocean.service.subscription.enums.PlanCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/plans")
public class SubscriptionPlanController {
    private final SubscriptionPlanService subscriptionPlanService;
    private final I18NService i18NService;

    public SubscriptionPlanController(SubscriptionPlanService subscriptionPlanService, I18NService i18NService) {
        this.subscriptionPlanService = subscriptionPlanService;
        this.i18NService = i18NService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).CREATE_SUBSCRIPTION_PLAN)")
    public ResponseEntity<ResponseDTO> createPlan(@Valid @RequestBody SubscriptionPlanRequestDTO request) {
        SubscriptionPlanResponseDTO plan = subscriptionPlanService.createPlan(request);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SUBSCRIPTION_PLAN_CREATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SUBSCRIPTION_PLAN_CREATED), plan));
    }

    @PutMapping("/{planCode}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_SUBSCRIPTION_PLAN)")
    public ResponseEntity<ResponseDTO> updatePlan(@PathVariable String planCode, @Valid @RequestBody SubscriptionPlanRequestDTO request) {
        SubscriptionPlanResponseDTO plan = subscriptionPlanService.updatePlan(planCode, request);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SUBSCRIPTION_PLAN_UPDATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SUBSCRIPTION_PLAN_UPDATED), plan));
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_SUBSCRIPTION_PLAN)")
    public ResponseEntity<ResponseDTO> listPlans(Pageable pageable, @RequestParam(required = false) PlanCategory category) {
        Page<SubscriptionPlanResponseDTO> plans = subscriptionPlanService.listPlans(pageable, category);
        ResponseDTO responseDTO = new ResponseDTO(true, ResponseCode.SUBSCRIPTION_PLAN_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SUBSCRIPTION_PLAN_LIST), plans.getContent());
        responseDTO.setSize(plans.getSize());
        responseDTO.setTotalPages(plans.getTotalPages());
        responseDTO.setTotalElements(plans.getTotalElements());
        return ResponseEntity.ok(responseDTO);
    }

    @PutMapping("/{planCode}/catalog")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_SUBSCRIPTION_PLAN)")
    public ResponseEntity<ResponseDTO> upsertPlanCatalog(
            @PathVariable String planCode,
            @Valid @RequestBody PlanCatalogUpsertDTO request
    ) {
        SubscriptionPlanResponseDTO plan = subscriptionPlanService.upsertPlanCatalog(planCode, request);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SUBSCRIPTION_PLAN_UPDATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SUBSCRIPTION_PLAN_UPDATED), plan));
    }

    @GetMapping("/{planCode}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_SUBSCRIPTION_PLAN)")
    public ResponseEntity<ResponseDTO> getPlan(@PathVariable String planCode) {
        SubscriptionPlanResponseDTO plan = subscriptionPlanService.getPlanByCode(planCode);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SUBSCRIPTION_PLAN_DETAILS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SUBSCRIPTION_PLAN_DETAILS), plan));
    }

    @PatchMapping("/{planCode}/status")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_SUBSCRIPTION_PLAN)")
    public ResponseEntity<ResponseDTO> updateStatus(@PathVariable String planCode, @Valid @RequestBody PlanStatusUpdateDTO request) {
        subscriptionPlanService.updatePlanStatus(planCode, request.active());
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SUBSCRIPTION_PLAN_STATUS_UPDATED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SUBSCRIPTION_PLAN_STATUS_UPDATED)));
    }
}
