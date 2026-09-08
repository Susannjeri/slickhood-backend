package org.pms.silverocean.service.payment.invoice;

import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.springframework.data.jpa.domain.Specification;

public class InvoiceSpecifications {

    private InvoiceSpecifications() {
    }

    /**
     * Platform administrators may inspect SlickHood subscription invoices only.
     * Customer rental, estate, sales and marketplace invoices remain private to
     * their payer and recipient even when the caller is a Super Admin.
     */
    public static Specification<PMSInvoice> searchPlatformInvoices(Long tenantId) {
        return searchPlatformInvoices(tenantId, null);
    }

    public static Specification<PMSInvoice> searchPlatformInvoices(Long tenantId, Long invoiceId) {
        return active()
                .and((root, query, cb) -> cb.isNotNull(root.get("subscriptionPlanCode")))
                .and(equalWhenPresent("billedUserId", tenantId))
                .and(equalWhenPresent("id", invoiceId));
    }

    /**
     * An operational invoice is visible only to its billed customer and exact
     * receiving account owner. Property membership and platform roles must not
     * silently widen access to another customer's financial records.
     */
    public static Specification<PMSInvoice> searchParticipantInvoices(long userId, Long propertyId, Long unitId) {
        return searchParticipantInvoices(userId, propertyId, unitId, null);
    }

    public static Specification<PMSInvoice> searchParticipantInvoices(long userId, Long propertyId, Long unitId, Long invoiceId) {
        Specification<PMSInvoice> participant = (root, query, cb) -> cb.or(
                cb.equal(root.get("billedUserId"), userId),
                cb.equal(root.get("payToUserId"), userId));
        return active()
                .and(participant)
                .and(equalWhenPresent("propertyId", propertyId))
                .and(equalWhenPresent("unitId", unitId))
                .and(equalWhenPresent("id", invoiceId));
    }

    private static Specification<PMSInvoice> active() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    private static Specification<PMSInvoice> equalWhenPresent(String field, Long value) {
        return value == null ? Specification.unrestricted()
                : (root, query, cb) -> cb.equal(root.get(field), value);
    }
}
