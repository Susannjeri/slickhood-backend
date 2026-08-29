package org.pms.silverocean.service.audit;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.proxy.HibernateProxy;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.database.audit.entities.AuditLog;
import org.pms.silverocean.database.pms.entities.Auditable;
import org.pms.silverocean.database.pms.entities.base.BaseIDEntity;
import org.pms.silverocean.service.I18NService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class AuditLogService {
    private final AuditDao auditDao;

    private final I18NService i18NService;

    private final List<AuditLog> buffer = Collections.synchronizedList(new ArrayList<>());
    private static final int BATCH_SIZE = 50;
    private static final long FLUSH_INTERVAL_MS = 1000 * 5;


    public AuditLogService(AuditDao auditDao, I18NService i18NService) {
        this.auditDao = auditDao;
        this.i18NService = i18NService;
    }

    public ResponseDTO getAuditLogs(Pageable pageable, Optional<String> filter) {
        Page<AuditLog> auditLogs = auditDao.getAuditLog(filter, pageable);

        ResponseDTO dto = new ResponseDTO();
        dto.setSuccess(true);
        dto.setCode(ResponseCode.AUDIT_LOG.getCode());
        dto.setDescription(i18NService.getLocalizedMessage(ResponseCode.AUDIT_LOG));
        dto.setData(auditLogs.toList());
        dto.setTotalElements(auditLogs.getTotalElements());
        dto.setSize(auditLogs.getSize());
        dto.setTotalPages(auditLogs.getTotalPages());

        return dto;
    }

    public <T> void createAuditLog(T entityInstance, String action) {
        createAuditLog(entityInstance, action, null, true);
    }

    public <T> void createAuditLog(T entityInstance, String action, String description, boolean success) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = "SYSTEM";
        if (authentication != null) {
            username = authentication.getPrincipal().toString();
        }
        if (entityInstance instanceof Auditable) {
            AuditLog auditLog = new AuditLog();
            auditLog.setUsername(username);
            auditLog.setAction(action);
            auditLog.setDescription(description);

            Class<?> clazz = entityInstance instanceof HibernateProxy
                    ? ((HibernateProxy) entityInstance).getHibernateLazyInitializer().getPersistentClass()
                    : entityInstance.getClass();

            auditLog.setEntityName(clazz.getSimpleName());

            auditLog.setEntityId(((BaseIDEntity) entityInstance).getId());
            auditLog.setValue(((Auditable) entityInstance).toAuditJSON());
            auditLog.setSuccess(success);

            buffer.add(auditLog);
            if (buffer.size() >= BATCH_SIZE) {
                flush();
            }
        }
    }

    @Scheduled(fixedRate = FLUSH_INTERVAL_MS)
    public void scheduledFlush() {
        flush();
    }

    private void flush() {
        List<AuditLog> copy;
        synchronized (buffer) {
            if (buffer.isEmpty()) return;
            copy = new ArrayList<>(buffer);
            buffer.clear();
        }
        log.info("Flushing audit logs {} items", copy.size());
        auditDao.saveAll(copy);
    }
}
