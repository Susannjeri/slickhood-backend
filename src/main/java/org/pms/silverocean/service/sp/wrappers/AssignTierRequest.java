package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.NotBlank;

public record AssignTierRequest(@NotBlank @jakarta.validation.constraints.Size(max=160) String tier) {}
