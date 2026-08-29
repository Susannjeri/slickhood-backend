package org.pms.silverocean.service.invites;

import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.InviteRepo;
import org.pms.silverocean.database.pms.entities.Invite;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class InviteDao {
    private final InviteRepo inviteRepo;
    private final AuditLogService auditLogService;

    public InviteDao(InviteRepo inviteRepo, AuditLogService auditLogService) {
        this.inviteRepo = inviteRepo;
        this.auditLogService = auditLogService;
    }

    public void createInvite(Invite invite) {
        if (invite.getId() == null) {
            invite.setLastModifiedDate(LocalDateTime.now());
            inviteRepo.save(invite);
            auditLogService.createAuditLog(invite, Permission.CREATE_INVITE);
        } else {
            auditLogService.createAuditLog(invite, Permission.CREATE_INVITE, "Create lease invite failed, use of save method to update invite", false);
            throw new PMSCustomException(ResponseCode.INVITE_ALREADY_EXISTS);
        }
    }

    public Optional<Invite> getInviteByToken(String token, boolean active) {
        return inviteRepo.findByTokenAndActive(token, active);
    }

    public Page<Invite> listUserInvites(Pageable pageable, long createdBy) {
        return inviteRepo.findByCreator(pageable, createdBy);
    }

    public Page<Invite> listInvitesByUnitAndCreatedBy(Pageable pageable, long createdBy, long unitId) {
        return inviteRepo.findByUnitAndCreatorAndType(pageable, createdBy, unitId, InviteType.TENANT.name());
    }

    public Optional<Invite> getInviteByInviteIdAndCreatedBy(long inviteId, long createdBy) {
        return inviteRepo.findByIdAndActiveTrueAndCreatedBy(inviteId, createdBy);
    }

    public void updateInvite(Invite invite) {
        auditLogService.createAuditLog(invite, Permission.UPDATE_INVITE);
        inviteRepo.save(invite);
    }
}
