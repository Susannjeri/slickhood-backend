package org.pms.silverocean.service.notification.sms.africastalking.wrappers;

import lombok.Getter;

@Getter
public enum ATSMSFailureReason {
    InsufficientCredit("This occurs when the subscriber doesn’t have enough airtime for a premium subscription service/message"),
    InvalidLinkId("This occurs when a message is sent with an invalid linkId for an onDemand service"),
    UserIsInactive("This occurs when the subscriber is inactive or the account deactivated by the MSP (Mobile Service Provider)"),
    UserInBlackList("This occurs if the user has been blacklisted not to receive messages from a paricular service (shortcode or keyword)"),
    UserAccountSuspended("This occurs when the mobile subscriber has been suspended by the MSP."),
    NotNetworkSubcriber("This occurs when the message is passed to an MSP where the subscriber doesn’t belong."),
    UserNotSubscribedToProduct("This occurs when the message from a subscription product is sent to a phone number that has not subscribed to the product."),
    UserDoesNotExist(" This occurs when the message is sent to a non-existent mobile number."),
    DeliveryFailure("This occurs when message delivery fails for any reason not listed above or where the MSP didn’t provide a delivery failure reason."),
    DoNotDisturbRejection("Note: This only applies to Nigeria. When attempting to send an SMS message with a promotional sender ID outside the allowed time window(8pm-8am), the API will return an HTTP 409 status code, indicating a conflict. ");

    private final String description;
    ATSMSFailureReason(String description) {
        this.description = description;
    }
}
