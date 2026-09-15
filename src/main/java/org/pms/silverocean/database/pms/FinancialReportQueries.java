package org.pms.silverocean.database.pms;

/** Financial reporting restrictions are applied by the database before row limits and totals. */
public final class FinancialReportQueries {
    private FinancialReportQueries() {}
    public static final String INVOICES = "SELECT i FROM PMSInvoice i WHERE i.active " +
            "AND i.createdOn >= :start AND i.createdOn < :end AND " +
            "((:privileged=true AND i.subscriptionPlanCode IS NOT NULL) OR " +
            "(:privileged=false AND (i.billedUserId=:userId OR i.payToUserId=:userId))) " +
            "AND (:restricted=false OR i.propertyId IN :propertyIds) ORDER BY i.createdOn DESC,i.id DESC";
    public static final String PAYMENTS = "SELECT DISTINCT p FROM PMSPayment p JOIN PMSInvoice i ON p.billReference=i.ref " +
            "WHERE i.active AND p.createdOn >= :start AND p.createdOn < :end AND " +
            "((:privileged=true AND i.subscriptionPlanCode IS NOT NULL) OR " +
            "(:privileged=false AND (i.billedUserId=:userId OR i.payToUserId=:userId))) " +
            "AND (:restricted=false OR i.propertyId IN :propertyIds) ORDER BY p.createdOn DESC,p.id DESC";
    public static final String STATEMENT = "SELECT l FROM FinancialLedgerLine l WHERE " +
            "l.createdOn >= :start AND l.createdOn < :end AND " +
            "((:privileged=true AND EXISTS (SELECT 1 FROM FinancialJournal j, PMSInvoice i " +
            "WHERE j.id=l.journalId AND j.sourceType='INVOICE' AND j.sourceId=CAST(i.id AS string) " +
            "AND i.subscriptionPlanCode IS NOT NULL)) OR " +
            "(:privileged=false AND (l.userId=:userId OR EXISTS (SELECT 1 FROM PropertyManager pm " +
            "WHERE pm.propertyId=l.propertyId AND pm.userId=:userId AND pm.active " +
            "AND pm.inviteId=:assignmentId AND pm.roleName=:roleName)))) " +
            "AND (:restricted=false OR l.propertyId IN :propertyIds) " +
            "ORDER BY l.createdOn DESC,l.journalId DESC,l.lineNumber";
    public static final String SALES = "SELECT DISTINCT s FROM SaleTransaction s JOIN Property p ON p.id=s.propertyId " +
            "WHERE s.active AND s.createdOn >= :start AND s.createdOn < :end AND " +
            "(:privileged=false AND (s.salesAgentUserId=:userId OR s.buyerUserId=:userId OR p.createdBy=:userId " +
            "OR EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=s.propertyId AND pm.userId=:userId " +
            "AND pm.active AND pm.inviteId=:assignmentId AND pm.roleName=:roleName))) " +
            "AND (:restricted=false OR s.propertyId IN :propertyIds) ORDER BY s.createdOn DESC,s.id DESC";
    public static final String ESTATE_CHARGES = "SELECT DISTINCT c FROM EstateServiceCharge c JOIN Property p ON p.id=c.propertyId " +
            "WHERE c.active AND c.createdOn >= :start AND c.createdOn < :end AND " +
            "(:privileged=false AND (c.homeownerUserId=:userId OR p.createdBy=:userId OR EXISTS " +
            "(SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=c.propertyId AND pm.userId=:userId AND pm.active " +
            "AND pm.inviteId=:assignmentId AND pm.roleName=:roleName))) " +
            "AND (:restricted=false OR c.propertyId IN :propertyIds) ORDER BY c.dueDate DESC,c.id DESC";
}
