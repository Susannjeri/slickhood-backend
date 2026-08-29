package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.NotBlank;

public record AddRefereeRequest(@NotBlank String name, @NotBlank String contact) {}
