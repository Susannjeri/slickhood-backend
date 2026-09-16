package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.UserCurrencyPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserCurrencyPreferenceRepo extends JpaRepository<UserCurrencyPreference, Long> {
    Optional<UserCurrencyPreference> findByUserIdAndActiveTrue(Long userId);
}
