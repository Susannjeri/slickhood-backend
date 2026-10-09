package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.persistence.LockModeType;

public interface UserRepo extends JpaRepository<Users, Long>, JpaSpecificationExecutor<Users> {
    Optional<Users> findByEmail(String email);

    @Modifying
    @Query("UPDATE Users u SET u.active=false WHERE u.email=:username")
    void deactivateAccount(@Param("username") String username);

    @Modifying
    @Query("UPDATE Users u SET u.lastLogin=CURRENT_TIMESTAMP WHERE u.email=:username")
    void updateLastLogin(String username);

    Optional<Users> findFirstByPhoneNumber(String phoneNumber);
    Optional<Users> findFirstByRefreshToken(String refreshToken);

    @Query("SELECT u.refreshToken FROM Users u WHERE u.email=:email AND u.active=true")
    Optional<String> findRefreshTokenByEmail(@Param("email") String email);

    interface AccountAccessStateRow {
        Long getUserId();
        boolean isActive();
        String getAccountStatus();
        boolean isVerified();
        boolean isEmailVerified();
    }

    /**
     * Security-sensitive account state must remain an uncached, narrow read.
     * A full Users entity may be stale in a node-local cache after another
     * application instance suspends the account or changes its KYC state.
     */
    @Query("""
        SELECT u.id AS userId, u.active AS active, u.accountStatus AS accountStatus,
               u.verified AS verified, u.emailVerified AS emailVerified
        FROM Users u
        WHERE u.email=:email
    """)
    Optional<AccountAccessStateRow> findAccountAccessStateByEmail(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Users u WHERE u.refreshToken=:refreshToken OR u.refreshTokenReplayHash=:refreshToken")
    Optional<Users> findByCurrentOrReplayRefreshTokenForUpdate(@Param("refreshToken") String refreshToken);

    @Modifying
    @Query("""
        UPDATE Users u SET u.refreshToken=null, u.refreshTokenRequestHash=null,
            u.refreshTokenReplayHash=null, u.refreshTokenReplayRequestHash=null,
            u.refreshTokenReplayExpiresAt=null
        WHERE u.email=:username
    """)
    void deleteRefreshToken(@Param("username") String username);

    @Query("""
        SELECT COUNT(u) FROM Users u
        WHERE u.id<>:userId AND UPPER(u.country)=UPPER(:country)
          AND FUNCTION('REPLACE', UPPER(u.identificationNumber), ' ', '')=UPPER(:nationalId)
    """)
    long countOtherUsersWithIdentificationNumber(@Param("userId") long userId,
                                                   @Param("country") String country,
                                                   @Param("nationalId") String nationalId);

    @Query("""
        SELECT COUNT(u) FROM Users u
        WHERE u.id<>:userId AND UPPER(u.country)=UPPER(:country)
          AND FUNCTION('REPLACE', UPPER(u.taxPin), ' ', '')=UPPER(:taxPin)
    """)
    long countOtherUsersWithTaxPin(@Param("userId") long userId,
                                    @Param("country") String country,
                                    @Param("taxPin") String taxPin);

    @Query("SELECT u FROM Users u JOIN UserRole ur ON u.id=ur.userId JOIN Role r ON ur.roleId=r.id WHERE r.name='Superadmin' AND r.active AND u.active")
    Set<Users> findSuperAdminAccounts();

    interface InsuranceStaffRow {
        Long getId();
        String getFullName();
        String getEmail();
        String getRoleName();
    }

    @Query("""
        SELECT u.id AS id, u.fullName AS fullName, u.email AS email, r.name AS roleName
        FROM Users u JOIN UserRole ur ON u.id=ur.userId JOIN Role r ON ur.roleId=r.id
        WHERE r.name IN :roleNames AND r.active=true AND u.active=true
        ORDER BY u.fullName, u.email
    """)
    List<InsuranceStaffRow> findActiveInsuranceStaff(@Param("roleNames") Set<String> roleNames);

    @Query("""
        SELECT COUNT(u) FROM Users u JOIN UserRole ur ON u.id=ur.userId JOIN Role r ON ur.roleId=r.id
        WHERE u.id=:userId AND r.name IN :roleNames AND r.active=true AND u.active=true
    """)
    long countActiveInsuranceStaff(@Param("userId") long userId, @Param("roleNames") Set<String> roleNames);

    @Query("""
        SELECT COALESCE((SUM(CASE WHEN u.active THEN 1.0 ELSE 0.0 END) * 100.0) / NULLIF(COUNT(u), 0), 0.0)
        FROM Users u
    """)
    double getActiveUserPercentage();

    @Query("SELECT COUNT(u) FROM Users u WHERE u.lastLogin >= :start AND u.lastLogin < :end")
    int countUsersLoggedInCurrentMonth(@Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);
}
