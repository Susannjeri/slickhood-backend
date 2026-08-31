package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.database.pms.entities.PropertyAccount;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PropertyAccountRepo extends JpaRepository<PropertyAccount, Long> {
    long countByPropertyIdAndActiveTrue(long propertyId);

    @Query("SELECT pa FROM PropertyAccount pa WHERE pa.accountId=:accountId and pa.propertyId=:propertyId and pa.active")
    Optional<PropertyAccount> findPropertyAccountByIdAndProperty(long accountId, long propertyId);

    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.id=:accountId AND pa.category=:category AND pa.active AND pa.createdBy=:userId")
    Optional<PaymentAccount> findByActiveAndCreatedByAndLandlordCategory(long accountId, long userId, AccountCategory category);
}
