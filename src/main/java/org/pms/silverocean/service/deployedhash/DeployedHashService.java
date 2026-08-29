package org.pms.silverocean.service.deployedhash;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

@Service
@Slf4j
public class DeployedHashService {

    static final String UNKNOWN = "unknown";
    private static final String DEV_PROFILE = "default";

    @Value("${spring.application.name:silverocean}")
    private String appName;

    private final ObjectProvider<BuildProperties> buildPropertiesProvider;
    private final Environment environment;

    private String classpathCommitHash = UNKNOWN;

    public DeployedHashService(ObjectProvider<BuildProperties> buildPropertiesProvider, Environment environment) {
        this.buildPropertiesProvider = buildPropertiesProvider;
        this.environment = environment;
    }

    @PostConstruct
    void loadClasspathGitHash() {
        ClassPathResource resource = new ClassPathResource("git.properties");
        if (!resource.exists()) {
            classpathCommitHash = UNKNOWN;
            return;
        }
        Properties properties = new Properties();
        try (InputStream in = resource.getInputStream()) {
            properties.load(in);
        } catch (IOException e) {
            log.warn("Could not read classpath git.properties", e);
            classpathCommitHash = UNKNOWN;
            return;
        }
        String full = properties.getProperty("git.commit.id.full");
        String abbrev = properties.getProperty("git.commit.id.abbrev");
        if (StringUtils.isNotBlank(full)) {
            classpathCommitHash = full.trim();
        } else if (StringUtils.isNotBlank(abbrev)) {
            classpathCommitHash = abbrev.trim();
        } else {
            classpathCommitHash = UNKNOWN;
        }
    }

    public String getDeployedHash() {
        if (!UNKNOWN.equals(classpathCommitHash)) {
            return classpathCommitHash;
        }
        return UNKNOWN;
    }

    public DeployedHashDTO getDeployedHashDetails() {
        long startMillis = ManagementFactory.getRuntimeMXBean().getStartTime();
        Instant startedAt = Instant.ofEpochMilli(startMillis);
        Instant now = Instant.now();
        long uptimeSeconds = Math.max(0, now.getEpochSecond() - startedAt.getEpochSecond());
        List<String> profiles = resolveProfiles();
        BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
        String appVersion = buildProperties != null ? buildProperties.getVersion() : UNKNOWN;
        return new DeployedHashDTO(
                getDeployedHash(),
                appName,
                appVersion,
                profiles,
                startedAt,
                uptimeSeconds,
                now,
                ZoneId.systemDefault().getId()
        );
    }

    private List<String> resolveProfiles() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return List.of(DEV_PROFILE);
        }
        return Arrays.asList(activeProfiles);
    }
}
