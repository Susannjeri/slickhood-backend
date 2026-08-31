package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.WorkspaceInvitation;
import org.pms.silverocean.service.teamaccess.TeamMembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface WorkspaceInvitationRepo extends JpaRepository<WorkspaceInvitation,Long> {
    Optional<WorkspaceInvitation> findByTokenHashAndActiveTrue(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorkspaceInvitation> findLockedByTokenHashAndActiveTrue(String tokenHash);

    Optional<WorkspaceInvitation> findByIdAndWorkspaceIdAndActiveTrue(long id,long workspaceId);
    Optional<WorkspaceInvitation> findFirstByWorkspaceIdAndMembershipIdAndActiveTrue(long workspaceId, long membershipId);
    List<WorkspaceInvitation> findByWorkspaceIdAndActiveTrueOrderByCreatedOnDesc(long workspaceId);
    boolean existsByWorkspaceIdAndRecipientEmailIgnoreCaseAndStatusInAndActiveTrue(long workspaceId,String email,List<TeamMembershipStatus>statuses);
    long countByWorkspaceIdAndStatusInAndActiveTrue(long workspaceId,List<TeamMembershipStatus>statuses);
}
