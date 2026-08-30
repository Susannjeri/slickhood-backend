package org.pms.silverocean.service.teamaccess;

import org.pms.silverocean.service.auth.roles.enums.PMSRole;

public enum TeamBusinessArea {
    LANDLORD(PMSRole.LANDLORD, "Landlord"),
    ESTATE_MANAGEMENT(PMSRole.ESTATE_MANAGER, "Estate Management"),
    PROPERTY_SALE_MANAGEMENT(PMSRole.SALES_AGENT, "Property Sale Management");

    private final PMSRole ownerRole;
    private final String displayName;

    TeamBusinessArea(PMSRole ownerRole, String displayName) { this.ownerRole = ownerRole; this.displayName = displayName; }
    public PMSRole ownerRole() { return ownerRole; }
    public String displayName() { return displayName; }
    public static TeamBusinessArea fromOwnerRole(PMSRole role) {
        for (TeamBusinessArea area : values()) if (area.ownerRole == role) return area;
        throw new IllegalArgumentException("Role does not own a customer workspace");
    }
}
