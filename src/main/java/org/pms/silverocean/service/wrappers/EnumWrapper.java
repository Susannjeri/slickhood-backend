package org.pms.silverocean.service.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnumWrapper(String id, String name, String description) {
}
