package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.TeamRoleDefinition;
import org.pms.silverocean.service.teamaccess.TeamBusinessArea;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface TeamRoleDefinitionRepo extends JpaRepository<TeamRoleDefinition, Long> {
    Optional<TeamRoleDefinition> findByIdAndActiveTrue(long id);
    Optional<TeamRoleDefinition> findByCodeIgnoreCase(String code);
    List<TeamRoleDefinition> findByBusinessAreaAndActiveTrueOrderByDisplayName(TeamBusinessArea businessArea);
    List<TeamRoleDefinition> findAllByOrderByBusinessAreaAscDisplayNameAsc();
}
