package org.pms.silverocean.database.audit;

import org.pms.silverocean.database.audit.entities.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepo extends JpaRepository<AuditLog, String>, JpaSpecificationExecutor<AuditLog> {
}
