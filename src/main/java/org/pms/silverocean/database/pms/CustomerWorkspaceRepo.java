package org.pms.silverocean.database.pms;
import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.CustomerWorkspace;
import org.pms.silverocean.service.teamaccess.TeamBusinessArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface CustomerWorkspaceRepo extends JpaRepository<CustomerWorkspace,Long> {
    Optional<CustomerWorkspace> findByOwnerUserIdAndBusinessAreaAndActiveTrue(long ownerUserId, TeamBusinessArea businessArea);
    List<CustomerWorkspace> findAllByOwnerUserIdAndActiveTrue(long ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CustomerWorkspace> findLockedByIdAndActiveTrue(long id);
}
