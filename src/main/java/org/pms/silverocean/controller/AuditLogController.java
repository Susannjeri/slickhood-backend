package org.pms.silverocean.controller;

import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.audit.AuditLogService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/audit")
public class AuditLogController {
    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_AUDIT_LOGS)")
    public ResponseEntity<ResponseDTO> getAuditLogs(Pageable pageable, @RequestParam Optional<String> filter) {
        return ResponseEntity.ok(auditLogService.getAuditLogs(pageable, filter));
    }
}
