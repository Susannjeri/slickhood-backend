package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.NotBlank;
import org.pms.silverocean.service.sp.enums.ComplaintResolutionAction;

public record ReviewComplaintRequest(
    ComplaintResolutionAction action,
    @NotBlank String adminNotes,
    String resolution
) {}
