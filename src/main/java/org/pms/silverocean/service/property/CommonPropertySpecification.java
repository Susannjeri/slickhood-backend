package org.pms.silverocean.service.property;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.pms.silverocean.database.pms.entities.PropertyManager;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.UnitTenant;
import org.pms.silverocean.database.pms.entities.PropertyOwnership;
import org.pms.silverocean.database.pms.entities.SaleTransaction;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.data.jpa.domain.Specification;

public class CommonPropertySpecification {

    public static <T> Specification<T> activeTrue(boolean active) {
        return  (document, query, criteriaBuilder) ->
                criteriaBuilder.equal(document.get("active"), active);

    }

    public static <T> Specification<T> accessibleForActiveRole(Long userId, PMSRole activeRole) {
        return (root, query, cb) -> {
            if (userId == null || activeRole == PMSRole.SUPER_ADMIN) return null;
            query.distinct(true);

            // 1. Landlord
            Predicate createdByPredicate =
                    cb.equal(root.get("createdBy"), userId);


            // 2. Manager Staff via subquery (PropertyManager)
            Subquery<Long> staffSubquery = query.subquery(Long.class);
            Root<PropertyManager> managerRoot = staffSubquery.from(PropertyManager.class);
            staffSubquery
                    .select(managerRoot.get("propertyId"))
                    .where(
                            cb.and(
                                    cb.equal(managerRoot.get("userId"), userId),
                                    cb.equal(managerRoot.get("roleName"), activeRole.name()),
                                    cb.isTrue(managerRoot.get("active"))
                            )
                    );

            // The final predicate checks if the Property.id is in the list of managed property IDs
            Expression<Long> entityId =  root.getJavaType().equals(Unit.class) ? root.get("propertyId") : root.get("id");
            Predicate staffPredicate = entityId.in(staffSubquery);

            // 3. Tenant via subquery (Unit → UnitTenant)
            Subquery<Long> tenantSubquery = query.subquery(Long.class);

            Root<Unit> unitRoot = tenantSubquery.from(Unit.class);


            Root<UnitTenant> unitTenantRoot = tenantSubquery.from(UnitTenant.class);
            Predicate userIsTenantCondition = cb.equal(unitTenantRoot.get("userId"), userId);
            Predicate activeTenantCondition = cb.isTrue(unitTenantRoot.get("active"));
            Predicate unitIsOccupiedBySameTenantCondition = cb.equal(unitRoot.get("id"), unitTenantRoot.get("unitId"));
            entityId =  root.getJavaType().equals(Unit.class) ?  unitRoot.get("id") : unitRoot.get("propertyId");
            tenantSubquery
                    .select(entityId)
                    .where(
                            cb.and(
                                    userIsTenantCondition,
                                    activeTenantCondition,
                                    unitIsOccupiedBySameTenantCondition
                            )
                    );

            Predicate tenantPredicate = root.get("id").in(tenantSubquery);

            Subquery<Long> homeownerSubquery = query.subquery(Long.class);
            Root<PropertyOwnership> ownershipRoot = homeownerSubquery.from(PropertyOwnership.class);
            Expression<Long> ownershipEntityId = root.getJavaType().equals(Unit.class)
                    ? ownershipRoot.get("unitId") : ownershipRoot.get("propertyId");
            homeownerSubquery.select(ownershipEntityId).where(cb.and(
                    cb.equal(ownershipRoot.get("homeownerUserId"), userId), cb.isTrue(ownershipRoot.get("active"))));
            Predicate homeownerPredicate = root.get("id").in(homeownerSubquery);

            Subquery<Long> buyerSubquery = query.subquery(Long.class);
            Root<SaleTransaction> saleRoot = buyerSubquery.from(SaleTransaction.class);
            Expression<Long> saleEntityId = root.getJavaType().equals(Unit.class)
                    ? saleRoot.get("unitId") : saleRoot.get("propertyId");
            buyerSubquery.select(saleEntityId).where(cb.and(
                    cb.equal(saleRoot.get("buyerUserId"), userId), cb.isTrue(saleRoot.get("active"))));
            Predicate buyerPredicate = root.get("id").in(buyerSubquery);
            return switch (activeRole) {
                case LANDLORD, ESTATE_MANAGER, SALES_AGENT -> createdByPredicate;
                case TENANT -> tenantPredicate;
                case HOMEOWNER -> homeownerPredicate;
                case BUYER -> buyerPredicate;
                case PROPERTY_MANAGER, WORKSPACE_ADMIN, PROPERTY_ACCOUNTANT, LEASING_OFFICER,
                     ESTATE_OPERATIONS_MANAGER, SECURITY_SUPERVISOR, SALES_COORDINATOR,
                     LISTING_AGENT, WORKSPACE_VIEWER, GUARD -> staffPredicate;
                default -> cb.disjunction();
            };
        };
    }
}
