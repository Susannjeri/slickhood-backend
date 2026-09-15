package org.pms.silverocean.service.reports;

import java.util.List;

/** Stable report/export headers, including periods with no records. */
final class ReportColumns {
    private ReportColumns() {}

    static List<String> forCode(String code) {
        return switch (code) {
            case "INVOICE_COLLECTIONS" -> List.of("Reference", "Type", "Property", "Unit", "Amount", "Currency", "Collected", "Outstanding", "Due date", "Status", "Created");
            case "PAYMENT_RECONCILIATION" -> List.of("Reference", "Channel", "Category", "Amount", "Status", "Transaction", "Created");
            case "ACCOUNT_STATEMENT" -> List.of("Date", "Journal", "Account", "Property", "Unit", "Currency", "Debit", "Credit", "Description");
            case "LEASE_EXPIRY" -> List.of("Lease", "Property", "Unit", "Move in", "Expiry", "Days remaining", "Status", "Auto renew", "Notice months", "Rent", "Currency");
            case "OCCUPANCY_RENT_ROLL" -> List.of("Property", "Unit", "Use", "Type", "Price", "Currency", "Occupied", "Advertised");
            case "VISITOR_ACTIVITY" -> List.of("Property", "Unit", "Visit type", "Purpose", "Vehicle", "Expected", "Checked in", "Checked out", "Status");
            case "SALES_PIPELINE" -> List.of("Property", "Unit", "Status", "Asking price", "Offer", "Currency", "Offered", "Completed", "Created");
            case "ESTATE_CHARGES" -> List.of("Property", "Unit", "Description", "Amount", "Currency", "Due date", "Status", "Invoice");
            case "SERVICE_BOOKINGS" -> List.of("Booking", "Service", "Scheduled", "Completed", "Status", "Quoted", "Currency", "Pricing unit", "Created");
            case "SOKO_ORDERS" -> List.of("Order", "Store", "Status", "Payment", "Delivery", "Subtotal", "Delivery fee", "Total", "Currency", "Placed", "Completed", "Delivery code verified");
            case "SUBSCRIPTION_LIFECYCLE" -> List.of("Subscription", "Role", "Plan", "Status", "Start", "End", "Auto renew", "Payment reference");
            case "AFFILIATE_EARNINGS" -> List.of("Invoice", "Status", "Qualifying amount", "Rate %", "Commission", "Currency", "Earned", "Available");
            case "KYC_OPERATIONS" -> List.of("Case", "User", "Status", "Phone verified", "Registry", "Consent version", "Submitted", "Reviewed");
            case "NOTIFICATION_DELIVERY" -> List.of("Notification", "Channel", "Type", "Recipient", "Delivered", "Retry", "Retries", "Created", "Updated");
            case "SMART_GATE_HEALTH" -> List.of("Device", "Name", "Property", "Gate", "Lane", "Enabled", "Last seen", "Health");
            case "MAINTENANCE_OPERATIONS" -> List.of("Work order", "Property", "Unit", "Title", "Category", "Priority", "Status", "Scheduled", "Completed", "Estimated", "Actual", "Currency");
            default -> throw new IllegalArgumentException("Unsupported report schema: " + code);
        };
    }
}
