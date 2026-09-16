package org.pms.silverocean.service.sp.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.pms.silverocean.database.pms.entities.ServiceCategory;
import org.pms.silverocean.service.sp.enums.DocumentType;

import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceCategoryDTO(long id, String name, String description, Set<DocumentType> requiredDocumentTypes, int requiredNumberOfReferees, boolean active) {
    public ServiceCategoryDTO(ServiceCategory c) {
        this(c.getId(), c.getName(), c.getDescription(), c.getRequiredDocumentTypes(), c.getRequiredNumberOfReferees(), c.isActive());
    }
}
