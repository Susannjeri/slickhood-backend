package org.pms.silverocean.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleCorsFilterTest {
    @Test
    void guestInsuranceAllowsOnlyTrustedWebsitesAndGuestMethods() throws Exception {
        var source = configured("https://app.slickhood.com", true).corsConfigurationSource();
        var request = new org.springframework.mock.web.MockHttpServletRequest("OPTIONS", "/public/insurance/access/request");
        request.addHeader("Origin", "https://slickhood.com");
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "content-type,x-insurance-access");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var policy = source.getCorsConfiguration(request);
        assertThat(new org.springframework.web.cors.DefaultCorsProcessor().processRequest(policy, request, response)).isTrue();
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://slickhood.com");
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isNull();
        assertThat(policy.checkOrigin("https://www.slickhood.com")).isEqualTo("https://www.slickhood.com");
        assertThat(policy.checkOrigin("https://untrusted.invalid")).isNull();
        assertThat(policy.checkOrigin("https://slickhood.com.attacker.invalid")).isNull();
        assertThat(policy.checkHttpMethod(org.springframework.http.HttpMethod.DELETE)).isNull();
        var protectedPolicy = source.getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest("POST", "/auth/login"));
        assertThat(protectedPolicy.checkOrigin("https://slickhood.com")).isNull();
    }

    @Test
    void publicWebsiteCanSubmitPropertyEnquiriesWithoutOpeningAuthenticatedApis() throws Exception {
        var source = configured("https://app.slickhood.com", true).corsConfigurationSource();
        var request = new org.springframework.mock.web.MockHttpServletRequest("OPTIONS", "/public/property-listings/sample/inquiries");
        request.addHeader("Origin", "https://www.slickhood.com");
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "content-type");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var policy = source.getCorsConfiguration(request);
        assertThat(new org.springframework.web.cors.DefaultCorsProcessor().processRequest(policy, request, response)).isTrue();
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://www.slickhood.com");
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isNull();
        assertThat(policy.checkHttpMethod(org.springframework.http.HttpMethod.DELETE)).isNull();
        assertThat(source.getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest("GET", "/user/details"))
                .checkOrigin("https://www.slickhood.com")).isNull();
    }

    @Test
    void publicWebsiteCanUseGuestHelpWithoutOpeningAuthenticatedHelpDesk() throws Exception {
        var source = configured("https://app.slickhood.com", true).corsConfigurationSource();
        var request = new org.springframework.mock.web.MockHttpServletRequest("OPTIONS", "/helpdesk/public/conversations/SH-TEST/messages");
        request.addHeader("Origin", "https://slickhood.com");
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "content-type,x-help-token");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var policy = source.getCorsConfiguration(request);
        assertThat(new org.springframework.web.cors.DefaultCorsProcessor().processRequest(policy, request, response)).isTrue();
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://slickhood.com");
        assertThat(response.getHeader("Access-Control-Allow-Headers")).containsIgnoringCase("x-help-token");
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isNull();
        assertThat(source.getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest("POST", "/helpdesk/admin/articles"))
                .checkOrigin("https://slickhood.com")).isNull();
    }

    @Test
    void productionRejectsHttpOrigins() {
        SimpleCorsFilter filter = configured("http://app.slickhood.com", true);
        assertThatThrownBy(filter::corsConfigurationSource)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void productionAcceptsHttpsOrigins() {
        SimpleCorsFilter filter = configured("https://app.slickhood.com", true);
        assertThatCode(filter::corsConfigurationSource).doesNotThrowAnyException();
    }

    @Test
    void localDevelopmentCanExplicitlyAllowHttp() {
        SimpleCorsFilter filter = configured("http://localhost:3000", false);
        assertThatCode(filter::corsConfigurationSource).doesNotThrowAnyException();
    }

    @Test
    void browserCanReadDownloadAndReportSafetyHeaders() {
        SimpleCorsFilter filter = configured("https://app.slickhood.com", true);
        var source = (UrlBasedCorsConfigurationSource) filter.corsConfigurationSource();
        assertThat(source.getCorsConfigurations().get("/**").getExposedHeaders())
                .contains("Content-Disposition", "X-Report-Truncated", "X-Report-Row-Limit");
    }

    private SimpleCorsFilter configured(String origin, boolean requireHttps) {
        SimpleCorsFilter filter = new SimpleCorsFilter();
        ReflectionTestUtils.setField(filter, "allowedOrigins", new String[]{origin});
        ReflectionTestUtils.setField(filter, "allowedMethods", new String[]{"GET", "OPTIONS"});
        ReflectionTestUtils.setField(filter, "allowedHeaders", new String[]{"Authorization", "Content-Type"});
        ReflectionTestUtils.setField(filter, "exposedHeaders",
                new String[]{"Content-Disposition", "X-Report-Truncated", "X-Report-Row-Limit"});
        ReflectionTestUtils.setField(filter, "requireHttps", requireHttps);
        return filter;
    }
}
