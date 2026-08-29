package org.pms.silverocean.service.notification.sms.africastalking.wrappers;

import lombok.Getter;

@Getter
public enum ATSMSStatus {
    Sent("The message has successfully been sent by our network."),
    Submitted("The message has successfully been submitted to the MSP (Mobile Service Provider)."),
    Buffered("The message has been queued by the MSP."),
    Rejected("The message has been rejected by the MSP. This is a final status."),
    Success("The message has successfully been delivered to the receiver’s handset. This is a final status."),
    Failed("The message could not be delivered to the receiver’s handset. This is a final status."),
    AbsentSubscriber("The message was not delivered since user’s SIM card was not reachable on the network either phone was off or in a place with no network coverage."),
    Expired("The message was discarded by the telco as it was flagged, either some content in the message or the sender ID use was flagged on their firewall.");

    private final String description;

    ATSMSStatus(String description) {
        this.description = description;
    }
}
