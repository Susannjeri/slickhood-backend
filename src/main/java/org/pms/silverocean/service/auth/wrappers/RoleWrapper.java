package org.pms.silverocean.service.auth.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.Role;
import org.pms.silverocean.service.wrappers.IdNameDescDTO;

import java.util.Set;

@Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
public class RoleWrapper {
    private long roleId;
    private String roleName;
    private String roleDescription;
    private Set<String> rolePermissions;
    private Set<IdNameDescDTO> property;
    private boolean selfAssignable;

    public RoleWrapper(Role role, Set<String> rolePermissions, Set<IdNameDescDTO> property) {
        this.roleId = role.getId();
        this.roleName = role.getName();
        this.roleDescription = role.getDescription();
        this.rolePermissions = rolePermissions;
        this.selfAssignable = role.isSelfAssignable();
        this.property = property;
    }
}
