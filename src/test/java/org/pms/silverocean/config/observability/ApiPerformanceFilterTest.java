package org.pms.silverocean.config.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.config.JWTFilter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

class ApiPerformanceFilterTest {
    @Test
    void recordsTemplatedRouteWithoutConcreteIdentifiers() throws Exception {
        var registry = new SimpleMeterRegistry();
        var filter = new ApiPerformanceFilter(registry, Long.MAX_VALUE);
        var request = new MockHttpServletRequest("GET", "/property/unit/572");
        request.setAttribute(JWTFilter.ACTIVE_ROLE_ATTRIBUTE, "LANDLORD");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/property/unit/{unitId}"));

        var timer = registry.find(ApiPerformanceFilter.DURATION_METRIC)
                .tags("method", "GET", "route", "/property/unit/{unitId}", "status", "200", "role", "LANDLORD")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(registry.getMeters().toString()).doesNotContain("572");
    }

    @Test
    void countsSlowRequestsAndUsesAnonymousForPublicRoutes() throws Exception {
        var registry = new SimpleMeterRegistry();
        var filter = new ApiPerformanceFilter(registry, 0);
        var request = new MockHttpServletRequest("POST", "/auth/login");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/auth/login"));

        assertThat(registry.find(ApiPerformanceFilter.SLOW_METRIC)
                .tags("method", "POST", "route", "/auth/login", "status", "200", "role", "ANONYMOUS")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void unmatchedRoutesNeverExposeRawPathValues() throws Exception {
        var registry = new SimpleMeterRegistry();
        var filter = new ApiPerformanceFilter(registry, Long.MAX_VALUE);
        var request = new MockHttpServletRequest("GET", "/invite/private-token-value");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertThat(registry.find(ApiPerformanceFilter.DURATION_METRIC).tag("route", "UNMATCHED").timer()).isNotNull();
        assertThat(registry.getMeters().toString()).doesNotContain("private-token-value");
    }

    @Test
    void actuatorRequestsAreExcluded() throws Exception {
        var registry = new SimpleMeterRegistry();
        var filter = new ApiPerformanceFilter(registry, 0);

        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"),
                new MockHttpServletResponse(), (req, res) -> { });

        assertThat(registry.getMeters()).isEmpty();
    }
}
