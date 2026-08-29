package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.UserOTP;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserOTPRepo extends JpaRepository<UserOTP, Long> {
    @Modifying
    @Query("UPDATE UserOTP uo SET uo.active=false, uo.lastModifiedDate=current_timestamp WHERE uo.active AND (uo.createdBy=:createdBy OR uo.contact=:contact)")
    void deactivateByCreatedByOrContactAndActiveTrue(Long createdBy, String contact);

    Optional<UserOTP> findByCreatedByAndActiveTrue(Long createdBy);

    Optional<UserOTP> findFirstByCreatedByAndActiveFalseAndVerifiedTrueOrderByIdDesc(Long createdBy);

}
