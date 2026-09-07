package org.pms.silverocean.service.config;

import org.pms.silverocean.service.config.enums.PMSConfigs;

public record EditConfigDTO(@jakarta.validation.constraints.NotBlank String value,
                            @jakarta.validation.constraints.NotNull PMSConfigs config) {
}
