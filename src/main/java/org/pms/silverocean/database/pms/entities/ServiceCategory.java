package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import org.pms.silverocean.service.sp.enums.DocumentType;

import java.util.Set;

@Entity
@Table(name = "pms_sp_category", indexes = {
        @Index(name = "idx_sp_category_name", columnList = "name")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ServiceCategory extends BaseCreatorEntity implements Auditable {
    private String name;
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pms_sp_category_doc_types", joinColumns = @JoinColumn(name = "categoryId"))
    @Enumerated(EnumType.STRING)
    @Column(name = "documentType")
    private Set<DocumentType> requiredDocumentTypes;

    private int requiredNumberOfReferees = 0;

    @Override
    public String toAuditJSON() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"name\":\"" + name + "\"," +
                "\"description\":\"" + description + "\"" +
                "\"requiredNumberOfReferees\":\"" + requiredNumberOfReferees + "\"" +
                "}";
    }
}
