package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.PaymentAccountProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PaymentAccountPropertyRepo extends JpaRepository<PaymentAccountProperty, Long> {
    List<PaymentAccountProperty> findByAccountId(long accountId);

    @Query("SELECT pap FROM PaymentAccountProperty pap JOIN PaymentAccount pa on pap.accountId=pa.id JOIN PropertyAccount p ON p.accountId=pa.id" +
            " WHERE p.active AND pa.active AND pa.verified AND p.propertyId=:propertyId AND pap.accountId=:accountId AND pap.propertyKey=:propertyKey")
    Optional<PaymentAccountProperty> findByAccountIdAndPropertyKeyAndPropertyId(long accountId, String propertyKey, long propertyId);

    Optional<PaymentAccountProperty> findByAccountIdAndPropertyKey(long accountId, String propertyKey);
}
