package org.pms.silverocean.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.kyc.AccountStatus;
import org.pms.silverocean.database.pms.WorkspaceMembershipRepo;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.database.pms.entities.WorkspaceMembership;
import org.pms.silverocean.service.teamaccess.TeamMembershipRole;
import org.pms.silverocean.service.teamaccess.TeamMembershipStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Optional;

class AccountActivationFilterTest {
    private UserDao users;
    private WorkspaceMembershipRepo memberships;
    private AccountActivationFilter filter;

    @BeforeEach void setUp() {
        users = mock(UserDao.class);
        memberships = mock(WorkspaceMembershipRepo.class);
        filter = new AccountActivationFilter(users, mock(I18NService.class), new ObjectMapper(), memberships);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("customer@example.com", null, java.util.List.of()));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void pendingCustomerCannotCallOperationalApi() throws Exception {
        pendingCustomer(PMSRole.LANDLORD);
        MockHttpServletResponse response = run("/property/list");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("S00333").contains("PENDING_KYC");
    }

    @Test void pendingCustomerCanCompleteKyc() throws Exception {
        pendingCustomer(PMSRole.LANDLORD);
        assertThat(run("/kyc/current").getStatus()).isEqualTo(200);
    }

    @Test void accountStateNeverBlocksAssignmentScopedPublicRiderBearer() throws Exception {
        pendingCustomer(PMSRole.LANDLORD);
        assertThat(run("/soko/public/rider-assignment/accept").getStatus()).isEqualTo(200);
        verify(users, never()).getCurrentAccessState();
    }

    @Test void internalReviewerIsNotSentThroughCustomerKyc() throws Exception {
        pendingCustomer(PMSRole.SUPER_ADMIN);
        assertThat(run("/kyc/admin/queue").getStatus()).isEqualTo(200);
    }

    @Test void approvedCustomerCanCallOperationalApi() throws Exception {
        accessState(10L, true, AccountStatus.ACTIVE);
        when(users.getActiveRole()).thenReturn(PMSRole.LANDLORD);
        assertThat(run("/property/list").getStatus()).isEqualTo(200);
    }

    @Test void suspendedWorkspaceMemberCannotUseStaleToken() throws Exception {
        accessState(11L, true, AccountStatus.ACTIVE);
        when(users.getActiveRole()).thenReturn(PMSRole.GUARD);
        when(memberships.findFirstByUserIdAndMembershipRoleAndStatusAndActiveTrue(
                11L, TeamMembershipRole.GUARD, TeamMembershipStatus.ACTIVE)).thenReturn(Optional.empty());
        assertThat(run("/visitor/list").getStatus()).isEqualTo(403);
    }

    @Test void activeWorkspaceMemberCanUseScopedApis() throws Exception {
        accessState(12L, true, AccountStatus.ACTIVE);
        when(users.getActiveRole()).thenReturn(PMSRole.GUARD);
        when(memberships.findFirstByUserIdAndMembershipRoleAndStatusAndActiveTrue(
                12L, TeamMembershipRole.GUARD, TeamMembershipStatus.ACTIVE)).thenReturn(Optional.of(new WorkspaceMembership()));
        assertThat(run("/visitor/list").getStatus()).isEqualTo(200);
    }

    @Test void cachedActiveUserIsDeniedImmediatelyAfterAuthoritativeDeactivation() throws Exception {
        Users cached = new Users(); cached.setId(13L); cached.setActive(true);
        cached.setAccountStatus(AccountStatus.ACTIVE.name());
        when(users.getUserObject()).thenReturn(cached);
        accessState(13L, false, AccountStatus.ACTIVE);
        when(users.getActiveRole()).thenReturn(PMSRole.LANDLORD);

        MockHttpServletResponse response = run("/property/list");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("S00333").contains("SUSPENDED");
        verify(users, never()).getUserObject();
    }

    @Test void cachedActiveUserIsDeniedImmediatelyAfterAuthoritativeKycRejection() throws Exception {
        Users cached = new Users(); cached.setId(14L); cached.setActive(true);
        cached.setAccountStatus(AccountStatus.ACTIVE.name());
        when(users.getUserObject()).thenReturn(cached);
        accessState(14L, true, AccountStatus.KYC_REJECTED);
        when(users.getActiveRole()).thenReturn(PMSRole.LANDLORD);

        MockHttpServletResponse response = run("/property/list");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("S00333").contains("KYC_REJECTED");
        verify(users, never()).getUserObject();
    }

    @Test void authoritativeDeactivationCannotBeBypassedByAnInternalRole() throws Exception {
        accessState(15L, false, AccountStatus.ACTIVE);
        when(users.getActiveRole()).thenReturn(PMSRole.SUPER_ADMIN);

        MockHttpServletResponse response = run("/property/list");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("SUSPENDED");
    }

    @Test void authoritativeAccessStateOutageIsRetryableAndFailsClosed() throws Exception {
        when(users.getCurrentAccessState()).thenThrow(
                new DataAccessResourceFailureException("temporary database outage"));

        MockHttpServletResponse response = run("/property/list");

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("3");
        assertThat(response.getContentAsString()).contains("S0039");
    }

    private void pendingCustomer(PMSRole role) {
        accessState(9L, true, AccountStatus.PENDING_KYC);
        when(users.getActiveRole()).thenReturn(role);
    }

    private void accessState(long userId, boolean active, AccountStatus status) {
        when(users.getCurrentAccessState()).thenReturn(Optional.of(
                new UserDao.AccountAccessState(userId, active, status.name())));
    }

    private MockHttpServletResponse run(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
