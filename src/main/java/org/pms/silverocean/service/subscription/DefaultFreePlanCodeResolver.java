package org.pms.silverocean.service.subscription;

import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class DefaultFreePlanCodeResolver {

    private final String landlordStarter;
    private final String serviceProviderStandard;
    private final String affiliateStandard;
    private final String assetManagerBasic;
    private final String estateStarter;
    private final String salesStarter;

    public DefaultFreePlanCodeResolver(
            @Value("${subscription.tier.landlord.starter:LANDLORD_BRONZE}") String landlordStarter,
            @Value("${subscription.tier.serviceprovider.standard:STANDARD}") String serviceProviderStandard,
            @Value("${subscription.tier.affiliate.standard:STANDARD_AFFILIATE}") String affiliateStandard,
            @Value("${subscription.tier.assetmanager.basic:WEALTH_BRONZE}") String assetManagerBasic,
            @Value("${subscription.tier.estate.starter:ESTATE_BRONZE}") String estateStarter,
            @Value("${subscription.tier.sales.starter:SALE_BRONZE}") String salesStarter
    ) {
        this.landlordStarter = landlordStarter;
        this.serviceProviderStandard = serviceProviderStandard;
        this.affiliateStandard = affiliateStandard;
        this.assetManagerBasic = assetManagerBasic;
        this.estateStarter = estateStarter;
        this.salesStarter = salesStarter;
    }

    public String resolvePlanCode(PMSRole role) {
        String raw = switch (role) {
            case LANDLORD -> landlordStarter;
            case SERVICE_PROVIDER -> serviceProviderStandard;
            case AFFILIATE -> affiliateStandard;
            case ASSET_PORTFOLIO_MANAGER -> assetManagerBasic;
            case ESTATE_MANAGER -> estateStarter;
            case SALES_AGENT -> salesStarter;
            default -> null;
        };
        return raw == null ? null : raw.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Roles that participate in billed subscription tiers (receive default free tier on onboarding).
     */
    public boolean isProvisioningRole(PMSRole role) {
        return switch (role) {
            case LANDLORD, ESTATE_MANAGER, SALES_AGENT, SERVICE_PROVIDER, AFFILIATE, ASSET_PORTFOLIO_MANAGER -> true;
            default -> false;
        };
    }
}
