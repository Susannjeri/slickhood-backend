package org.pms.silverocean.service.notification.common;

import lombok.Getter;

@Getter
public enum NotificationType {
    OTP_SMS("sms.otp.message", false, NotificationChannel.SMS),
    EMAIL_OTP("email.otp.subject", "email.otp.body", false, NotificationChannel.EMAIL),

    ADD_UNITS_REMINDER_EMAIL("add.units.reminder.email.subject", "add.units.reminder.email.body", true, NotificationChannel.EMAIL),
    PAYMENT_SUCCESS_SMS("sms.payment.success.message", true, NotificationChannel.SMS),
    MPESA_VALIDATION_FAILED_SMS("sms.mpesa.validation.failed.message", false, NotificationChannel.SMS),
    CREATE_SIMILAR_UNITS_JOB_COMPLETION_EMAIL("similar.units.job.subject", "similar.units.job.body", false, NotificationChannel.EMAIL),
    TENANT_INVITE_SMS("sms.tenant.invite", false, NotificationChannel.SMS),
    TENANT_INVITE_EMAIL("email.tenant.invite.subject", "email.tenant.invite.body", false,  NotificationChannel.EMAIL),
    USER_SETTING_VERIFIED_EMAIL("email.user.setting.verified.subject", "email.user.setting.verified.body", false, NotificationChannel.EMAIL),
    PROPERTY_MANAGER_INVITE_SMS("sms.property.manager.invite", false, NotificationChannel.SMS),
    PROPERTY_MANAGER_INVITE_EMAIL("email.property.manager.invite.subject", "email.property.manager.invite.body", false, NotificationChannel.EMAIL),
    GUARD_INVITE_SMS("sms.guard.invite", false, NotificationChannel.SMS),
    GUARD_INVITE_EMAIL("email.guard.invite.subject", "email.guard.invite.body", false, NotificationChannel.EMAIL),
    USER_INVITE_SMS("sms.user.invite", false, NotificationChannel.SMS),
    USER_INVITE_EMAIL("email.user.invite.subject", "email.user.invite.body", false, NotificationChannel.EMAIL),
    FINANCE_INVITE_SMS("sms.finance.invite", false, NotificationChannel.SMS),
    FINANCE_INVITE_EMAIL("email.finance.invite.subject", "email.finance.invite.body", false, NotificationChannel.EMAIL),
    ASSET_MANAGER_INVITE_SMS("sms.asset.manager.invite", false, NotificationChannel.SMS),
    ASSET_MANAGER_INVITE_EMAIL("email.asset.manager.invite.subject", "email.asset.manager.invite.body", false, NotificationChannel.EMAIL),
    LEASE_MESSAGE_EMAIL("email.lease.message.subject", "email.lease.message.body", false, NotificationChannel.EMAIL),
    INVOICE_EMAIL("email.invoice.subject", "email.invoice.body", false, NotificationChannel.EMAIL),
    VISITOR_BOOKING_CONFIRMATION_SMS("sms.visitor.booking.confirmation", false, NotificationChannel.SMS),
    VISITOR_BOOKING_CONFIRMATION_EMAIL("email.visitor.booking.confirmation.subject","email.visitor.booking.confirmation.body", false, NotificationChannel.EMAIL),
    VISITOR_ARRIVAL_SMS("sms.visitor.arrival", false, NotificationChannel.SMS),
    VISITOR_ARRIVAL_EMAIL("email.visitor.arrival.subject","email.visitor.arrival.body", false, NotificationChannel.EMAIL),
    VISITOR_DEPARTURE_SMS("sms.visitor.departure", false, NotificationChannel.SMS),
    VISITOR_DEPARTURE_EMAIL("email.visitor.departure.subject","email.visitor.departure.body", false, NotificationChannel.EMAIL),

    ACCOUNT_VERIFICATION_REQUEST_EMAIL("email.account.verification.request.subject","email.account.verification.request.body", true, NotificationChannel.EMAIL),
    ACCOUNT_VERIFICATION_FAILED_EMAIL("email.account.verification.failed.subject","email.account.verification.failed.body", true, NotificationChannel.EMAIL),
    ACCOUNT_VERIFICATION_SUCCESS_EMAIL("email.account.verification.success.subject","email.account.verification.success.body", true, NotificationChannel.EMAIL),
    SUBSCRIPTION_SALES_REQUEST_EMAIL("email.subscription.sales.request.subject", "email.subscription.sales.request.body", true, NotificationChannel.EMAIL),
    SP_SERVICE_APPROVED_EMAIL("email.sp.service.approved.subject", "email.sp.service.approved.body", false, NotificationChannel.EMAIL),
    SP_SERVICE_APPROVED_SMS("sms.sp.service.approved", false, NotificationChannel.SMS),
    SP_SERVICE_REJECTED_EMAIL("email.sp.service.rejected.subject", "email.sp.service.rejected.body", false, NotificationChannel.EMAIL),
    SP_SERVICE_REJECTED_SMS("sms.sp.service.rejected", false, NotificationChannel.SMS),
    SP_SERVICE_SUSPENDED_EMAIL("email.sp.service.suspended.subject", "email.sp.service.suspended.body", false, NotificationChannel.EMAIL),
    SP_BOOKING_CONFIRMED_EMAIL("email.sp.booking.confirmed.subject", "email.sp.booking.confirmed.body", false, NotificationChannel.EMAIL),
    SP_BOOKING_CONFIRMED_SMS("sms.sp.booking.confirmed", false, NotificationChannel.SMS),
    SP_BOOKING_COMPLETED_EMAIL("email.sp.booking.completed.subject", "email.sp.booking.completed.body", false, NotificationChannel.EMAIL),
    SP_BOOKING_CANCELLED_EMAIL("email.sp.booking.cancelled.subject", "email.sp.booking.cancelled.body", false, NotificationChannel.EMAIL),
    SP_COMPLAINT_RECEIVED_EMAIL("email.sp.complaint.received.subject", "email.sp.complaint.received.body", false, NotificationChannel.EMAIL),
    SP_COMPLAINT_RESOLVED_EMAIL("email.sp.complaint.resolved.subject", "email.sp.complaint.resolved.body", false, NotificationChannel.EMAIL),
    SP_TRUSTED_LABEL_EMAIL("email.sp.trusted.label.subject", "email.sp.trusted.label.body", false, NotificationChannel.EMAIL),
    ;


    private final String subject;
    private final String body;
    private final boolean retry;
    private final NotificationChannel channel;

    NotificationType(String subject, String body, boolean retry, NotificationChannel channel) {
        this.subject = subject;
        this.body = body;
        this.retry = retry;
        this.channel = channel;
    }

    NotificationType(String body, boolean retry, NotificationChannel channel) {
        this.subject = null;
        this.body = body;
        this.retry = retry;
        this.channel = channel;
    }
}
