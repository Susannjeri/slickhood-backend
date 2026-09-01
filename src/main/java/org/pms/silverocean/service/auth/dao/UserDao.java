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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.List;

import static org.pms.silverocean.service.users.UserSpecifications.searchUsers;

@Service
public class UserDao {

    private final LoadingCache<Object, Optional<Users>> userCache = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(Duration.ofMinutes(7)).build(new CacheLoader<>() {
                @Override
                public Optional<Users> load(Object key) {
                    try {
                        return key instanceof String ? loadUserByEmail((String) key) : loadUserById((long) key);
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                }
            });

    private final UserRepo userRepo;

    public UserDao(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    private Optional<Users> loadUserByEmail(String email) throws Exception {
        return userRepo.findByEmail(email);
    }

    private Optional<Users> loadUserById(long id) throws Exception {
        return userRepo.findById(id);
    }

    public Optional<Users> findByEmail(String email) {
        return userCache.getUnchecked(email);
    }

    public Optional<Users> findByRefreshToken(String refreshToken) {
        return userRepo.findFirstByRefreshToken(refreshToken);
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
        String username = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        return findByEmail(username).map(Users::getId).orElse(null);
    }

    public String getEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
    }

    public Users getUserObject() {
        String username = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        if (StringUtils.isBlank(username)) {
            return null;
        }
        return findByEmail(username).orElse(null);
    }

    public boolean isValidIDAndTaxPin(long userId, String country, String nationalId, String taxPin) {
        return userRepo.findFirstByUserIdCountryNationalIdAndTaxPin(userId, country, nationalId, taxPin).isEmpty();
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
