package org.pms.silverocean.service.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IdNameDescDTO(long id, String name, String description) {
    public IdNameDescDTO(long id, String name) {
        this(id, name, null);
    }
}
