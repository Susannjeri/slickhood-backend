package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.ConversionRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversionRateRepo extends JpaRepository<ConversionRate, Long> {
    Optional<ConversionRate> findByCurrency(String currency);
}
