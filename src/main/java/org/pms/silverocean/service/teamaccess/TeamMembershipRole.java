package org.pms.silverocean.service.teamaccess;

import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

public enum TeamMembershipRole {
    WORKSPACE_ADMIN("Workspace administrator", 80, PMSRole.WORKSPACE_ADMIN, Set.of(TeamBusinessArea.LANDLORD, TeamBusinessArea.ESTATE_MANAGEMENT, TeamBusinessArea.PROPERTY_SALE_MANAGEMENT)),
    PROPERTY_MANAGER("Property manager", 60, PMSRole.PROPERTY_MANAGER, Set.of(TeamBusinessArea.LANDLORD)),
    PROPERTY_ACCOUNTANT("Property accountant", 50, PMSRole.PROPERTY_ACCOUNTANT, Set.of(TeamBusinessArea.LANDLORD, TeamBusinessArea.ESTATE_MANAGEMENT, TeamBusinessArea.PROPERTY_SALE_MANAGEMENT)),
    LEASING_OFFICER("Leasing officer", 40, PMSRole.LEASING_OFFICER, Set.of(TeamBusinessArea.LANDLORD)),
    ESTATE_OPERATIONS_MANAGER("Estate operations manager", 60, PMSRole.ESTATE_OPERATIONS_MANAGER, Set.of(TeamBusinessArea.ESTATE_MANAGEMENT)),
    SECURITY_SUPERVISOR("Security supervisor", 40, PMSRole.SECURITY_SUPERVISOR, Set.of(TeamBusinessArea.ESTATE_MANAGEMENT)),
    GUARD("Guard", 20, PMSRole.GUARD, Set.of(TeamBusinessArea.ESTATE_MANAGEMENT)),
    SALES_COORDINATOR("Sales coordinator", 60, PMSRole.SALES_COORDINATOR, Set.of(TeamBusinessArea.PROPERTY_SALE_MANAGEMENT)),
    LISTING_AGENT("Listing agent", 40, PMSRole.LISTING_AGENT, Set.of(TeamBusinessArea.PROPERTY_SALE_MANAGEMENT)),
    VIEWER("Viewer", 10, PMSRole.WORKSPACE_VIEWER, Set.of(TeamBusinessArea.LANDLORD, TeamBusinessArea.ESTATE_MANAGEMENT, TeamBusinessArea.PROPERTY_SALE_MANAGEMENT));

    private final String displayName; private final int privilegeLevel; private final PMSRole platformRole; private final Set<TeamBusinessArea> allowedAreas;
    TeamMembershipRole(String displayName, int privilegeLevel, PMSRole platformRole, Set<TeamBusinessArea> allowedAreas) { this.displayName=displayName; this.privilegeLevel=privilegeLevel; this.platformRole=platformRole; this.allowedAreas=allowedAreas; }
    public String displayName() { return displayName; }
    public int privilegeLevel() { return privilegeLevel; }
    public PMSRole platformRole() { return platformRole; }
    public boolean allowedFor(TeamBusinessArea area) { return allowedAreas.contains(area); }
    public static Optional<TeamMembershipRole> fromPlatformRole(PMSRole role) {
        return Arrays.stream(values()).filter(value -> value.platformRole == role).findFirst();
    }
}
