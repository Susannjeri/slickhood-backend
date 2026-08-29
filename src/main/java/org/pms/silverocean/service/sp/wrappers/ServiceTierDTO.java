package org.pms.silverocean.service.sp.wrappers;

import org.pms.silverocean.database.pms.entities.ServiceTier;

public record ServiceTierDTO(long id, String name, String description, String requirements) {
    public ServiceTierDTO(ServiceTier t) {
        this(t.getId(), t.getName(), t.getDescription(), t.getRequirements());
    }
}
