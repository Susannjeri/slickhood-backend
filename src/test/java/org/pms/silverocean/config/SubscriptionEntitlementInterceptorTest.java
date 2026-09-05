package org.pms.silverocean.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.service.subscription.SubscriptionEntitlementService;
import org.pms.silverocean.service.subscription.enums.SubscriptionProduct;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionEntitlementInterceptorTest {
    @Mock SubscriptionEntitlementService entitlements;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;

    @Test void signedGateDeviceRequestIsNotTreatedAsAUserSubscription() {
        when(request.getRequestURI()).thenReturn("/smart-gate/device/access-decision");

        assertTrue(new SubscriptionEntitlementInterceptor(entitlements)
                .preHandle(request, response, new Object()));

        verify(entitlements, never()).requireProduct(SubscriptionProduct.GATE_MANAGEMENT_ADDON);
        verify(entitlements, never()).requireSessionBusinessProductIfApplicable();
    }

    @Test void gateAdministrationAcceptsBundledFeatureOrAddOn() {
        when(request.getRequestURI()).thenReturn("/smart-gate/devices");
        when(entitlements.sessionBusinessProduct()).thenReturn(SubscriptionProduct.ESTATE_MANAGEMENT);

        assertTrue(new SubscriptionEntitlementInterceptor(entitlements)
                .preHandle(request, response, new Object()));

        verify(entitlements).requireFeatureOrAddOn(SubscriptionProduct.ESTATE_MANAGEMENT,
                "GATE_MANAGEMENT_INCLUDED_UNITS", SubscriptionProduct.GATE_MANAGEMENT_ADDON);
    }

    @Test void propertyRoutesRequireTheFeatureForTheActiveBusinessArea() {
        when(request.getRequestURI()).thenReturn("/property/list");

        assertTrue(new SubscriptionEntitlementInterceptor(entitlements)
                .preHandle(request, response, new Object()));

        verify(entitlements).requireSessionFeatureIfApplicable("PROPERTY_AND_UNIT_MANAGEMENT",
                "ESTATE_AND_HOMEOWNER_MANAGEMENT", "PROPERTY_SALES");
        verify(entitlements, never()).requireSessionBusinessProductIfApplicable();
    }
}
