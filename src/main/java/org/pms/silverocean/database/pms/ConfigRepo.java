package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.Config;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfigRepo extends JpaRepository<Config, Long> {
    Optional<Config> findByName(String name);
}
