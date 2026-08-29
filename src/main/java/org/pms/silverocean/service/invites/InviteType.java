package org.pms.silverocean.service.invites;

import lombok.Getter;
import org.pms.silverocean.service.notification.common.NotificationType;

@Getter
public enum InviteType {
    TENANT(false, NotificationType.TENANT_INVITE_SMS, NotificationType.TENANT_INVITE_EMAIL, "tenant.invite.type"),
    PROPERTY_MANAGER(true, NotificationType.PROPERTY_MANAGER_INVITE_SMS, NotificationType.PROPERTY_MANAGER_INVITE_EMAIL, "property.manager.invite.type"),
    GUARD(true, NotificationType.GUARD_INVITE_SMS, NotificationType.GUARD_INVITE_EMAIL, "guard.invite.type"),
    ASSET_PORTFOLIO_MANAGER(true, NotificationType.ASSET_MANAGER_INVITE_SMS, NotificationType.ASSET_MANAGER_INVITE_EMAIL, "asset.portfolio.manager.invite.type"),
    FINANCE(true, NotificationType.ASSET_MANAGER_INVITE_SMS, NotificationType.ASSET_MANAGER_INVITE_EMAIL, "finance.manager.invite.type"),
    ESTATE_MANAGER(true, NotificationType.USER_INVITE_SMS, NotificationType.USER_INVITE_EMAIL, "estate.manager.invite.type"),
    HOMEOWNER(false, NotificationType.USER_INVITE_SMS, NotificationType.USER_INVITE_EMAIL, "homeowner.invite.type"),
    SALES_AGENT(true, NotificationType.USER_INVITE_SMS, NotificationType.USER_INVITE_EMAIL, "sales.agent.invite.type"),
    BUYER(false, NotificationType.USER_INVITE_SMS, NotificationType.USER_INVITE_EMAIL, "buyer.invite.type"),
    USER(false, NotificationType.USER_INVITE_SMS, NotificationType.USER_INVITE_EMAIL, "user.invite.type"),;

    private final boolean expiresAfterUse;
    private final NotificationType inviteSMS;
    private final NotificationType inviteEmail;
    private final String name;
    InviteType(boolean expiresAfterUse, NotificationType inviteSMS, NotificationType inviteEmail, String name) {
        this.expiresAfterUse = expiresAfterUse;
        this.inviteSMS = inviteSMS;
        this.inviteEmail = inviteEmail;
        this.name = name;
    }
}
