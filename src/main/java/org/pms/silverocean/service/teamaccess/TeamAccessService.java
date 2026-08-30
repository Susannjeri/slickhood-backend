package org.pms.silverocean.service.teamaccess;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.*;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.*;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.kyc.AccountStatus;
import org.pms.silverocean.service.notification.*;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.subscription.enums.SubscriptionStatus;
import org.pms.silverocean.service.wrappers.IdNameDescDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

import static org.pms.silverocean.common.PMSUtils.formatInviteLink;

@Service @RequiredArgsConstructor
public class TeamAccessService {
    private static final List<TeamMembershipStatus> OCCUPIED_MEMBER_STATUSES = List.of(TeamMembershipStatus.ACCEPTED, TeamMembershipStatus.KYC_PENDING, TeamMembershipStatus.ACTIVE, TeamMembershipStatus.SUSPENDED);
    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);
    private static final int MAX_RESENDS = 5;
    private final CustomerWorkspaceRepo workspaces;
    private final WorkspaceInvitationRepo invitations;
    private final WorkspaceMembershipRepo memberships;
    private final TeamRoleDefinitionRepo roleDefinitions;
    private final PropertyRepo properties;
    private final PropertyManagerRepo propertyManagers;
    private final UserSubscriptionRepo subscriptions;
    private final SubscriptionPlanRepo plans;
    private final PlanQuotaRepo quotas;
    private final RoleRepo roles;
    private final UserRoleRepo userRoles;
    private final UserDao users;
    private final ConfigService config;
    private final NotificationService notifications;
    private final I18NService i18n;
    private final AuditLogService audit;
    private final ObjectMapper mapper;

    @Transactional
    public TeamAccessModels.WorkspaceView current() {
        AccessContext access = accessContext();
        expireInvitations(access.workspace());
        memberships.findByWorkspaceIdAndActiveTrueOrderByCreatedOnDesc(access.workspace().getId()).forEach(this::syncKycStatus);
        return view(access);
    }

    @Transactional
    public TeamAccessModels.InvitationView invite(TeamAccessModels.InviteRequest request) {
        AccessContext access = accessContext();
        TeamRoleDefinition definition = roleDefinitions.findByIdAndActiveTrue(request.roleDefinitionId()).orElseThrow(this::invalid);
        if (definition.getBusinessArea() != access.workspace().getBusinessArea()) throw new PMSCustomException(ResponseCode.INVALID_ROLE);
        TeamMembershipRole role = definition.getPermissionTemplate();
        requireCanGrant(access, role);
        validateEmail(request.email());
        String email = normalizeEmail(request.email());
        if (email.equalsIgnoreCase(users.getUserObject().getEmail())) throw invalid();
        if (memberships.findByWorkspaceIdAndActiveTrueOrderByCreatedOnDesc(access.workspace().getId()).stream().anyMatch(m -> m.getMemberEmail().equalsIgnoreCase(email) && m.getStatus()!=TeamMembershipStatus.REVOKED)
                || invitations.existsByWorkspaceIdAndRecipientEmailIgnoreCaseAndStatusInAndActiveTrue(access.workspace().getId(), email, List.of(TeamMembershipStatus.PENDING))) {
            throw new PMSCustomException(ResponseCode.INVITE_ALREADY_EXISTS);
        }
        enforceSeats(access.workspace());
        List<Long> resources = validateScope(access.workspace(), request.scopeType(), request.resourceIds());
        RawToken token = token();
        WorkspaceInvitation invitation = new WorkspaceInvitation();
        invitation.setWorkspaceId(access.workspace().getId()); invitation.setRecipientEmail(email);
        invitation.setRoleDefinitionId(definition.getId()); invitation.setMembershipRole(role); invitation.setScopeType(request.scopeType());
        invitation.setResourceIdsJson(writeIds(resources)); invitation.setTokenHash(token.hash());
        invitation.setStatus(TeamMembershipStatus.PENDING); invitation.setExpiresAt(expiry());
        invitation.setLastSentAt(LocalDateTime.now()); invitation.setCreatedBy(users.getUserId()); invitation.setActive(true);
        invitation = invitations.save(invitation);
        audit.createAuditLog(invitation, "workspace_invite");
        send(invitation, access.workspace(), token.raw());
        return invitationView(invitation);
    }

    @Transactional
    public TeamAccessModels.InvitationView resend(long invitationId) {
        AccessContext access = accessContext();
        WorkspaceInvitation invitation = ownedInvitation(access, invitationId);
        requireCanGrant(access, invitation.getMembershipRole());
        if (!(invitation.getStatus()==TeamMembershipStatus.PENDING || invitation.getStatus()==TeamMembershipStatus.EXPIRED)
                || invitation.getResendCount()>=MAX_RESENDS) throw invalid();
        if (invitation.getLastSentAt()!=null && Duration.between(invitation.getLastSentAt(), LocalDateTime.now()).compareTo(RESEND_COOLDOWN)<0) throw invalid();
        RawToken token = token();
        invitation.setTokenHash(token.hash()); invitation.setStatus(TeamMembershipStatus.PENDING);
        invitation.setExpiresAt(expiry()); invitation.setLastSentAt(LocalDateTime.now()); invitation.setResendCount(invitation.getResendCount()+1);
        invitations.save(invitation); audit.createAuditLog(invitation, "workspace_invite_resend");
        send(invitation, access.workspace(), token.raw());
        return invitationView(invitation);
    }

    @Transactional
    public TeamAccessModels.InviteInspection inspect(String rawToken) {
        WorkspaceInvitation invitation = invitation(rawToken);
        expire(invitation);
        CustomerWorkspace workspace = workspaces.findById(invitation.getWorkspaceId()).orElseThrow(this::invalid);
        return new TeamAccessModels.InviteInspection(mask(invitation.getRecipientEmail()), workspace.getName(),
                workspace.getBusinessArea().displayName(), roleName(invitation.getRoleDefinitionId(), invitation.getMembershipRole()),
                invitation.getExpiresAt(), invitation.getStatus());
    }

    @Transactional
    public WorkspaceMembership accept(String rawToken) {
        Users user = users.getUserObject();
        if (user==null) throw new PMSCustomException(ResponseCode.ASSIGNED_ROLE_REGISTRATION_REQUIRED);
        return acceptForUser(invitation(rawToken), user);
    }

    @Transactional
    public void registerInvitedUser(String rawToken, Users user) {
        WorkspaceInvitation invitation = invitation(rawToken);
        requireEmail(invitation, user.getEmail());
        Users saved = users.save(user);
        acceptForUser(invitation, saved);
    }

    public boolean isTeamToken(String rawToken) {
        if (rawToken==null || rawToken.isBlank()) return false;
        return invitations.findByTokenHashAndActiveTrue(hash(rawToken)).isPresent();
    }

    @Transactional
    public TeamAccessModels.MemberView updateScope(long memberId, TeamAccessModels.ScopeUpdate request) {
        AccessContext access = accessContext(); WorkspaceMembership member = ownedMember(access, memberId);
        requireCanManage(access, member); List<Long> ids = validateScope(access.workspace(), request.scopeType(), request.resourceIds());
        member.setScopeType(request.scopeType()); member.setResourceIdsJson(writeIds(ids)); memberships.save(member);
        if (member.getStatus()==TeamMembershipStatus.ACTIVE) refreshAssignments(access.workspace(), member);
        audit.createAuditLog(member, "workspace_member_scope_change"); return memberView(member);
    }

    @Transactional public TeamAccessModels.MemberView suspend(long id) { return changeMemberStatus(id, TeamMembershipStatus.SUSPENDED); }
    @Transactional public TeamAccessModels.MemberView revokeMember(long id) { return changeMemberStatus(id, TeamMembershipStatus.REVOKED); }
    @Transactional public TeamAccessModels.MemberView resume(long id) {
        AccessContext access=accessContext(); WorkspaceMembership member=ownedMember(access,id); requireCanManage(access,member);
        member.setSuspendedAt(null); member.setStatus(TeamMembershipStatus.KYC_PENDING); assignPlatformRole(member.getUserId(),member.getMembershipRole().platformRole()); syncKycStatus(member);
        audit.createAuditLog(member,"workspace_member_resume"); return memberView(member);
    }

    @Transactional public void revokeInvitation(long id) {
        AccessContext access=accessContext(); WorkspaceInvitation invitation=ownedInvitation(access,id); requireCanGrant(access,invitation.getMembershipRole());
        if (invitation.getStatus()!=TeamMembershipStatus.PENDING && invitation.getStatus()!=TeamMembershipStatus.EXPIRED) throw invalid();
        invitation.setStatus(TeamMembershipStatus.REVOKED); invitations.save(invitation); audit.createAuditLog(invitation,"workspace_invite_revoke");
    }

    @Transactional
    public void assignNewProperty(long ownerUserId, long propertyId) {
        for (CustomerWorkspace workspace : workspaces.findAll()) {
            if (workspace.isActive() && workspace.getOwnerUserId()==ownerUserId) {
                memberships.findByWorkspaceIdAndStatusAndScopeTypeAndActiveTrue(workspace.getId(), TeamMembershipStatus.ACTIVE, TeamScopeType.ENTIRE_WORKSPACE)
                        .forEach(member -> addAssignment(member, propertyId));
            }
        }
    }

    private WorkspaceMembership acceptForUser(WorkspaceInvitation invitation, Users user) {
        expire(invitation); requireEmail(invitation,user.getEmail());
        if (invitation.getStatus()!=TeamMembershipStatus.PENDING) throw new PMSCustomException(ResponseCode.INVALID_OR_EXPIRED_TOKEN);
        WorkspaceMembership member = memberships.findByWorkspaceIdAndUserIdAndActiveTrue(invitation.getWorkspaceId(),user.getId()).orElseGet(WorkspaceMembership::new);
        if (member.getId()!=null && member.getStatus()!=TeamMembershipStatus.REVOKED) throw new PMSCustomException(ResponseCode.INVITE_ALREADY_EXISTS);
        member.setWorkspaceId(invitation.getWorkspaceId()); member.setUserId(user.getId()); member.setMemberEmail(normalizeEmail(user.getEmail()));
        member.setRoleDefinitionId(invitation.getRoleDefinitionId()); member.setMembershipRole(invitation.getMembershipRole()); member.setScopeType(invitation.getScopeType()); member.setResourceIdsJson(invitation.getResourceIdsJson());
        member.setStatus(TeamMembershipStatus.ACCEPTED); member.setAcceptedAt(LocalDateTime.now()); member.setActivatedAt(null); member.setSuspendedAt(null); member.setRevokedAt(null); member.setCreatedBy(invitation.getCreatedBy()); member.setActive(true);
        member=memberships.save(member); assignPlatformRole(user.getId(),member.getMembershipRole().platformRole());
        invitation.setStatus(TeamMembershipStatus.ACCEPTED); invitation.setAcceptedAt(LocalDateTime.now()); invitation.setMembershipId(member.getId()); invitations.save(invitation);
        audit.createAuditLog(invitation,"workspace_invite_accept"); audit.createAuditLog(member,"workspace_membership_create");
        syncKycStatus(member); return member;
    }

    private void syncKycStatus(WorkspaceMembership member) {
        if (member.getStatus()==TeamMembershipStatus.ACTIVE) {
            if (member.getScopeType()==TeamScopeType.ENTIRE_WORKSPACE) workspaces.findById(member.getWorkspaceId()).ifPresent(w->refreshAssignments(w,member));
            return;
        }
        if (!(member.getStatus()==TeamMembershipStatus.ACCEPTED || member.getStatus()==TeamMembershipStatus.KYC_PENDING)) return;
        Users user=users.findById(member.getUserId()).orElse(null); if(user==null)return;
        if(AccountStatus.ACTIVE.name().equals(user.getAccountStatus())) { member.setStatus(TeamMembershipStatus.ACTIVE); member.setActivatedAt(LocalDateTime.now()); memberships.save(member); CustomerWorkspace workspace=workspaces.findById(member.getWorkspaceId()).orElseThrow(this::invalid); refreshAssignments(workspace,member); updateInvitationStatus(member,TeamMembershipStatus.ACTIVE); audit.createAuditLog(member,"workspace_membership_activate"); }
        else { member.setStatus(TeamMembershipStatus.KYC_PENDING); memberships.save(member); updateInvitationStatus(member,TeamMembershipStatus.KYC_PENDING); }
    }

    private TeamAccessModels.MemberView changeMemberStatus(long id,TeamMembershipStatus status){AccessContext access=accessContext();WorkspaceMembership member=ownedMember(access,id);requireCanManage(access,member);if(status==TeamMembershipStatus.SUSPENDED)member.setSuspendedAt(LocalDateTime.now());else member.setRevokedAt(LocalDateTime.now());member.setStatus(status);memberships.save(member);deactivateAssignments(member);removePlatformRoleIfUnused(member);updateInvitationStatus(member,status);audit.createAuditLog(member,status==TeamMembershipStatus.SUSPENDED?"workspace_member_suspend":"workspace_member_revoke");return memberView(member);}
    private void updateInvitationStatus(WorkspaceMembership member,TeamMembershipStatus status){invitations.findByWorkspaceIdAndActiveTrueOrderByCreatedOnDesc(member.getWorkspaceId()).stream().filter(i->Objects.equals(i.getMembershipId(),member.getId())).findFirst().ifPresent(i->{i.setStatus(status);invitations.save(i);});}
    private void refreshAssignments(CustomerWorkspace workspace,WorkspaceMembership member){deactivateAssignments(member);List<Long>ids=member.getScopeType()==TeamScopeType.ENTIRE_WORKSPACE?properties.findAllByCreatedByAndActiveTrue(workspace.getOwnerUserId()).stream().map(Property::getId).toList():readIds(member.getResourceIdsJson());ids.forEach(id->addAssignment(member,id));}
    private void addAssignment(WorkspaceMembership member,long propertyId){String role=member.getMembershipRole().platformRole().name();if(propertyManagers.findByUserIdAndPropertyIdAndRoleNameAndActiveTrue(member.getUserId(),propertyId,role).isPresent())return;PropertyManager pm=new PropertyManager();pm.setInviteId(-member.getId());pm.setUserId(member.getUserId());pm.setPropertyId(propertyId);pm.setRoleName(role);pm.setActive(true);propertyManagers.save(pm);audit.createAuditLog(pm,"workspace_scope_grant");}
    private void deactivateAssignments(WorkspaceMembership member){propertyManagers.findByInviteIdAndActiveTrue(-member.getId()).forEach(pm->{pm.setActive(false);propertyManagers.save(pm);audit.createAuditLog(pm,"workspace_scope_revoke");});}

    private AccessContext accessContext(){Users user=users.getUserObject();if(user==null)throw invalid();PMSRole active=users.getActiveRole();try{TeamBusinessArea area=TeamBusinessArea.fromOwnerRole(active);CustomerWorkspace workspace=workspaces.findByOwnerUserIdAndBusinessAreaAndActiveTrue(user.getId(),area).orElseGet(()->createWorkspace(user,area));return new AccessContext(workspace,true,100);}catch(IllegalArgumentException ignored){if(active!=PMSRole.WORKSPACE_ADMIN)throw new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS);WorkspaceMembership member=memberships.findFirstByUserIdAndMembershipRoleAndStatusAndActiveTrue(user.getId(),TeamMembershipRole.WORKSPACE_ADMIN,TeamMembershipStatus.ACTIVE).orElseThrow(this::invalid);CustomerWorkspace workspace=workspaces.findById(member.getWorkspaceId()).filter(CustomerWorkspace::isActive).orElseThrow(this::invalid);return new AccessContext(workspace,false,member.getMembershipRole().privilegeLevel());}}
    private CustomerWorkspace createWorkspace(Users owner,TeamBusinessArea area){CustomerWorkspace w=new CustomerWorkspace();w.setOwnerUserId(owner.getId());w.setBusinessArea(area);w.setName((owner.getFullName()==null?"My":owner.getFullName())+" — "+area.displayName());w.setCreatedBy(owner.getId());w.setActive(true);w=workspaces.save(w);audit.createAuditLog(w,"workspace_create");return w;}
    private void requireCanGrant(AccessContext access,TeamMembershipRole role){if(!role.allowedFor(access.workspace().getBusinessArea()))throw new PMSCustomException(ResponseCode.INVALID_ROLE);if(!access.owner()&&access.privilegeLevel()<=role.privilegeLevel())throw new PMSCustomException(ResponseCode.INVALID_ROLE);}
    private void requireCanManage(AccessContext access,WorkspaceMembership target){if(target.getStatus()==TeamMembershipStatus.REVOKED)throw invalid();if(!access.owner()&&access.privilegeLevel()<=target.getMembershipRole().privilegeLevel())throw new PMSCustomException(ResponseCode.INVALID_ROLE);}
    private void enforceSeats(CustomerWorkspace workspace){long limit=seatLimit(workspace);long used=invitations.countByWorkspaceIdAndStatusInAndActiveTrue(workspace.getId(),List.of(TeamMembershipStatus.PENDING))+memberships.countByWorkspaceIdAndStatusInAndActiveTrue(workspace.getId(),OCCUPIED_MEMBER_STATUSES);if(limit>=0&&used>=limit)throw new PMSCustomException(ResponseCode.SUBSCRIPTION_LIMIT_EXCEEDED);}
    private long seatLimit(CustomerWorkspace workspace){return subscriptions.findTopByCreatedByAndRoleAndStatusAndActiveTrueOrderByStartAtDesc(workspace.getOwnerUserId(),workspace.getBusinessArea().ownerRole(),SubscriptionStatus.ACTIVE).flatMap(s->plans.findByCode(s.getPlanCode())).flatMap(p->quotas.findTopBySubscriptionPlanAndMetricKeyOrderByIdDesc(p,"TEAM_SEATS")).map(PlanQuota::getLimitValue).orElse(1L);}
    private List<Long> validateScope(CustomerWorkspace workspace,TeamScopeType type,List<Long>requested){if(type==TeamScopeType.ENTIRE_WORKSPACE)return List.of();List<Long>ids=requested==null?List.of():requested.stream().distinct().toList();if(ids.isEmpty())throw invalid();List<Property>owned=properties.findAllById(ids).stream().filter(p->p.isActive()&&Objects.equals(p.getCreatedBy(),workspace.getOwnerUserId())).toList();if(owned.size()!=ids.size())throw new PMSCustomException(ResponseCode.PROPERTY_FORBIDDEN_ACCESS);return ids;}
    private WorkspaceInvitation invitation(String raw){WorkspaceInvitation i=invitations.findByTokenHashAndActiveTrue(hash(raw)).orElseThrow(this::invalid);expire(i);return i;}
    private void expireInvitations(CustomerWorkspace w){invitations.findByWorkspaceIdAndActiveTrueOrderByCreatedOnDesc(w.getId()).forEach(this::expire);}
    private void expire(WorkspaceInvitation i){if(i.getStatus()==TeamMembershipStatus.PENDING&&LocalDateTime.now().isAfter(i.getExpiresAt())){i.setStatus(TeamMembershipStatus.EXPIRED);invitations.save(i);audit.createAuditLog(i,"workspace_invite_expire");}}
    private WorkspaceInvitation ownedInvitation(AccessContext a,long id){return invitations.findByIdAndWorkspaceIdAndActiveTrue(id,a.workspace().getId()).orElseThrow(this::invalid);}
    private WorkspaceMembership ownedMember(AccessContext a,long id){return memberships.findByIdAndWorkspaceIdAndActiveTrue(id,a.workspace().getId()).orElseThrow(this::invalid);}
    private void assignPlatformRole(long userId,PMSRole role){Role dbRole=roles.findByName(role.getName()).orElseThrow(()->new PMSCustomException(ResponseCode.INVALID_ROLE));if(userRoles.findByUserIdAndRoleId(userId,dbRole.getId())==0){UserRole ur=new UserRole(userId,dbRole.getId());userRoles.save(ur);audit.createAuditLog(ur,"assign_workspace_role");}}
    private void removePlatformRoleIfUnused(WorkspaceMembership member){if(memberships.existsByUserIdAndMembershipRoleAndStatusAndActiveTrue(member.getUserId(),member.getMembershipRole(),TeamMembershipStatus.ACTIVE))return;roles.findByName(member.getMembershipRole().platformRole().getName()).flatMap(role->userRoles.findFirstByUserIdAndRoleId(member.getUserId(),role.getId())).ifPresent(userRole->{userRoles.delete(userRole);audit.createAuditLog(userRole,"remove_workspace_role");});}
    private void send(WorkspaceInvitation i,CustomerWorkspace w,String raw){String link=formatInviteLink(config.getConfigByName(PMSConfigs.INVITE_LINK_URL).get().stringValue(),raw);String body=String.format(i18n.getLocalizedMessage(NotificationType.WORKSPACE_INVITE_EMAIL.getBody()),w.getName(),roleName(i.getRoleDefinitionId(),i.getMembershipRole()),link);notifications.sendNotification(new NotificationDTO(body,i.getRecipientEmail(),NotificationType.WORKSPACE_INVITE_EMAIL));}
    private TeamAccessModels.WorkspaceView view(AccessContext a){CustomerWorkspace w=a.workspace();List<TeamAccessModels.RoleOption>roleOptions=roleDefinitions.findByBusinessAreaAndActiveTrueOrderByDisplayName(w.getBusinessArea()).stream().filter(d->a.owner()||a.privilegeLevel()>d.getPermissionTemplate().privilegeLevel()).map(d->new TeamAccessModels.RoleOption(d.getId(),d.getCode(),d.getDisplayName(),d.getPermissionTemplate())).toList();List<IdNameDescDTO>resources=properties.findAllByCreatedByAndActiveTrue(w.getOwnerUserId()).stream().map(p->new IdNameDescDTO(p.getId(),p.getName(),p.getAddress())).toList();List<TeamAccessModels.InvitationView>inviteViews=invitations.findByWorkspaceIdAndActiveTrueOrderByCreatedOnDesc(w.getId()).stream().filter(i->i.getMembershipId()==null).map(this::invitationView).toList();List<TeamAccessModels.MemberView>memberViews=memberships.findByWorkspaceIdAndActiveTrueOrderByCreatedOnDesc(w.getId()).stream().map(this::memberView).toList();long used=inviteViews.stream().filter(i->i.status()==TeamMembershipStatus.PENDING).count()+memberViews.stream().filter(m->m.status()!=TeamMembershipStatus.REVOKED).count();return new TeamAccessModels.WorkspaceView(w.getId(),w.getName(),w.getBusinessArea(),a.owner(),seatLimit(w),used,roleOptions,resources,inviteViews,memberViews);}
    private TeamAccessModels.InvitationView invitationView(WorkspaceInvitation i){return new TeamAccessModels.InvitationView(i.getId(),i.getRecipientEmail(),i.getMembershipRole(),roleName(i.getRoleDefinitionId(),i.getMembershipRole()),i.getScopeType(),readIds(i.getResourceIdsJson()),i.getStatus(),i.getExpiresAt(),i.getResendCount());}
    private TeamAccessModels.MemberView memberView(WorkspaceMembership m){Users u=users.findById(m.getUserId()).orElse(null);return new TeamAccessModels.MemberView(m.getId(),m.getUserId(),m.getMemberEmail(),u==null?m.getMemberEmail():u.getFullName(),m.getMembershipRole(),roleName(m.getRoleDefinitionId(),m.getMembershipRole()),m.getScopeType(),readIds(m.getResourceIdsJson()),m.getStatus(),m.getAcceptedAt(),m.getActivatedAt());}
    private String roleName(Long definitionId,TeamMembershipRole fallback){return definitionId==null?fallback.displayName():roleDefinitions.findById(definitionId).map(TeamRoleDefinition::getDisplayName).orElse(fallback.displayName());}
    private LocalDateTime expiry(){long days=config.getConfigByName(PMSConfigs.INVITE_LINK_EXPIRY_DAYS).get().intValue();return LocalDateTime.now().plusDays(Math.max(1,Math.min(days,7)));}
    private RawToken token(){byte[]b=new byte[32];new SecureRandom().nextBytes(b);String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(b);return new RawToken(raw,hash(raw));}
    private String hash(String raw){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private String writeIds(List<Long>ids){try{return mapper.writeValueAsString(ids==null?List.of():ids);}catch(Exception e){throw invalid();}}
    private List<Long> readIds(String json){try{return json==null?List.of():mapper.readValue(json,new TypeReference<>(){});}catch(Exception e){return List.of();}}
    private void validateEmail(String email){try{InternetAddress a=new InternetAddress(email);a.validate();}catch(Exception e){throw new PMSCustomException(ResponseCode.INVALID_EMAIL);}}
    private void requireEmail(WorkspaceInvitation i,String email){if(email==null||!i.getRecipientEmail().equalsIgnoreCase(email.trim()))throw new PMSCustomException(ResponseCode.INVALID_USER_DETAILS);}
    private String normalizeEmail(String email){return email.trim().toLowerCase(Locale.ROOT);}
    private String mask(String email){int at=email.indexOf('@');return at<2?"***"+email.substring(Math.max(0,at)):email.substring(0,2)+"***"+email.substring(at);}
    private PMSCustomException invalid(){return new PMSCustomException(ResponseCode.INVALID_OR_EXPIRED_TOKEN);}
    private record RawToken(String raw,String hash){} private record AccessContext(CustomerWorkspace workspace,boolean owner,int privilegeLevel){}
}
