package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.database.pms.entities.PropertyAccount;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PropertyAccountRepo extends JpaRepository<PropertyAccount, Long> {
    long countByPropertyIdAndActiveTrue(long propertyId);

    @Query("SELECT COUNT(pa) FROM PropertyAccount pa JOIN PaymentAccount a ON a.id=pa.accountId " +
            "JOIN Property p ON p.id=pa.propertyId WHERE pa.propertyId=:propertyId AND pa.active " +
            "AND a.active AND a.verified AND a.category=:category AND a.createdBy=p.createdBy")
    long countVerifiedOperatingAccounts(long propertyId, AccountCategory category);

    @Query("SELECT pa FROM PropertyAccount pa WHERE pa.accountId=:accountId and pa.propertyId=:propertyId and pa.active")
    Optional<PropertyAccount> findPropertyAccountByIdAndProperty(long accountId, long propertyId);

    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.id=:accountId AND pa.category=:category AND pa.active AND pa.createdBy=:userId")
    Optional<PaymentAccount> findByActiveAndCreatedByAndCategory(long accountId, long userId, AccountCategory category);

    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.id=:accountId AND pa.active AND pa.createdBy=:userId")
    Optional<PaymentAccount> findActiveOwnedAccount(long accountId, long userId);
}
