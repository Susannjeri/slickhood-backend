package org.pms.silverocean.controller.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collection;
import java.util.List;

@Getter @Setter @NoArgsConstructor @JsonInclude(value = JsonInclude.Include.NON_NULL)
public class ResponseDTO {
    private boolean success;
    private String code;
    private String description;
    private List<?> data;
    private Integer totalPages;
    private Long totalElements;
    private Integer size;

    public ResponseDTO(boolean success, String code, String description, Object data) {
        this.success = success;
        this.code = code;
        this.description = description;
        this.data = data instanceof Collection ? List.copyOf((Collection<?>) data) : List.of(data);
    }

    public ResponseDTO(boolean success, String code, String description, List<?> data, int totalPages, long totalElements, Integer size) {
        this.success = success;
        this.code = code;
        this.description = description;
        this.data = data;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
        this.size = size;
    }

    public ResponseDTO(boolean success, String code, String description) {
        this.success = success;
        this.code = code;
        this.description = description;
        this.data = List.of();
    }
}
