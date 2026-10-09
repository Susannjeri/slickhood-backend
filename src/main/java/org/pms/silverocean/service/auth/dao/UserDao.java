package org.pms.silverocean.service.auth.dao;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.UserRepo;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.kyc.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.util.Locale;

import static org.pms.silverocean.service.users.UserSpecifications.searchUsers;

@Service
public class UserDao {

    private final LoadingCache<Object, Optional<Users>> userCache = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(Duration.ofMinutes(7)).build(new CacheLoader<>() {
                @Override
                public Optional<Users> load(Object key) {
                    // A legitimate repository miss may be cached, but a database
                    // timeout or connection failure must propagate. Converting an
                    // infrastructure error into Optional.empty() poisons the cache
                    // with "user not found" for seven minutes and makes every
                    // authenticated recovery request fail until that entry expires.
                    return key instanceof String ? loadUserByEmail((String) key) : loadUserById((long) key);
                }
            });

    private final UserRepo userRepo;

    public UserDao(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    private Optional<Users> loadUserByEmail(String email) {
        return userRepo.findByEmail(email);
    }

    private Optional<Users> loadUserById(long id) {
        return userRepo.findById(id);
    }

    public Optional<Users> findByEmail(String email) {
        return userCache.getUnchecked(email);
    }

    public Optional<Users> findByRefreshToken(String refreshToken) {
        return userRepo.findFirstByRefreshToken(refreshToken);
    }

    public Optional<Users> findByCurrentOrReplayRefreshTokenForUpdate(String refreshToken) {
        return userRepo.findByCurrentOrReplayRefreshTokenForUpdate(refreshToken);
    }

    /**
     * Session binding is security-sensitive mutable state and must not come
     * from the local user cache. In a multi-instance deployment another node
     * may have rotated the refresh token, while this node still has an older
     * Users entity cached.
     */
    public Optional<String> findCurrentSessionToken(String email) {
        return userRepo.findRefreshTokenByEmail(email);
    }

    /**
     * An uncached snapshot used only for authorization and onboarding routing.
     * The active flag deliberately remains separate from accountStatus because
     * legacy rows may still say ACTIVE after a technical account deactivation.
     */
    public record AccountAccessState(long userId, boolean active, String accountStatus) {
        public String operationalAccountStatus() {
            return active ? accountStatus : AccountStatus.SUSPENDED.name();
        }
    }

    public Optional<AccountAccessState> findAuthoritativeAccessState(String email) {
        if (StringUtils.isBlank(email)) return Optional.empty();
        return userRepo.findAccountAccessStateByEmail(email).map(row -> new AccountAccessState(
                row.getUserId(), row.isActive(), effectiveAccountStatus(row)));
    }

    public Optional<AccountAccessState> getCurrentAccessState() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) return Optional.empty();
        return findAuthoritativeAccessState(authentication.getPrincipal().toString());
    }

    private String effectiveAccountStatus(UserRepo.AccountAccessStateRow row) {
        if (StringUtils.isNotBlank(row.getAccountStatus())) return row.getAccountStatus();
        if (row.isVerified()) return AccountStatus.ACTIVE.name();
        return row.isEmailVerified() ? AccountStatus.PENDING_KYC.name()
                : AccountStatus.PENDING_EMAIL_VERIFICATION.name();
    }

    public Optional<Users> findByPhone(String phoneNumber) {
        Optional<Users> unSanitizedSearch = userRepo.findFirstByPhoneNumber(phoneNumber);
        if (unSanitizedSearch.isPresent()) {
            return unSanitizedSearch;
        }
        return userRepo.findFirstByPhoneNumber(phoneNumber.replaceAll("\\+", ""));
    }

    public Optional<Users> findById(long id) {
        return userCache.getUnchecked(id);
    }

    public List<Users> findAllById(Iterable<Long> ids) { return userRepo.findAllById(ids); }

    public Users save(Users user) {
        userCache.invalidate(user.getEmail());
        // A new JPA entity has no generated id until after the first save.
        // Never unbox that nullable id while preparing the cache invalidation.
        if (user.getId() != null && user.getId() != 0L) userCache.invalidate(user.getId());
        return userRepo.save(user);
    }

    public Long getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) return null;
        String username = authentication.getPrincipal().toString();
        return findByEmail(username).map(Users::getId).orElse(null);
    }

    public String getEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
    }

    public Users getUserObject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) return null;
        String username = authentication.getPrincipal().toString();
        if (StringUtils.isBlank(username)) {
            return null;
        }
        return findByEmail(username).orElse(null);
    }

    public boolean isValidIDAndTaxPin(long userId, String country, String nationalId, String taxPin) {
        return conflictingIdentityFields(userId, country, nationalId, taxPin).isEmpty();
    }

    /**
     * Returns only the names of identity fields already claimed by another account.
     * The conflicting account is deliberately not exposed to callers.
     */
    public List<String> conflictingIdentityFields(long userId, String country, String nationalId, String taxPin) {
        String normalizedNationalId = normalizeIdentityValue(nationalId);
        String normalizedTaxPin = normalizeIdentityValue(taxPin);
        if (normalizedNationalId == null && normalizedTaxPin == null) return List.of();

        String normalizedCountry = StringUtils.trimToNull(country);
        if (normalizedCountry == null) {
            throw new PMSCustomException(ResponseCode.INCOMPLETE_USER_PROFILE);
        }

        List<String> conflicts = new ArrayList<>(2);
        if (normalizedNationalId != null
                && userRepo.countOtherUsersWithIdentificationNumber(userId, normalizedCountry, normalizedNationalId) > 0) {
            conflicts.add("identificationNumber");
        }
        if (normalizedTaxPin != null
                && userRepo.countOtherUsersWithTaxPin(userId, normalizedCountry, normalizedTaxPin) > 0) {
            conflicts.add("taxPin");
        }
        return List.copyOf(conflicts);
    }

    public String normalizeIdentityValue(String value) {
        if (StringUtils.isBlank(value)) return null;
        return value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    public Page<Users> searchAllUsers(Pageable pageable, Optional<String> searchParam) {
       return userRepo.findAll(searchUsers(searchParam), pageable);
    }

    public boolean hasRole(PMSRole role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role.getName().toUpperCase()));
    }

    public PMSRole getActiveRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new PMSCustomException(ResponseCode.INVALID_ROLE);
        }
        return java.util.Arrays.stream(PMSRole.values())
                .filter(role -> auth.getAuthorities().stream().anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_" + role.getName().toUpperCase())))
                .findFirst()
                .orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_ROLE));
    }

    public boolean hasPermission(String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(permission));
    }

    @Transactional
    public void logoutUserByUserId() {
        String email = getEmail();
        userCache.invalidate(email);
        userRepo.deleteRefreshToken(email);
    }

    public Set<Users> findActiveSuperAdminAccounts() {
       return userRepo.findSuperAdminAccounts();
    }
}
