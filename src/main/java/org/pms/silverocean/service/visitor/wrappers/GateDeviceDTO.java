package org.pms.silverocean.service.visitor.wrappers;

import org.pms.silverocean.database.pms.entities.GateDevice;

import java.time.ZonedDateTime;

public record GateDeviceDTO(String deviceCode, long propertyId, String displayName,
                            String gateName, String laneName, boolean enabled, ZonedDateTime lastSeenAt) {
    public GateDeviceDTO(GateDevice device) {
        this(device.getDeviceCode(), device.getPropertyId(), device.getDisplayName(), device.getGateName(),
                device.getLaneName(), device.isEnabled(), device.getLastSeenAt());
    }
}
