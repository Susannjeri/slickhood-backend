package org.pms.silverocean.service.property;

import jakarta.persistence.criteria.JoinType;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.service.lease.wrappers.PMSLeaseMode;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class UnitSpecification extends CommonPropertySpecification {
    public static List<Specification<Unit>> createGetUnitSpecification(Optional<String> ref, Optional<Long> propertyId, Long userId, PMSRole activeRole, Optional<PMSLeaseMode> leaseMode) {
        return Stream.<Specification<Unit>>of(
                        fetchPropertyWithUnit(),
                        ref.isPresent() ? refLike(ref) : null,
                        propertyId.isPresent() ? propertyIdEquals(propertyId) : null,
                        leaseMode.isPresent() ? leaseModeEquals(leaseMode) : null,
                        accessibleForActiveRole(userId, activeRole),
                        activeTrue(true)
                )
                .filter(Objects::nonNull)
                .toList();

    }

    private static Specification<Unit> fetchPropertyWithUnit() {
        return (root, query, criteriaBuilder) -> {
            // Guard clause: Do not apply fetch join if this is a count query (for pagination)
            Class<?> clazz = query.getResultType();
            if (clazz != Long.class && clazz != long.class) {
                root.fetch("property", JoinType.LEFT); // Matches the field name in your Unit entity
            }
            return null; // Return null because this modifies the FETCH clause, not the WHERE clause
        };
    }

    private static Specification<Unit> refLike(Optional<String> name) {
        return name.<Specification<Unit>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.like(document.get("ref"), "%" + s.toLowerCase() + "%"))
                .orElse(null);

    }

    private static Specification<Unit> leaseModeEquals(Optional<PMSLeaseMode> leaseMode) {
        return leaseMode.<Specification<Unit>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.equal(document.get("leaseMode"),  s.name()))
                .orElse(null);

    }


    private static Specification<Unit> propertyIdEquals(Optional<Long> id) {
        return id.<Specification<Unit>>map(propertyId -> (document, query, criteriaBuilder) ->
                criteriaBuilder.equal(document.get("propertyId"), propertyId)).orElse(null);

    }
}
