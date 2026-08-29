package org.pms.silverocean.service.lease;

import org.pms.silverocean.database.pms.LeaseMessageRepo;
import org.pms.silverocean.database.pms.entities.LeaseMessage;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.lease.wrappers.LeaseMessageDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class LeaseMessageDao {
    private final LeaseMessageRepo leaseMessageRepo;
    private final AuditLogService auditLogService;

    public LeaseMessageDao(LeaseMessageRepo leaseMessageRepo, AuditLogService auditLogService) {
        this.leaseMessageRepo = leaseMessageRepo;
        this.auditLogService = auditLogService;
    }

    public void save(LeaseMessage leaseMessage) {
        leaseMessageRepo.save(leaseMessage);
        auditLogService.createAuditLog(leaseMessage, Permission.SEND_LEASE_MESSAGE);
    }

    public Page<LeaseMessageDTO> getLeaseMessagesByLeaseId(Pageable pageable, long leaseId) {
        return leaseMessageRepo.findLeaseMessageDTOsByLeaseId(pageable, leaseId);
    }
}
