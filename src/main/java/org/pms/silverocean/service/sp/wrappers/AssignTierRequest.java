package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.NotBlank;

public record AssignTierRequest(@NotBlank String tier) {}
