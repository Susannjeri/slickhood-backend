package org.pms.silverocean.service.deployedhash;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class DeployedHashDTO {
    private final String hash;
    private final String appName;
    private final String appVersion;
    private final List<String> activeProfiles;
    private final Instant startedAt;
    private final long uptimeSeconds;
    private final Instant serverTime;
    private final String timezone;
}
