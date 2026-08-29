package org.pms.silverocean.service.account.enums;

import lombok.Getter;

@Getter
public enum AccountCategory {
    SLICKHOOD("SlickHood", "Platform-level master accounts"),
    LANDLORD("Landlord", "Landlord property payment accounts"),
    MERCHANT("Merchant", "Service and Soko merchant payment accounts"),
    INSURANCE("Insurance", "Silverwood-managed insurer payment destinations"),
    COMMUNITY_FUND("Community Fund", "Ring-fenced estate or apartment welfare and project fund accounts");

    private final String displayName;
    private final String description;

    AccountCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
