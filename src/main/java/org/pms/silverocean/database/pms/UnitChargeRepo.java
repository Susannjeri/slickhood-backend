package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.UnitCharge;
import org.pms.silverocean.service.property.wrappers.UnitChargeProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UnitChargeRepo extends JpaRepository<UnitCharge, Long> {
    @Modifying
    void deleteByUnitId(Long unitId);

    @Query("SELECT uc.id as id, uc.createdOn as createdOn, uc.chargeId as chargeId, ct.name as chargeName, uc.amount as amount, uc.period as periodId FROM UnitCharge uc JOIN ChargeType ct ON uc.chargeId=ct.id WHERE uc.unitId=:unitId")
    List<UnitChargeProjection> findByUnitId(Long unitId);

    List<UnitCharge> findAllByUnitId(long unitId);

}
