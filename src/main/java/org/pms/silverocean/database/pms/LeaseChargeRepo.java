package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.LeaseCharge;
import org.pms.silverocean.service.property.wrappers.UnitChargeProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface LeaseChargeRepo extends JpaRepository<LeaseCharge, Long> {
    @Query("SELECT uc.id as id, uc.createdOn as createdOn, uc.chargeId as chargeId, ct.name as chargeName, uc.amount as amount, uc.period as periodId, uc.nextPaymentDate as nextPaymentDate FROM LeaseCharge uc JOIN ChargeType ct ON uc.chargeId=ct.id WHERE uc.leaseId=:leaseId")
    List<UnitChargeProjection> findByLeaseId(Long leaseId);

    @Modifying
    @Query("UPDATE LeaseCharge lc SET lc.nextPaymentDate=:nextPaymentDate WHERE lc.id=:leaseChargeId")
    void updateNextPaymentDate(long leaseChargeId, LocalDate nextPaymentDate);

    @Modifying
    @Query("UPDATE LeaseCharge lc SET lc.nextPaymentDate=current_date WHERE lc.leaseId=:leaseId")
    void updateSignedLeaseChargeNextPaymentDate(long leaseId);
}
