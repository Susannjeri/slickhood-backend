package org.pms.silverocean.service.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountPropertyDTO(
        String key,
        String label,
        String description,
        String value,
        boolean encrypted,
        boolean displayField
) {}
