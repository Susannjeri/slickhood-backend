package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.NotBlank;

public record AddRefereeRequest(@NotBlank @jakarta.validation.constraints.Size(max=150) String name, @NotBlank @jakarta.validation.constraints.Size(max=200) String contact) {}
