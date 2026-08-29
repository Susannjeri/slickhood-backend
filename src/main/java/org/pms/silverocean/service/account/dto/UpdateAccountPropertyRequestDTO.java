package org.pms.silverocean.service.account.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAccountPropertyRequestDTO(
        @NotBlank String key,
        @NotBlank String value
) {}
