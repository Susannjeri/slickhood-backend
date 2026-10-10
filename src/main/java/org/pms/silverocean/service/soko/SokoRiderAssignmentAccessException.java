package org.pms.silverocean.service.soko;

import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.PMSCustomException;

/** Uniform public-bearer failure; callers cannot distinguish missing, expired or revoked links. */
public final class SokoRiderAssignmentAccessException extends PMSCustomException {
    public SokoRiderAssignmentAccessException() {
        super(ResponseCode.INVALID_OR_EXPIRED_TOKEN);
    }
}
