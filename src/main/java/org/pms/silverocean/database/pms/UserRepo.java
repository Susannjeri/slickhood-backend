package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @Modifying
    @Query("UPDATE Users  u SET u.refreshToken=null WHERE u.email=:username")
    void deleteRefreshToken(@Param("username") String username);

    @Query("SELECT u.id FROM Users u WHERE u.country=:country AND u.id<>:userId AND (u.taxPin=:taxPin OR u.identificationNumber=:nationalId)")
    List<Long> findFirstByUserIdCountryNationalIdAndTaxPin(long userId, String country, String nationalId, String taxPin);

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
        SELECT (SUM(CASE WHEN u.active THEN 1.0 ELSE 0.0 END) * 100.0) / COUNT(u)
        FROM Users u
    """)
    double getActiveUserPercentage();

    @Query("SELECT COUNT(u) FROM Users u WHERE u.lastLogin >= :start AND u.lastLogin < :end")
    int countUsersLoggedInCurrentMonth(@Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);
}
