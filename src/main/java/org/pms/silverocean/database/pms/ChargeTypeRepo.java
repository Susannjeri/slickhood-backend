package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.ChargeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface ChargeTypeRepo extends JpaRepository<ChargeType, Long> {
    @Query("SELECT u.name FROM ChargeType u WHERE u.active")
    Set<String> queryAllChargeTypeNames();

    List<ChargeType> findAllByActiveTrue();
}
