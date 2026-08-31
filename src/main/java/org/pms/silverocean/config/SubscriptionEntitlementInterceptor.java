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
        SubscriptionProduct product = product(path);
        if (product != null) {
            entitlements.requireProduct(product);
        } else {
            entitlements.requireSessionBusinessProductIfApplicable();
        }
        return true;
    }

    private SubscriptionProduct product(String path) {
        if (path.startsWith("/wealth")) return SubscriptionProduct.MY_WEALTH;
        if (path.startsWith("/smart-gate")) return SubscriptionProduct.GATE_MANAGEMENT_ADDON;
        if (path.startsWith("/soko/store") || path.startsWith("/soko/product")
                || path.startsWith("/soko/rider") || path.equals("/soko/order/merchant")
                || path.matches("/soko/order/[^/]+/(status|finance)")) return SubscriptionProduct.SOKO;
        if (path.startsWith("/sp/profile") || path.startsWith("/sp/service")
                || path.startsWith("/sp/document") || path.startsWith("/sp/referee")
                || path.matches("/sp/booking/[^/]+/(confirm|complete|start|finance)")) {
            return SubscriptionProduct.SERVICES;
        }
        if (path.startsWith("/affiliate") && !path.startsWith("/affiliate/public")) return SubscriptionProduct.AFFILIATE;
        return null;
    }
}
