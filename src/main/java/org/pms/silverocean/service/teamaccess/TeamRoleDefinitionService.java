package org.pms.silverocean.service.teamaccess;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.TeamRoleDefinitionRepo;
import org.pms.silverocean.database.pms.entities.TeamRoleDefinition;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class TeamRoleDefinitionService {
    private final TeamRoleDefinitionRepo definitions;
    private final UserDao users;
    private final AuditLogService audit;

    public List<TeamAccessModels.RoleDefinitionView> list() {
        requireSuperadmin();
        return definitions.findAllByOrderByBusinessAreaAscDisplayNameAsc().stream().map(this::view).toList();
    }

    @Transactional
    public TeamAccessModels.RoleDefinitionView create(TeamAccessModels.RoleDefinitionRequest request) {
        requireSuperadmin();
        if (!request.permissionTemplate().allowedFor(request.businessArea())) throw new PMSCustomException(ResponseCode.INVALID_ROLE);
        if (definitions.findByCodeIgnoreCase(request.code()).isPresent()) throw new PMSCustomException(ResponseCode.DATA_INTEGRITY_VIOLATION);
        TeamRoleDefinition definition = new TeamRoleDefinition();
        definition.setCode(request.code().trim().toUpperCase(Locale.ROOT));
        apply(definition, request); definition.setCreatedBy(users.getUserId()); definition.setActive(true);
        definition = definitions.save(definition); audit.createAuditLog(definition, "team_role_definition_create");
        return view(definition);
    }

    @Transactional
    public TeamAccessModels.RoleDefinitionView update(long id, TeamAccessModels.RoleDefinitionRequest request) {
        requireSuperadmin(); TeamRoleDefinition definition = definitions.findById(id).orElseThrow(this::invalid);
        if (!definition.getCode().equalsIgnoreCase(request.code()) && definitions.findByCodeIgnoreCase(request.code()).isPresent()) throw new PMSCustomException(ResponseCode.DATA_INTEGRITY_VIOLATION);
        if (!request.permissionTemplate().allowedFor(request.businessArea())) throw new PMSCustomException(ResponseCode.INVALID_ROLE);
        definition.setCode(request.code().trim().toUpperCase(Locale.ROOT)); apply(definition, request);
        definition = definitions.save(definition); audit.createAuditLog(definition, "team_role_definition_update"); return view(definition);
    }

    @Transactional
    public TeamAccessModels.RoleDefinitionView setActive(long id, boolean active) {
        requireSuperadmin(); TeamRoleDefinition definition = definitions.findById(id).orElseThrow(this::invalid);
        definition.setActive(active); definition = definitions.save(definition); audit.createAuditLog(definition, active ? "team_role_definition_enable" : "team_role_definition_disable");
        return view(definition);
    }

    private void apply(TeamRoleDefinition d, TeamAccessModels.RoleDefinitionRequest r) { d.setDisplayName(r.displayName().trim()); d.setDescription(r.description()); d.setBusinessArea(r.businessArea()); d.setPermissionTemplate(r.permissionTemplate()); }
    private TeamAccessModels.RoleDefinitionView view(TeamRoleDefinition d) { return new TeamAccessModels.RoleDefinitionView(d.getId(),d.getCode(),d.getDisplayName(),d.getDescription(),d.getBusinessArea(),d.getPermissionTemplate(),d.isActive()); }
    private void requireSuperadmin() { if (users.getActiveRole() != PMSRole.SUPER_ADMIN) throw new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS); }
    private PMSCustomException invalid() { return new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND); }
}
