package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.GateRequestNonce;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GateRequestNonceRepo extends JpaRepository<GateRequestNonce, Long> {}
