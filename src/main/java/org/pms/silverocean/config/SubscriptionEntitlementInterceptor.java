package org.pms.silverocean.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.pms.silverocean.service.subscription.SubscriptionEntitlementService;
import org.pms.silverocean.service.subscription.enums.SubscriptionProduct;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SubscriptionEntitlementInterceptor implements HandlerInterceptor {
    private final SubscriptionEntitlementService entitlements;
    public SubscriptionEntitlementInterceptor(SubscriptionEntitlementService entitlements) { this.entitlements = entitlements; }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        // Gate devices authenticate with their own timestamped HMAC signature in the
        // controller/service. They do not have a user subscription session.
        if (path.startsWith("/smart-gate/device/")) return true;
        // Affiliate participation is a commission programme, not a subscribed
        // SlickHood product. Endpoint role checks still protect affiliate and
        // administrator data, but neither persona may be sent through billing.
        if (path.startsWith("/affiliate")) return true;
        // Finance operations are staff workflows protected by a FINANCE-only
        // endpoint role. Staff must never need to buy a provider subscription.
        if (path.matches("/sp/booking/[^/]+/finance")) return true;
        // Finance, administration and linked-rider operations are protected by
        // endpoint roles/ownership. Requiring the merchant's Soko subscription
        // here would incorrectly bill staff and riders merely to perform work.
        if (isSokoOperationalExemption(path)) return true;
        // Subscription expiry must prevent a provider from taking or publishing
        // new commercial work, but it must never strand an existing paid job.
        // Controller permissions and service ownership/state checks continue to
        // protect these buyer and fulfilment operations.
        if (isServicesOperationalExemption(path, request.getMethod())) return true;
        if (path.startsWith("/smart-gate")) {
            entitlements.requireFeatureOrAddOn(entitlements.sessionBusinessProduct(),
                    "GATE_MANAGEMENT_INCLUDED_UNITS", SubscriptionProduct.GATE_MANAGEMENT_ADDON);
            return true;
        }
        if (path.startsWith("/property")) {
            entitlements.requireSessionFeatureIfApplicable("PROPERTY_AND_UNIT_MANAGEMENT",
                    "ESTATE_AND_HOMEOWNER_MANAGEMENT", "PROPERTY_SALES");
            return true;
        }
        if (path.startsWith("/lease")) {
            entitlements.requireSessionFeatureIfApplicable("LEASE_MANAGEMENT", null, null);
            return true;
        }
        if (path.startsWith("/estate")) {
            entitlements.requireSessionFeatureIfApplicable(null, "ESTATE_MANAGEMENT", null);
            return true;
        }
        if (path.startsWith("/sales")) {
            entitlements.requireSessionFeatureIfApplicable(null, null, "PROPERTY_SALES");
            return true;
        }
        if (path.startsWith("/wealth")) {
            entitlements.requireFeatureOrAddOn(entitlements.sessionBusinessProduct(),
                    "WEALTH_INCLUDED_UNITS", SubscriptionProduct.MY_WEALTH);
            return true;
        }
        SubscriptionProduct product = product(path);
        if (product != null) {
            entitlements.requireProduct(product);
        } else {
            entitlements.requireSessionBusinessProductIfApplicable();
        }
        return true;
    }

    private SubscriptionProduct product(String path) {
        if (path.startsWith("/soko/store") || path.startsWith("/soko/product")
                || path.startsWith("/soko/rider") || path.equals("/soko/order/merchant")
                || path.matches("/soko/order/[^/]+/status")) return SubscriptionProduct.SOKO;
        if (path.startsWith("/sp/profile") || path.startsWith("/sp/service")
                || path.startsWith("/sp/document") || path.startsWith("/sp/referee")
                || path.matches("/sp/booking/[^/]+/(confirm|complete|start|finance)")) {
            return SubscriptionProduct.SERVICES;
        }
        return null;
    }

    private boolean isSokoOperationalExemption(String path) {
        return path.startsWith("/soko/catalog")
                || path.equals("/soko/categories")
                || path.equals("/soko/delivery-destinations")
                || path.startsWith("/soko/order/")
                || path.startsWith("/soko/finance/")
                || path.equals("/soko/rider/assignments")
                || path.startsWith("/soko/rider/kyc")
                ;
    }

    private boolean isServicesOperationalExemption(String path, String method) {
        if (path.startsWith("/sp/directory")
                || path.equals("/sp/category/list")
                || path.equals("/sp/pricing/unit/list")
                || path.equals("/sp/booking/my")
                || path.matches("/sp/booking/service/[^/]+")) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method)) {
            return path.equals("/sp/profile")
                    || path.equals("/sp/service/list")
                    || path.matches("/sp/service/[^/]+/readiness")
                    || path.startsWith("/sp/rating/")
                    || path.startsWith("/sp/complaint/");
        }
        if ("POST".equalsIgnoreCase(method)) {
            return path.equals("/sp/booking/create")
                    || path.equals("/sp/rating/submit")
                    || path.equals("/sp/complaint/file");
        }
        if ("PUT".equalsIgnoreCase(method)) {
            return path.matches("/sp/booking/[^/]+/(start|complete|cancel|accept-completion)")
                    || path.matches("/sp/service/[^/]+/pause");
        }
        return false;
    }
}
