package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.pms.silverocean.service.sp.enums.ComplaintResolutionAction;

public record ReviewComplaintRequest(
    @NotNull ComplaintResolutionAction action,
    @NotBlank @Size(max = 2000) String adminNotes,
    @Size(max = 2000) String resolution
) {}
