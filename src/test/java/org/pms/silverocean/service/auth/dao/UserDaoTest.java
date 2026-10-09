package org.pms.silverocean.service.auth.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.UserRepo;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.kyc.AccountStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class UserDaoTest {
    @Mock UserRepo userRepo;

    @Test
    void savingBrandNewUserDoesNotUnboxNullGeneratedId() {
        Users user = Users.builder().email("new@example.com").build();
        when(userRepo.save(user)).thenAnswer(invocation -> {
            Users saved = invocation.getArgument(0);
            saved.setId(501L);
            return saved;
        });

        Users saved = assertDoesNotThrow(() -> new UserDao(userRepo).save(user));

        assertEquals(501L, saved.getId());
    }

    @Test
    void publicInvitationChecksHaveNoUserWhenSecurityContextIsEmpty() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        UserDao users = new UserDao(userRepo);

        assertNull(users.getUserId());
        assertNull(users.getUserObject());
    }

    @Test
    void transientRepositoryFailureIsNotCachedAsAUserMiss() {
        Users user = Users.builder().email("recover@example.com").build();
        when(userRepo.findByEmail("recover@example.com"))
                .thenThrow(new IllegalStateException("temporary database timeout"))
                .thenReturn(Optional.of(user));
        UserDao users = new UserDao(userRepo);

        assertThrows(RuntimeException.class, () -> users.findByEmail("recover@example.com"));
        assertEquals(user, users.findByEmail("recover@example.com").orElseThrow());
        verify(userRepo, times(2)).findByEmail("recover@example.com");
    }

    @Test
    void legitimateUserMissRemainsCacheable() {
        when(userRepo.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        UserDao users = new UserDao(userRepo);

        assertEquals(Optional.empty(), users.findByEmail("missing@example.com"));
        assertEquals(Optional.empty(), users.findByEmail("missing@example.com"));
        verify(userRepo).findByEmail("missing@example.com");
    }

    @Test
    void currentSessionTokenAlwaysUsesTheAuthoritativeRepositoryValue() {
        when(userRepo.findRefreshTokenByEmail("owner@example.com"))
                .thenReturn(Optional.of("session-one"), Optional.of("session-two"));
        UserDao users = new UserDao(userRepo);

        assertEquals("session-one", users.findCurrentSessionToken("owner@example.com").orElseThrow());
        assertEquals("session-two", users.findCurrentSessionToken("owner@example.com").orElseThrow());
        verify(userRepo, times(2)).findRefreshTokenByEmail("owner@example.com");
    }

    @Test
    void refreshLookupLocksCurrentOrReplayCredential() throws Exception {
        var method = UserRepo.class.getMethod(
                "findByCurrentOrReplayRefreshTokenForUpdate", String.class);
        var lock = method.getAnnotation(org.springframework.data.jpa.repository.Lock.class);
        var query = method.getAnnotation(org.springframework.data.jpa.repository.Query.class).value();

        assertEquals(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE, lock.value());
        assertTrue(query.contains("u.refreshToken=:refreshToken"));
        assertTrue(query.contains("u.refreshTokenReplayHash=:refreshToken"));
    }

    @Test
    void logoutRevokesBothCurrentAndReplayCredentialPairs() throws Exception {
        var method = UserRepo.class.getMethod("deleteRefreshToken", String.class);
        String query = method.getAnnotation(org.springframework.data.jpa.repository.Query.class).value();

        assertTrue(query.contains("u.refreshToken=null"));
        assertTrue(query.contains("u.refreshTokenRequestHash=null"));
        assertTrue(query.contains("u.refreshTokenReplayHash=null"));
        assertTrue(query.contains("u.refreshTokenReplayRequestHash=null"));
        assertTrue(query.contains("u.refreshTokenReplayExpiresAt=null"));
    }

    @Test
    void accountAccessStateBypassesAnAlreadyCachedActiveUser() {
        Users staleCachedUser = Users.builder().email("owner@example.com")
                .accountStatus(AccountStatus.ACTIVE.name()).build();
        staleCachedUser.setId(51L);
        staleCachedUser.setActive(true);
        when(userRepo.findByEmail("owner@example.com")).thenReturn(Optional.of(staleCachedUser));

        UserRepo.AccountAccessStateRow active = accessRow(51L, true, AccountStatus.ACTIVE.name());
        UserRepo.AccountAccessStateRow revoked = accessRow(51L, false, AccountStatus.KYC_REJECTED.name());
        when(userRepo.findAccountAccessStateByEmail("owner@example.com"))
                .thenReturn(Optional.of(active), Optional.of(revoked));
        UserDao users = new UserDao(userRepo);

        assertEquals(staleCachedUser, users.findByEmail("owner@example.com").orElseThrow());
        assertEquals(AccountStatus.ACTIVE.name(), users.findAuthoritativeAccessState("owner@example.com")
                .orElseThrow().operationalAccountStatus());
        UserDao.AccountAccessState revokedState = users.findAuthoritativeAccessState("owner@example.com").orElseThrow();

        assertEquals(false, revokedState.active());
        assertEquals(AccountStatus.KYC_REJECTED.name(), revokedState.accountStatus());
        assertEquals(AccountStatus.SUSPENDED.name(), revokedState.operationalAccountStatus());
        assertEquals(AccountStatus.ACTIVE.name(), users.findByEmail("owner@example.com")
                .orElseThrow().getAccountStatus());
        verify(userRepo, times(2)).findAccountAccessStateByEmail("owner@example.com");
        verify(userRepo).findByEmail("owner@example.com");
    }

    @Test
    void authoritativeAccessStatePreservesLegacyStatusFallback() {
        UserRepo.AccountAccessStateRow legacy = accessRow(52L, true, " ");
        when(legacy.isVerified()).thenReturn(true);
        when(userRepo.findAccountAccessStateByEmail("legacy@example.com"))
                .thenReturn(Optional.of(legacy));

        UserDao.AccountAccessState state = new UserDao(userRepo)
                .findAuthoritativeAccessState("legacy@example.com").orElseThrow();

        assertEquals(AccountStatus.ACTIVE.name(), state.accountStatus());
    }

    @Test
    void identityConflictReportsOnlyTheFieldsClaimedByAnotherAccount() {
        when(userRepo.countOtherUsersWithIdentificationNumber(51L, "Kenya", "12345678"))
                .thenReturn(1L);
        when(userRepo.countOtherUsersWithTaxPin(51L, "Kenya", "A123456789B"))
                .thenReturn(0L);

        List<String> conflicts = new UserDao(userRepo).conflictingIdentityFields(
                51L, " Kenya ", " 1234 5678 ", "a123456789b");

        assertEquals(List.of("identificationNumber"), conflicts);
    }

    @Test
    void taxPinConflictIsDetectedIndependentlyOfTheNationalId() {
        when(userRepo.countOtherUsersWithIdentificationNumber(51L, "Kenya", "12345678"))
                .thenReturn(0L);
        when(userRepo.countOtherUsersWithTaxPin(51L, "Kenya", "A123456789B"))
                .thenReturn(1L);

        List<String> conflicts = new UserDao(userRepo).conflictingIdentityFields(
                51L, "Kenya", "12345678", "A123456789B");

        assertEquals(List.of("taxPin"), conflicts);
    }

    @Test
    void missingOptionalTaxPinDoesNotRunATaxConflictLookup() {
        when(userRepo.countOtherUsersWithIdentificationNumber(51L, "Kenya", "P1234567"))
                .thenReturn(0L);

        assertTrue(new UserDao(userRepo).isValidIDAndTaxPin(51L, "Kenya", "P1234567", null));

        verify(userRepo, never()).countOtherUsersWithTaxPin(51L, "Kenya", null);
    }

    @Test
    void missingCountryFailsClosedInsteadOfSkippingIdentityConflictChecks() {
        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> new UserDao(userRepo).conflictingIdentityFields(
                        51L, " ", "12345678", "A123456789B"));

        assertEquals(ResponseCode.INCOMPLETE_USER_PROFILE, error.getResponseCode());
    }

    @Test
    void missingCountryDoesNotBlockAProfileWithNoIdentityValues() {
        assertTrue(new UserDao(userRepo).conflictingIdentityFields(
                51L, null, null, " ").isEmpty());
    }

    @Test
    void identityConflictQueriesExcludeTheSubjectAndRemainCountryScoped() throws Exception {
        String identityQuery = UserRepo.class
                .getMethod("countOtherUsersWithIdentificationNumber", long.class, String.class, String.class)
                .getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
        String taxQuery = UserRepo.class
                .getMethod("countOtherUsersWithTaxPin", long.class, String.class, String.class)
                .getAnnotation(org.springframework.data.jpa.repository.Query.class).value();

        assertTrue(identityQuery.contains("u.id<>:userId"));
        assertTrue(identityQuery.contains("UPPER(u.country)=UPPER(:country)"));
        assertTrue(identityQuery.contains("FUNCTION('REPLACE', UPPER(u.identificationNumber), ' ', '')"));
        assertTrue(taxQuery.contains("u.id<>:userId"));
        assertTrue(taxQuery.contains("UPPER(u.country)=UPPER(:country)"));
        assertTrue(taxQuery.contains("FUNCTION('REPLACE', UPPER(u.taxPin), ' ', '')"));
    }

    private UserRepo.AccountAccessStateRow accessRow(long id, boolean active, String accountStatus) {
        UserRepo.AccountAccessStateRow row = mock(UserRepo.AccountAccessStateRow.class);
        when(row.getUserId()).thenReturn(id);
        when(row.isActive()).thenReturn(active);
        when(row.getAccountStatus()).thenReturn(accountStatus);
        return row;
    }
}
