package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.ConfigRepo;
import org.pms.silverocean.database.pms.UserRepo;
import org.pms.silverocean.database.pms.entities.Config;
import org.pms.silverocean.service.auth.dao.UserReportDao;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.EditConfigDTO;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.security.EncryptionService;
import org.pms.silverocean.service.PMSCustomException;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminDashboardWiringTest {
    @Test void activePercentageIsNotReportedAsInactive() {
        UserRepo repo = mock(UserRepo.class);
        when(repo.getActiveUserPercentage()).thenReturn(80.0);
        assertEquals(20.0, new UserReportDao(repo).getInActiveUserPercentage());
    }
    @Test void configNumericValuesWithNoStringCanBeReadAndAudited() throws Exception {
        ConfigRepo repo = mock(ConfigRepo.class);
        Config config = new Config(); config.setId(9L); config.setName(PMSConfigs.SMS_MAX_RETRIES.getName()); config.setIntValue(0);
        when(repo.findByName(config.getName())).thenReturn(Optional.of(config));
        var service = new ConfigService(repo, mock(AuditLogService.class), mock(UserDao.class), mock(EncryptionService.class));
        assertEquals(0, service.getConfigByNameForFrontEndView(PMSConfigs.SMS_MAX_RETRIES).intValue());
        assertEquals(0, new com.fasterxml.jackson.databind.ObjectMapper().readTree(config.toAuditJSON()).get("intValue").asInt());
    }
    @Test void invalidNumericConfigDoesNotSave() {
        ConfigRepo repo = mock(ConfigRepo.class);
        when(repo.findByName(PMSConfigs.SMS_MAX_RETRIES.getName())).thenReturn(Optional.of(new Config()));
        var service = new ConfigService(repo, mock(AuditLogService.class), mock(UserDao.class), mock(EncryptionService.class));
        assertThrows(PMSCustomException.class, () -> service.updateConfig(new EditConfigDTO("not-a-number", PMSConfigs.SMS_MAX_RETRIES)));
        assertThrows(PMSCustomException.class, () -> service.updateConfig(new EditConfigDTO("-1", PMSConfigs.SMS_MAX_RETRIES)));
        verify(repo, never()).save(any());
    }
    @Test void auditEscapesTextAndMasksEncryptedValues() throws Exception {
        Config config = new Config(); config.setId(1L); config.setName("test"); config.setStringValue("quoted \"line\"\nvalue".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertEquals("quoted \"line\"\nvalue", mapper.readTree(config.toAuditJSON()).get("stringValue").asText());
        config.setEncrypted(true);
        assertEquals("*****", mapper.readTree(config.toAuditJSON()).get("stringValue").asText());
    }
    @Test void viewConfigurationPermissionDoesNotAuthorizeSecretDecryption() throws Exception {
        assertTrue(ConfigController.class.getMethod("getConfigDecryptedValue", PMSConfigs.class).getAnnotation(PreAuthorize.class).value().contains("EDIT_CONFIG"));
    }
}
