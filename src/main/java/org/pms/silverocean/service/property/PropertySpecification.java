package org.pms.silverocean.service.property;

import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.lease.wrappers.PMSLeaseMode;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;

public class PropertySpecification  extends CommonPropertySpecification {
    public static Specification<Property> searchProperty(Optional<String> searchParam,
                                                          Optional<PMSPropertyManagementMode> managementMode,
                                                          Optional<PMSLeaseMode> unitLeaseMode,
                                                          boolean activeOnly, Long userId, PMSRole activeRole,
                                                          Long workspaceMembershipId) {
        return Specification.anyOf(addressLike(searchParam), typeLike(searchParam), nameLike(searchParam))
                .and(managementModeEquals(managementMode))
                .and(hasActiveUnitMode(unitLeaseMode))
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

    /**
     * Unit commercial use is the authoritative business-area classifier. A
     * single property may contain rental, sale and homeowner units, so the
     * property's historical default management mode must not drive these
     * selectors.
     */
    private static Specification<Property> hasActiveUnitMode(Optional<PMSLeaseMode> mode) {
        return mode.<Specification<Property>>map(value -> (property, query, criteriaBuilder) -> {
                    var units = query.subquery(Long.class);
                    var unit = units.from(Unit.class);
                    units.select(unit.get("propertyId"));
                    units.where(
                            criteriaBuilder.equal(unit.get("propertyId"), property.get("id")),
                            criteriaBuilder.isTrue(unit.get("active")),
                            criteriaBuilder.equal(unit.get("leaseMode"), value.name())
                    );
                    return criteriaBuilder.exists(units);
                })
                .orElse(null);
    }
}
