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

    @Test void affiliateProgrammeIsNotTreatedAsASubscribedProduct() {
        when(request.getRequestURI()).thenReturn("/affiliate/dashboard");

        assertTrue(new SubscriptionEntitlementInterceptor(entitlements)
                .preHandle(request, response, new Object()));

        verify(entitlements, never()).requireProduct(SubscriptionProduct.AFFILIATE);
        verify(entitlements, never()).requireSessionBusinessProductIfApplicable();
    }

    @Test void affiliateAdministrationIsNotTreatedAsASubscribedProduct() {
        when(request.getRequestURI()).thenReturn("/affiliate/admin/policy");

        assertTrue(new SubscriptionEntitlementInterceptor(entitlements)
                .preHandle(request, response, new Object()));

        verify(entitlements, never()).requireProduct(SubscriptionProduct.AFFILIATE);
        verify(entitlements, never()).requireSessionBusinessProductIfApplicable();
    }

    @Test void wealthAcceptsTheFeatureBundledWithTheActiveLandlordPlan() {
        when(request.getRequestURI()).thenReturn("/wealth/assets");
        when(entitlements.sessionBusinessProduct()).thenReturn(SubscriptionProduct.LANDLORD);

        assertTrue(new SubscriptionEntitlementInterceptor(entitlements)
                .preHandle(request, response, new Object()));

        verify(entitlements).requireFeatureOrAddOn(SubscriptionProduct.LANDLORD,
                "WEALTH_INCLUDED_UNITS", SubscriptionProduct.MY_WEALTH);
        verify(entitlements, never()).requireProduct(SubscriptionProduct.MY_WEALTH);
    }

    @Test void wealthAcceptsTheStandaloneMyWealthProduct() {
        when(request.getRequestURI()).thenReturn("/wealth/categories");
        when(entitlements.sessionBusinessProduct()).thenReturn(SubscriptionProduct.MY_WEALTH);

        assertTrue(new SubscriptionEntitlementInterceptor(entitlements)
                .preHandle(request, response, new Object()));

        verify(entitlements).requireFeatureOrAddOn(SubscriptionProduct.MY_WEALTH,
                "WEALTH_INCLUDED_UNITS", SubscriptionProduct.MY_WEALTH);
    }

    @Test void financeAndRiderOperationsDoNotRequireAMerchantSubscription() {
        var interceptor = new SubscriptionEntitlementInterceptor(entitlements);
        for (String path : new String[]{"/soko/finance/refunds", "/soko/order/42/finance",
                "/soko/order/42/finance-hold/return", "/soko/rider/assignments",
                "/soko/rider/kyc", "/soko/order/42/rider/accept"}) {
            when(request.getRequestURI()).thenReturn(path);
            assertTrue(interceptor.preHandle(request, response, new Object()));
        }

        verify(entitlements, never()).requireProduct(SubscriptionProduct.SOKO);
        verify(entitlements, never()).requireSessionBusinessProductIfApplicable();
    }

    @Test void merchantSokoMutationsStillRequireSokoSubscription() {
        when(request.getRequestURI()).thenReturn("/soko/product/42/publish");

        assertTrue(new SubscriptionEntitlementInterceptor(entitlements)
                .preHandle(request, response, new Object()));

        verify(entitlements).requireProduct(SubscriptionProduct.SOKO);
    }

    @Test void serviceMarketplaceMutationsRequireServicesRatherThanSoko() {
        when(request.getRequestURI()).thenReturn("/sp/profile/me");

        assertTrue(new SubscriptionEntitlementInterceptor(entitlements)
                .preHandle(request, response, new Object()));

        verify(entitlements).requireProduct(SubscriptionProduct.SERVICES);
        verify(entitlements, never()).requireProduct(SubscriptionProduct.SOKO);
    }

    @Test void sokoShoppingAndExistingOrderFulfilmentAreSubscriptionNeutral() {
        var interceptor=new SubscriptionEntitlementInterceptor(entitlements);
        for(String path:new String[]{"/soko/catalog","/soko/catalog/sellers","/soko/categories",
                "/soko/delivery-destinations","/soko/order/checkout","/soko/order/my",
                "/soko/order/merchant","/soko/order/42/status","/soko/order/42/delivery/confirm",
                "/soko/order/42/pickup/confirm","/soko/order/42/delivery-code/recovery/request"}){
            when(request.getRequestURI()).thenReturn(path);
            assertTrue(interceptor.preHandle(request,response,new Object()));
        }

        verify(entitlements,never()).requireProduct(SubscriptionProduct.SOKO);
        verify(entitlements,never()).requireProduct(SubscriptionProduct.SERVICES);
        verify(entitlements,never()).requireSessionBusinessProductIfApplicable();
    }
}
