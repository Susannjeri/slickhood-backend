package org.pms.silverocean.service.lease;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.PropertyRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.entities.Lease;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.property.PMSPropertyManagementMode;
import org.pms.silverocean.service.teamaccess.WorkspaceSelectionService;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class LeaseAccessService {
    private final LeaseDao leases;
    private final UnitRepo units;
    private final PropertyRepo properties;
    private final UserDao users;
    private final WorkspaceSelectionService workspaces;

    public Long selectedAssignmentId() {
        return workspaces.selectedMembership(users.getUserId()).map(m -> -m.getId()).orElse(null);
    }

    public void check(Lease lease) {
        var tenancy = leases.getUnitTenantByTenantId(lease.getTenantId()).orElseThrow(this::denied);
        var unit = units.findById(tenancy.getUnitId()).filter(u -> u.isActive() && "RENT".equals(u.getLeaseMode())).orElseThrow(this::denied);
        PMSRole role = users.getActiveRole();
        java.util.Optional<Property> property = java.util.Optional.empty();
        if (role == PMSRole.SUPER_ADMIN || (role == PMSRole.TENANT && tenancy.getUserId() == users.getUserId()))
            property = properties.findById(unit.getPropertyId()).filter(Property::isActive);
        else if (role == PMSRole.LANDLORD)
            property = properties.findByIdAndCreatedByAndActiveTrue(unit.getPropertyId(), users.getUserId());
        else if (role != null && role.isCustomerEmployeeRole()) {
            Long assignment = selectedAssignmentId();
            if (assignment != null) property = properties.findByIdAndManagerRoleAndInviteId(unit.getPropertyId(), users.getUserId(), role.name(), assignment);
        }
        property.filter(p -> p.getManagementMode() == PMSPropertyManagementMode.RENTAL).orElseThrow(this::denied);
        if (!lease.isActive() || !"RENT".equals(lease.getLeaseMode())) throw denied();
    }

    private PMSCustomException denied() { return new PMSCustomException(ResponseCode.LEASE_NOT_FOUND); }
}
