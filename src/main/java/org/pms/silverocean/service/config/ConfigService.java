package org.pms.silverocean.service.config;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.ConfigRepo;
import org.pms.silverocean.database.pms.entities.Config;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.security.DecryptDTO;
import org.pms.silverocean.service.security.EncryptionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service("configService")
@Slf4j
public class ConfigService {
    private final ConfigRepo configRepo;
    private final AuditLogService auditLogService;
    private final UserDao userDao;
    private final EncryptionService encryptionService;

    private final LoadingCache<String, Optional<ConfigDTO>> configCache = CacheBuilder.newBuilder()
            .build(new CacheLoader<>() {
                @Override
                public Optional<ConfigDTO> load(String key) {
                    return findByName(key);
                }
            });


    public ConfigService(ConfigRepo configRepo, AuditLogService auditLogService, UserDao userDao, EncryptionService encryptionService) {
        this.configRepo = configRepo;
        this.auditLogService = auditLogService;
        this.userDao = userDao;
        this.encryptionService = encryptionService;
    }

    public Set<String> getAllConfigNames() {
        return EnumSet.allOf(PMSConfigs.class).stream().map(Enum::name).collect(Collectors.toSet());
    }

    public Supplier<ConfigDTO> getConfigByName(PMSConfigs config) {
        return () -> {
            try {
                Optional<ConfigDTO> configDTO = configCache.get(config.getName());
                return configDTO
                        .orElseThrow(() -> new PMSCustomException(ResponseCode.CONFIG_WITH_GIVEN_NAME_MISSING));
            } catch (Exception e) {
                Throwable cause = e.getCause();
                if (cause instanceof PMSCustomException) {
                    throw (PMSCustomException) cause;
                } else {
                    throw new PMSCustomException(ResponseCode.CONFIG_WITH_GIVEN_NAME_MISSING);
                }

            }
        };
    }

    public ConfigDTO getConfigByNameForFrontEndView(PMSConfigs config) {
        ConfigDTO cachedConfig = getConfigByName(config).get();
        ConfigDTO configDTO;
        assert cachedConfig != null;
        if (cachedConfig.encrypted()) {
            configDTO = new ConfigDTO(cachedConfig.id(), cachedConfig.name(), "*****", 0, true);
        } else {
            configDTO = new ConfigDTO(cachedConfig.id(), cachedConfig.name(), cachedConfig.stringValue(), cachedConfig.intValue(), false);
        }
        return configDTO;
    }

    public void updateConfig(EditConfigDTO configDTO) {
        Config configByName = configRepo.findByName(configDTO.config().getName())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.CONFIG_WITH_GIVEN_NAME_MISSING));
        if (StringUtils.isNotBlank(configDTO.config().getStringValue())) {
            byte[] value = configDTO.config().isEncrypted() ? encryptionService.encrypt(configDTO.value()) : configDTO.value().getBytes();

            configByName.setStringValue(value);
        } else {
            configByName.setIntValue(Integer.parseInt(configDTO.value()));
        }
        saveConfig(configByName);
        auditLogService.createAuditLog(configByName, Permission.EDIT_CONFIG);
    }

    public void rotateKey() {
        try {
            encryptionService.rotateKey(userDao.getUserId());
        } catch (Exception e) {
            throw new PMSCustomException(ResponseCode.FAILED_TO_ROTATE_KEY, e);
        }
    }

    private void saveConfig(Config config) {
        config.setUpdatedOn(LocalDateTime.now());
        configRepo.save(config);
        configCache.invalidate(config.getName());
    }

    private Optional<ConfigDTO> findByName(String name) {
        return configRepo.findByName(name)
                .map(config -> {
                    ConfigDTO configDTO;
                    if (config.isEncrypted()) {
                        DecryptDTO decrypt = encryptionService.decrypt(config.getStringValue());
                        if (decrypt != null) {
                            if (decrypt.usedOldKey()) {
                                config.setStringValue(encryptionService.encrypt(decrypt.decryptedValue()));
                                saveConfig(config);
                            }
                            configDTO = new ConfigDTO(config.getId(), config.getName(), decrypt.decryptedValue(), config.getIntValue(), true);
                        } else {
                            throw new PMSCustomException(ResponseCode.FAILED_TO_DECRYPT_CONFIG);
                        }
                    } else {
                        configDTO = new ConfigDTO(config.getId(), config.getName(), new String(config.getStringValue()), config.getIntValue(), false);
                    }
                    return configDTO;
                });
    }

}
