package org.pms.silverocean.service.sp.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.pms.silverocean.database.pms.entities.Referee;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RefereeDTO(long id, long profileId, String name, String contact, String verificationStatus) {
    public RefereeDTO(Referee r) {
        this(r.getId(), r.getProfileId(), r.getName(), r.getContact(), r.getVerificationStatus());
    }
}
