package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PaymentAccountRepo extends JpaRepository<PaymentAccount, Long>, JpaSpecificationExecutor<PaymentAccount> {
    Page<PaymentAccount> findAllByActiveTrue(Pageable pageable);
    Page<PaymentAccount> findByCreatedByAndActiveTrue(Long createdBy, Pageable pageable);
    Optional<PaymentAccount> findByIdAndActiveTrue(Long id);
    Optional<PaymentAccount> findByIdAndActiveTrueAndCreatedBy(long id, long createdBy);

    @Query("SELECT pa FROM PaymentAccount pa JOIN PropertyAccount pa2 on pa.id=pa2.accountId WHERE pa.active AND pa.verified AND (pa.createdBy=:userId OR " +
            " EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=pa2.propertyId AND pm.userId=:userId AND pm.active) OR" +
            " EXISTS (SELECT 1 FROM UnitTenant ut JOIN Unit u ON ut.unitId=u.id WHERE u.propertyId =pa2.propertyId)" +
            ")")
    Page<PaymentAccount> listAccountsByProperty(Pageable pageable, long propertyId, long userId);
    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.category=AccountCategory.SLICKHOOD AND pa.active AND pa.verified")
    Page<PaymentAccount> listSlickHoodAccountByVerifiedTrueAndActive(Pageable pageable);
    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.category=AccountCategory.SLICKHOOD AND pa.active")
    Page<PaymentAccount> listAllActiveSlickHoodAccount(Pageable pageable);
}
