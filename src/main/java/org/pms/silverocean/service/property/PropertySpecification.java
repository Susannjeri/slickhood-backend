package org.pms.silverocean.service.property;

import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;

public class PropertySpecification  extends CommonPropertySpecification {
    public static Specification<Property> searchProperty(Optional<String> searchParam, boolean activeOnly, Long userId, PMSRole activeRole) {
        return Specification.anyOf(addressLike(searchParam), typeLike(searchParam), nameLike(searchParam)).and(accessibleForActiveRole(userId, activeRole)).and(activeTrue(activeOnly));
    }

    private static Specification<Property> nameLike(Optional<String> name) {
        return name.<Specification<Property>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.like(document.get("name"), "%" + s.toLowerCase() + "%"))
                .orElse(null);

    }

    private static Specification<Property> addressLike(Optional<String> address) {
        return address.<Specification<Property>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.like(document.get("address"), "%" + s.toLowerCase() + "%"))
                .orElse(null);

    }

    private static Specification<Property> typeLike(Optional<String> type) {
        return type.<Specification<Property>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.like(document.get("type"), "%" + s.toLowerCase() + "%"))
                .orElse(null);

    }
}
