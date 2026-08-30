package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.CustomerWorkspace; import org.pms.silverocean.service.teamaccess.TeamBusinessArea; import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional;
public interface CustomerWorkspaceRepo extends JpaRepository<CustomerWorkspace,Long>{Optional<CustomerWorkspace>findByOwnerUserIdAndBusinessAreaAndActiveTrue(long ownerUserId,TeamBusinessArea businessArea);}
