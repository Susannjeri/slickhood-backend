package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.GateDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface GateDeviceRepo extends JpaRepository<GateDevice, Long> {
    @Query("SELECT g FROM GateDevice g JOIN Property p ON p.id=g.propertyId WHERE g.active AND (:privileged=true OR p.createdBy=:userId OR EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=p.id AND pm.userId=:userId AND pm.active)) ORDER BY g.displayName")
    List<GateDevice> findForReport(long userId,boolean privileged,Pageable pageable);
    Optional<GateDevice> findByDeviceCodeAndEnabledTrueAndActiveTrue(String deviceCode);
    Optional<GateDevice> findByDeviceCodeAndActiveTrue(String deviceCode);
    List<GateDevice> findAllByPropertyIdAndActiveTrueOrderByDisplayName(long propertyId);
}
