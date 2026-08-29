package org.pms.silverocean.service.config;

import org.pms.silverocean.service.config.enums.PMSConfigs;

public record EditConfigDTO(String value, PMSConfigs config) {
}
