package org.pms.silverocean.service.property;

import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;

public class PropertySpecification  extends CommonPropertySpecification {
    public static Specification<Property> searchProperty(Optional<String> searchParam,
                                                          Optional<PMSPropertyManagementMode> managementMode,
                                                          boolean activeOnly, Long userId, PMSRole activeRole,
                                                          Long workspaceMembershipId) {
        return Specification.anyOf(addressLike(searchParam), typeLike(searchParam), nameLike(searchParam))
                .and(managementModeEquals(managementMode))
                .and(accessibleForActiveRole(userId, activeRole, workspaceMembershipId)).and(activeTrue(activeOnly));
    }

    private static Specification<Property> nameLike(Optional<String> name) {
        return name.<Specification<Property>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.like(criteriaBuilder.lower(document.get("name")), "%" + s.trim().toLowerCase() + "%"))
                .orElse(null);

    }

    private static Specification<Property> addressLike(Optional<String> address) {
        return address.<Specification<Property>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.like(criteriaBuilder.lower(document.get("address")), "%" + s.trim().toLowerCase() + "%"))
                .orElse(null);

    }

    private static Specification<Property> typeLike(Optional<String> type) {
        return type.<Specification<Property>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.like(criteriaBuilder.lower(document.get("type")), "%" + s.trim().toLowerCase() + "%"))
                .orElse(null);

    }

    private static Specification<Property> managementModeEquals(Optional<PMSPropertyManagementMode> mode) {
        return mode.<Specification<Property>>map(value -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.equal(document.get("managementMode"), value))
                .orElse(null);
    }
}
