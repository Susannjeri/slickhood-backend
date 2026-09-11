package org.pms.silverocean.service.estate;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.PropertyRepo;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.teamaccess.WorkspaceSelectionService;
import org.springframework.stereotype.Service;

/** Applies the active business role and selected workspace, not dormant account roles. */
@Service
@RequiredArgsConstructor
public class EstateAccessService {
    private final PropertyRepo properties;
    private final UserDao users;
    private final WorkspaceSelectionService workspaces;

    public Long selectedAssignmentId() {
        return workspaces.selectedMembership(users.getUserId()).map(member -> -member.getId()).orElse(null);
    }

    public Property require(long propertyId, String permission) {
        PMSRole role = users.getActiveRole();
        java.util.Optional<Property> property = java.util.Optional.empty();
        if (role == PMSRole.SUPER_ADMIN) {
            property = properties.findById(propertyId).filter(Property::isActive);
        } else if (role == PMSRole.HOMEOWNER && Permission.VIEW_ESTATE.equals(permission)) {
            property = properties.findByIdAndHomeowner(propertyId, users.getUserId());
        } else if (users.hasPermission(permission)) {
            if (role == PMSRole.ESTATE_MANAGER) {
                property = properties.findByIdAndCreatedByAndActiveTrue(propertyId, users.getUserId());
            } else if (role != null && role.isCustomerEmployeeRole()) {
                Long assignmentId = selectedAssignmentId();
                if (assignmentId != null) {
                    property = properties.findByIdAndManagerRoleAndInviteId(propertyId, users.getUserId(), role.name(), assignmentId);
                }
            }
        }
        return property.orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
    }
}
