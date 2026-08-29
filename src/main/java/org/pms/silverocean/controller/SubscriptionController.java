package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.controller.wrappers.SubscriptionCurrentDTO;
import org.pms.silverocean.controller.wrappers.SubscriptionAutoRenewDTO;
import org.pms.silverocean.controller.wrappers.SubscriptionCancelDTO;
import org.pms.silverocean.controller.wrappers.SubscriptionRenewDTO;
import org.pms.silverocean.controller.wrappers.SubscriptionPlanChangeDTO;
import org.pms.silverocean.controller.wrappers.SubscriptionSalesRequestDTO;
import org.pms.silverocean.controller.wrappers.SubscriptionTrialDTO;
import org.pms.silverocean.controller.wrappers.SubscriptionTrialPolicyDTO;
import org.pms.silverocean.controller.wrappers.SubscriptionSubscribeRestDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.account.AccountService;
import org.pms.silverocean.service.subscription.SubscriptionProvisioningService;
import org.pms.silverocean.service.subscription.SubscriptionManagementService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/subscription")
public class SubscriptionController {

    private final SubscriptionProvisioningService subscriptionProvisioningService;
    private final I18NService i18NService;
    private final AccountService accountService;
    private final SubscriptionManagementService subscriptionManagementService;

    public SubscriptionController(
            SubscriptionProvisioningService subscriptionProvisioningService,
            I18NService i18NService,
            AccountService accountService,
            SubscriptionManagementService subscriptionManagementService
    ) {
        this.subscriptionProvisioningService = subscriptionProvisioningService;
        this.i18NService = i18NService;
        this.accountService = accountService;
        this.subscriptionManagementService = subscriptionManagementService;
    }

    @GetMapping("/current")
    public ResponseEntity<ResponseDTO> getCurrentSubscription(
            @RequestParam(value = "role", required = false) String role) {
        SubscriptionCurrentDTO dto = role == null || role.isBlank()
                ? subscriptionProvisioningService.getCurrentSubscription()
                : subscriptionProvisioningService.getCurrentSubscriptionForSessionRole(role);
        if (dto == null) {
            return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SUBSCRIPTION_CURRENT_ABSENT.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.SUBSCRIPTION_CURRENT_ABSENT)));
        }
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SUBSCRIPTION_CURRENT.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SUBSCRIPTION_CURRENT), dto));
    }

    /**
     * @param role PMS persona enum constant name (e.g. LANDLORD, SERVICE_PROVIDER, AFFILIATE, ASSET_PORTFOLIO_MANAGER)
     */
    @GetMapping("/plans")
    public ResponseEntity<ResponseDTO> listCatalogForRole(@RequestParam("role") String role) {
        var plans = subscriptionProvisioningService.listPlansSummariesForRoleParam(role);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SUBSCRIPTION_CATALOG_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SUBSCRIPTION_CATALOG_LIST), plans));
    }

    @GetMapping("/payment-accounts")
    public ResponseEntity<ResponseDTO> listPaymentAccounts() {
        var accounts = accountService.listAccounts(PageRequest.of(0, 50),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.of(true),
                java.util.Optional.empty(), java.util.Optional.empty(), true, true);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.ACCOUNT_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.ACCOUNT_LIST), accounts.getContent()));
    }

    @GetMapping("/trial-policy")
    public ResponseEntity<ResponseDTO> trialPolicy() {
        return ok(new SubscriptionTrialPolicyDTO(subscriptionProvisioningService.getTrialDays()));
    }

    @PostMapping("/trial")
    public ResponseEntity<ResponseDTO> startTrial(@Valid @RequestBody SubscriptionTrialDTO body) {
        var subscription = subscriptionProvisioningService.startTrialForSessionUser(body.role(), body.planCode());
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SUBSCRIPTION_TRIAL_STARTED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SUBSCRIPTION_TRIAL_STARTED), subscription));
    }

    @GetMapping("/overview")
    public ResponseEntity<ResponseDTO> overview(@RequestParam("role") String role) {
        return ok(subscriptionManagementService.overview(role));
    }

    @GetMapping("/billing-history")
    public ResponseEntity<ResponseDTO> billingHistory(Pageable pageable) {
        var page = subscriptionManagementService.billingHistory(pageable);
        ResponseDTO response = new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), page.getContent());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setSize(page.getSize());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/auto-renew")
    public ResponseEntity<ResponseDTO> updateAutoRenew(@RequestParam("role") String role,
                                                        @Valid @RequestBody SubscriptionAutoRenewDTO body) {
        return ok(subscriptionManagementService.updateAutoRenew(role, body.enabled()));
    }

    @PostMapping("/cancel")
    public ResponseEntity<ResponseDTO> cancel(@RequestParam("role") String role,
                                               @Valid @RequestBody SubscriptionCancelDTO body) {
        return ok(subscriptionManagementService.scheduleCancellation(role, body.reason()));
    }

    @PostMapping("/cancel/restore")
    public ResponseEntity<ResponseDTO> restoreCancellation(@RequestParam("role") String role) {
        return ok(subscriptionManagementService.restoreCancellation(role));
    }

    @PostMapping("/renew")
    public ResponseEntity<ResponseDTO> renew(@RequestParam("role") String role,
                                              @Valid @RequestBody SubscriptionRenewDTO body) {
        var result = subscriptionManagementService.renew(role, body.paymentAccountId());
        return ok(result.requiresPayment() ? result.pendingPayment() : result.assignedSubscription());
    }

    @PostMapping("/change-plan")
    public ResponseEntity<ResponseDTO> schedulePlanChange(@RequestParam("role") String role,
                                                           @Valid @RequestBody SubscriptionPlanChangeDTO body) {
        return ok(subscriptionManagementService.schedulePlanChange(role, body.planCode()));
    }

    @PostMapping("/change-plan/revoke")
    public ResponseEntity<ResponseDTO> revokePlanChange(@RequestParam("role") String role) {
        return ok(subscriptionManagementService.revokePlanChange(role));
    }

    @PostMapping("/contact-sales")
    public ResponseEntity<ResponseDTO> contactSales(@Valid @RequestBody SubscriptionSalesRequestDTO body) {
        subscriptionManagementService.requestSalesContact(body.planCode(), body.message());
        return ok(null);
    }

    /**
     * Subscribe or change plan for the logged-in user (self-service only).
     */
    @PostMapping("/subscribe")
    public ResponseEntity<ResponseDTO> subscribe(@Valid @RequestBody SubscriptionSubscribeRestDTO body) {
        var result = subscriptionProvisioningService.subscribeOrUpgradeForSessionUser(
                body.role(),
                body.planCode(),
                body.paymentAccountId());
        if (result.requiresPayment()) {
            return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SUBSCRIPTION_CHECKOUT_INVOICE_CREATED.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.SUBSCRIPTION_CHECKOUT_INVOICE_CREATED), result.pendingPayment()));
        }
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.SUBSCRIPTION_ASSIGNED_FREE.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.SUBSCRIPTION_ASSIGNED_FREE), result.assignedSubscription()));
    }

    private ResponseEntity<ResponseDTO> ok(Object data) {
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), data));
    }
}
