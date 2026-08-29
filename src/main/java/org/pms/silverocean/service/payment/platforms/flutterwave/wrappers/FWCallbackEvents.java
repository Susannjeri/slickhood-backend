package org.pms.silverocean.service.payment.platforms.flutterwave.wrappers;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public enum FWCallbackEvents {
    CHARGE_COMPLETED("charge.completed"),
    TRANSFER_COMPLETED("transfer.completed");

    private final String name;
    private static final Map<String, FWCallbackEvents> events = new HashMap<>();

    static {
        for (FWCallbackEvents event : FWCallbackEvents.values()) {
            events.put(event.name, event);
        }
    }

    FWCallbackEvents(String name) {
        this.name = name;
    }

    public static FWCallbackEvents getEvent(String name) {
        if (!events.containsKey(name)) {
            throw new IllegalArgumentException(name + " is not a valid event name");
        }
        return events.get(name);
    }
}
