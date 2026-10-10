package org.pms.silverocean.service.soko;

import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.PMSCustomException;

/** Stable signal that the single-use handover code exhausted its attempt limit. */
public final class SokoDeliveryCodeLockedException extends PMSCustomException {
    public SokoDeliveryCodeLockedException() {
        super(ResponseCode.FORBIDDEN_ACCESS,
                "Delivery verification is locked after too many incorrect codes. Contact the shop or support.");
    }
}
