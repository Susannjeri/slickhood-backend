package org.pms.silverocean.service.payment.invoice;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PropertyManager;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;

public class InvoiceSpecifications {

    public static Specification<PMSInvoice> searchInvoiceForSuperAdminView(Long tenantId, Long propertyId, Long unitId, Long landlordId) {
        return Specification.anyOf(billedUserIdEquals(Optional.ofNullable(tenantId)), propertyIdEquals(Optional.ofNullable(propertyId)), unitIdEquals(Optional.ofNullable(unitId)), landlordIdEquals(Optional.ofNullable(landlordId)));
    }

    public static Specification<PMSInvoice> searchInvoiceForOwnerAndTenantView(Long userId, Long propertyId, Long unitId) {
        return Specification.anyOf(searchInvoiceForTenantView(userId, propertyId, unitId), searchInvoiceForLandlordView(propertyId, unitId, userId));
    }

    private static Specification<PMSInvoice> searchInvoiceForLandlordView(Long propertyId, Long unitId, Long landlordId) {
        return Specification.anyOf(propertyIdEquals(Optional.ofNullable(propertyId)), unitIdEquals(Optional.ofNullable(unitId))).and(ownerIdEquals(Optional.ofNullable(landlordId)));
    }

    private static Specification<PMSInvoice> searchInvoiceForTenantView(Long tenantId, Long propertyId, Long unitId) {
        return Specification.anyOf(propertyIdEquals(Optional.ofNullable(propertyId)), unitIdEquals(Optional.ofNullable(unitId))).and(billedUserIdEquals(Optional.ofNullable(tenantId)));
    }

    private static Specification<PMSInvoice> billedUserIdEquals(Optional<Long> tenantUserId) {
        return tenantUserId.<Specification<PMSInvoice>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.equal(document.get("billedUserId"), s))
                .orElse(null);
    }

    private static Specification<PMSInvoice> propertyIdEquals(Optional<Long> propertyId) {
        return propertyId.<Specification<PMSInvoice>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.equal(document.get("propertyId"), s))
                .orElse(null);
    }

    private static Specification<PMSInvoice> unitIdEquals(Optional<Long> unitId) {
        return unitId.<Specification<PMSInvoice>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.equal(document.get("unitId"), s))
                .orElse(null);
    }

    private static Specification<PMSInvoice> landlordIdEquals(Optional<Long> landlordId) {
        return landlordId.<Specification<PMSInvoice>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.equal(document.get("payToUserId"), s))
                .orElse(null);
    }

    private static Specification<PMSInvoice> ownerIdEquals(Optional<Long> landlordId) {
        return landlordId.<Specification<PMSInvoice>>map(id -> (root, query, cb) -> {
            // Condition 1: Check if payToUserId matches the landlordId directly
            Predicate directLandlordPredicate = cb.equal(root.get("payToUserId"), id);

            // Condition 2: Join to PropertyManager table and check their userId
            // Assuming PMSInvoice has a field 'propertyId' that links to PropertyManager
            // and PropertyManager is an entity related to PMSInvoice.
            Root<PropertyManager> properyManagerRoot = query.from(PropertyManager.class);
            Predicate propertyManagerJoin = cb.equal(root.get("propertyId"), properyManagerRoot.get("propertyId"));

            Predicate isTargetUser = cb.equal(properyManagerRoot.get("userId"), id);
            Predicate isCorrectRole = cb.equal(properyManagerRoot.get("roleName"), PMSRole.PROPERTY_MANAGER.name());

            Predicate propertyManagerPredicate = cb.and(propertyManagerJoin, isTargetUser, isCorrectRole);

            query.distinct(true);

            // Return records matching EITHER condition (OR logic)
            return cb.or(directLandlordPredicate, propertyManagerPredicate);
        }).orElse(null);
    }

}
