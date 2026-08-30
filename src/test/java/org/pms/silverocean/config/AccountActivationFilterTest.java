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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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

    @Test void internalReviewerIsNotSentThroughCustomerKyc() throws Exception {
        pendingCustomer(PMSRole.SUPER_ADMIN);
        assertThat(run("/kyc/admin/queue").getStatus()).isEqualTo(200);
    }

    @Test void approvedCustomerCanCallOperationalApi() throws Exception {
        Users user = new Users(); user.setId(10L); user.setAccountStatus(AccountStatus.ACTIVE.name());
        when(users.getUserObject()).thenReturn(user); when(users.getActiveRole()).thenReturn(PMSRole.LANDLORD);
        assertThat(run("/property/list").getStatus()).isEqualTo(200);
    }

    @Test void suspendedWorkspaceMemberCannotUseStaleToken() throws Exception {
        Users user = new Users(); user.setId(11L); user.setAccountStatus(AccountStatus.ACTIVE.name());
        when(users.getUserObject()).thenReturn(user); when(users.getActiveRole()).thenReturn(PMSRole.GUARD);
        when(memberships.findFirstByUserIdAndMembershipRoleAndStatusAndActiveTrue(
                11L, TeamMembershipRole.GUARD, TeamMembershipStatus.ACTIVE)).thenReturn(Optional.empty());
        assertThat(run("/visitor/list").getStatus()).isEqualTo(403);
    }

    @Test void activeWorkspaceMemberCanUseScopedApis() throws Exception {
        Users user = new Users(); user.setId(12L); user.setAccountStatus(AccountStatus.ACTIVE.name());
        when(users.getUserObject()).thenReturn(user); when(users.getActiveRole()).thenReturn(PMSRole.GUARD);
        when(memberships.findFirstByUserIdAndMembershipRoleAndStatusAndActiveTrue(
                12L, TeamMembershipRole.GUARD, TeamMembershipStatus.ACTIVE)).thenReturn(Optional.of(new WorkspaceMembership()));
        assertThat(run("/visitor/list").getStatus()).isEqualTo(200);
    }

    private void pendingCustomer(PMSRole role) {
        Users user = new Users(); user.setId(9L); user.setAccountStatus(AccountStatus.PENDING_KYC.name());
        when(users.getUserObject()).thenReturn(user); when(users.getActiveRole()).thenReturn(role);
    }

    private MockHttpServletResponse run(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
