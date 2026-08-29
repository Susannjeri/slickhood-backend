package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.LoginPerIPLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Date;

public interface LoginperiplogsRepo extends JpaRepository<LoginPerIPLog, Long> {
    @Modifying
    @Query("DELETE FROM LoginPerIPLog login where login.username=:username")
    void deleteOnSuccessfulLogin(@Param("username") String username);

    Optional<LoginPerIPLog> findByUsernameAndIpaddress(String username, String ipaddress);

    @Query("SELECT COALESCE(SUM(login.attemptsCount), 0) FROM LoginPerIPLog login WHERE login.ipaddress = :ipaddress AND login.lastLoginAttempt >= :cutoff")
    int countRecentFailedLoginsByIP(@Param("ipaddress") String ipaddress, @Param("cutoff") Date cutoff);


    @Query("SELECT COALESCE(SUM(login.attemptsCount), 0) FROM LoginPerIPLog login WHERE login.username = :username")
    int countFailedLoginByUsername(@Param("username") String username);
}
