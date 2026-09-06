package org.pms.silverocean.service.sales;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.PropertyRepo;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.property.PMSPropertyManagementMode;
import org.pms.silverocean.service.teamaccess.WorkspaceSelectionService;
import org.springframework.stereotype.Service;

/** Sales access follows the active role and selected membership, never dormant roles. */
@Service @RequiredArgsConstructor
public class SalesAccessService {
    private final PropertyRepo properties;
    private final UserDao users;
    private final WorkspaceSelectionService workspaces;

    public Long selectedAssignmentId() {
        return workspaces.selectedMembership(users.getUserId()).map(m -> -m.getId()).orElse(null);
    }

    public Property require(long propertyId, String permission) {
        PMSRole role = users.getActiveRole();
        java.util.Optional<Property> result = java.util.Optional.empty();
        if (role == PMSRole.SUPER_ADMIN) result = properties.findById(propertyId).filter(Property::isActive);
        else if (users.hasPermission(permission)) {
            if (role == PMSRole.SALES_AGENT) result = properties.findByIdAndCreatedByAndActiveTrue(propertyId, users.getUserId());
            else if (role != null && role.isCustomerEmployeeRole()) {
                Long assignment = selectedAssignmentId();
                if (assignment != null) result = properties.findByIdAndManagerRoleAndInviteId(propertyId, users.getUserId(), role.name(), assignment);
            }
        }
        return result.filter(p -> p.getManagementMode() == PMSPropertyManagementMode.SALE)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
    }
}
