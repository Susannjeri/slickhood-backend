package org.pms.silverocean.service.reports;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ReportService {
    private static final int SCREEN_ROWS = 500;
    private static final int EXPORT_ROWS = 5_000;
    private static final int MAX_RANGE_DAYS = 366;

    private final UserDao users;
    private final PMSInvoiceRepo invoices;
    private final PMSPaymentRepo payments;
    private final UnitRepo units;
    private final VisitorRepo visitors;
    private final SaleTransactionRepo sales;
    private final EstateServiceChargeRepo estateCharges;
    private final ServiceBookingRepo serviceBookings;
    private final SokoOrderRepo sokoOrders;
    private final FinancialLedgerLineRepo ledgerLines;
    private final LeaseRepo leases;
    private final UserSubscriptionRepo subscriptions;
    private final AffiliateCommissionRepo affiliateCommissions;
    private final KycCaseRepo kycCases;
    private final NotificationRepo notifications;
    private final GateDeviceRepo gateDevices;
    private final MaintenanceWorkOrderRepo maintenanceOrders;

    private static final List<ReportModels.Definition> DEFINITIONS = List.of(
            definition("INVOICE_COLLECTIONS", "Invoice collections & arrears", "Billed, collected, outstanding and overdue invoices.", "FINANCE",
                    PMSRole.LANDLORD, PMSRole.TENANT, PMSRole.PROPERTY_MANAGER, PMSRole.ESTATE_MANAGER, PMSRole.HOMEOWNER, PMSRole.SALES_AGENT, PMSRole.BUYER, PMSRole.WORKSPACE_ADMIN, PMSRole.PROPERTY_ACCOUNTANT, PMSRole.WORKSPACE_VIEWER, PMSRole.FINANCE, PMSRole.SUPER_ADMIN),
            definition("PAYMENT_RECONCILIATION", "Payment reconciliation", "Payment-channel success, pending and exception activity matched to invoices.", "FINANCE",
                    PMSRole.LANDLORD, PMSRole.TENANT, PMSRole.PROPERTY_MANAGER, PMSRole.ESTATE_MANAGER, PMSRole.HOMEOWNER, PMSRole.WORKSPACE_ADMIN, PMSRole.PROPERTY_ACCOUNTANT, PMSRole.WORKSPACE_VIEWER, PMSRole.FINANCE, PMSRole.SUPER_ADMIN),
            definition("ACCOUNT_STATEMENT", "Account statement", "Immutable debit and credit entries for invoices and receipts.", "FINANCE",
                    PMSRole.LANDLORD, PMSRole.TENANT, PMSRole.PROPERTY_MANAGER, PMSRole.ESTATE_MANAGER, PMSRole.HOMEOWNER, PMSRole.SALES_AGENT, PMSRole.BUYER, PMSRole.WORKSPACE_ADMIN, PMSRole.PROPERTY_ACCOUNTANT, PMSRole.WORKSPACE_VIEWER, PMSRole.FINANCE, PMSRole.SUPER_ADMIN),
            forwardDefinition("LEASE_EXPIRY", "Lease expiry & renewal", "Signed and draft leases approaching expiry, including renewal and notice settings.", "PROPERTY",
                    PMSRole.LANDLORD, PMSRole.TENANT, PMSRole.PROPERTY_MANAGER, PMSRole.ESTATE_MANAGER, PMSRole.WORKSPACE_ADMIN, PMSRole.LEASING_OFFICER, PMSRole.WORKSPACE_VIEWER, PMSRole.FINANCE, PMSRole.SUPER_ADMIN),
            snapshotDefinition("OCCUPANCY_RENT_ROLL", "Occupancy & rent roll", "Current unit occupancy, asking rent, availability and advertising status.", "PROPERTY",
                    PMSRole.LANDLORD, PMSRole.TENANT, PMSRole.PROPERTY_MANAGER, PMSRole.HOMEOWNER, PMSRole.ESTATE_MANAGER, PMSRole.WORKSPACE_ADMIN, PMSRole.LEASING_OFFICER, PMSRole.ESTATE_OPERATIONS_MANAGER, PMSRole.WORKSPACE_VIEWER, PMSRole.SUPER_ADMIN),
            definition("VISITOR_ACTIVITY", "Visitor & gate activity", "Walk-ins, drive-ins and deliveries with gate status and dwell time.", "SECURITY",
                    PMSRole.LANDLORD, PMSRole.TENANT, PMSRole.PROPERTY_MANAGER, PMSRole.HOMEOWNER, PMSRole.ESTATE_MANAGER, PMSRole.WORKSPACE_ADMIN, PMSRole.ESTATE_OPERATIONS_MANAGER, PMSRole.SECURITY_SUPERVISOR, PMSRole.GUARD, PMSRole.WORKSPACE_VIEWER, PMSRole.SUPER_ADMIN),
            definition("SALES_PIPELINE", "Property sales pipeline", "Offers, due diligence, completion and pipeline value.", "SALES",
                    PMSRole.LANDLORD, PMSRole.PROPERTY_MANAGER, PMSRole.SALES_AGENT, PMSRole.BUYER, PMSRole.WORKSPACE_ADMIN, PMSRole.PROPERTY_ACCOUNTANT, PMSRole.SALES_COORDINATOR, PMSRole.LISTING_AGENT, PMSRole.WORKSPACE_VIEWER, PMSRole.FINANCE, PMSRole.SUPER_ADMIN),
            definition("ESTATE_CHARGES", "Estate service-charge report", "Service-charge billing, settlement and overdue exposure.", "ESTATE",
                    PMSRole.LANDLORD, PMSRole.PROPERTY_MANAGER, PMSRole.ESTATE_MANAGER, PMSRole.HOMEOWNER, PMSRole.WORKSPACE_ADMIN, PMSRole.PROPERTY_ACCOUNTANT, PMSRole.ESTATE_OPERATIONS_MANAGER, PMSRole.WORKSPACE_VIEWER, PMSRole.FINANCE, PMSRole.SUPER_ADMIN),
            definition("SERVICE_BOOKINGS", "Service marketplace operations", "Bookings, completion, cancellations and quoted value.", "MARKETPLACE",
                    PMSRole.LANDLORD, PMSRole.TENANT, PMSRole.PROPERTY_MANAGER, PMSRole.HOMEOWNER, PMSRole.ESTATE_MANAGER, PMSRole.WORKSPACE_ADMIN, PMSRole.ESTATE_OPERATIONS_MANAGER, PMSRole.SERVICE_PROVIDER, PMSRole.SUPER_ADMIN),
            definition("SOKO_ORDERS", "Soko orders & delivery", "Orders, payments, dispatch, delivery-code completion and order value.", "SOKO", PMSRole.values())
            ,definition("SUBSCRIPTION_LIFECYCLE", "Subscription lifecycle", "Trials, active plans, renewals and expiries by role and plan.", "SUBSCRIPTION", PMSRole.values())
            ,definition("AFFILIATE_EARNINGS", "Affiliate earnings", "Commission qualification, approval and payout allocation.", "AFFILIATE", PMSRole.AFFILIATE, PMSRole.FINANCE, PMSRole.SUPER_ADMIN)
            ,definition("KYC_OPERATIONS", "KYC operations", "Consent, phone verification, submission and review status without document contents.", "COMPLIANCE", PMSRole.SUPER_ADMIN)
            ,definition("NOTIFICATION_DELIVERY", "Notification delivery", "Channel delivery, retry and failure performance.", "OPERATIONS", PMSRole.SUPER_ADMIN)
            ,snapshotDefinition("SMART_GATE_HEALTH", "Smart-gate health", "Registered gate devices, enablement and last-seen health.", "SECURITY", PMSRole.LANDLORD, PMSRole.PROPERTY_MANAGER, PMSRole.ESTATE_MANAGER, PMSRole.WORKSPACE_ADMIN, PMSRole.ESTATE_OPERATIONS_MANAGER, PMSRole.SECURITY_SUPERVISOR, PMSRole.GUARD, PMSRole.WORKSPACE_VIEWER, PMSRole.SUPER_ADMIN)
            ,definition("MAINTENANCE_OPERATIONS", "Maintenance operations", "Open, in-progress, completed and emergency work orders.", "PROPERTY", PMSRole.LANDLORD, PMSRole.TENANT, PMSRole.PROPERTY_MANAGER, PMSRole.ESTATE_MANAGER, PMSRole.HOMEOWNER, PMSRole.WORKSPACE_ADMIN, PMSRole.ESTATE_OPERATIONS_MANAGER, PMSRole.WORKSPACE_VIEWER, PMSRole.SUPER_ADMIN)
    );

    public List<ReportModels.Definition> catalog() {
        String activeRole = users.getActiveRole().getName();
        return DEFINITIONS.stream().filter(d -> d.availableToRoles().contains(activeRole)).toList();
    }

    @Transactional(readOnly = true)
    public ReportModels.Data generate(String code, LocalDate requestedFrom, LocalDate requestedTo) {
        return generate(code, requestedFrom, requestedTo, SCREEN_ROWS);
    }

    private ReportModels.Data generate(String code, LocalDate requestedFrom, LocalDate requestedTo, int rowLimit) {
        ReportModels.Definition definition = definitionFor(code);
        authorize(definition);
        Range range = validateRange(definition, requestedFrom, requestedTo);
        return switch (definition.code()) {
            case "INVOICE_COLLECTIONS" -> invoiceCollections(definition, range, rowLimit);
            case "PAYMENT_RECONCILIATION" -> paymentReconciliation(definition, range, rowLimit);
            case "ACCOUNT_STATEMENT" -> accountStatement(definition, range, rowLimit);
            case "LEASE_EXPIRY" -> leaseExpiry(definition, range, rowLimit);
            case "OCCUPANCY_RENT_ROLL" -> occupancy(definition, range, rowLimit);
            case "VISITOR_ACTIVITY" -> visitorActivity(definition, range, rowLimit);
            case "SALES_PIPELINE" -> salesPipeline(definition, range, rowLimit);
            case "ESTATE_CHARGES" -> estateCharges(definition, range, rowLimit);
            case "SERVICE_BOOKINGS" -> serviceBookings(definition, range, rowLimit);
            case "SOKO_ORDERS" -> sokoOrders(definition, range, rowLimit);
            case "SUBSCRIPTION_LIFECYCLE" -> subscriptionLifecycle(definition, range, rowLimit);
            case "AFFILIATE_EARNINGS" -> affiliateEarnings(definition, range, rowLimit);
            case "KYC_OPERATIONS" -> kycOperations(definition, range, rowLimit);
            case "NOTIFICATION_DELIVERY" -> notificationDelivery(definition, range, rowLimit);
            case "SMART_GATE_HEALTH" -> smartGateHealth(definition, range, rowLimit);
            case "MAINTENANCE_OPERATIONS" -> maintenanceOperations(definition, range, rowLimit);
            default -> throw new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND);
        };
    }

    public ReportModels.CsvExport csv(String code, LocalDate from, LocalDate to) {
        ReportModels.Data report = generate(code, from, to, EXPORT_ROWS);
        StringBuilder csv = new StringBuilder("\uFEFF");
        appendCsvRow(csv, report.columns());
        for (Map<String, Object> row : report.rows()) {
            appendCsvRow(csv, report.columns().stream().map(row::get).map(this::display).toList());
        }
        return new ReportModels.CsvExport(csv.toString().getBytes(StandardCharsets.UTF_8), report.truncated(), report.rowLimit());
    }

    private ReportModels.Data invoiceCollections(ReportModels.Definition definition, Range range, int rowLimit) {
        List<PMSInvoice> fetched = invoices.findForReport(userId(), privileged(), range.start(), range.end(), page(rowLimit));
        boolean truncated = isTruncated(fetched, rowLimit);
        List<PMSInvoice> data = cap(fetched, rowLimit);
        long overdue = data.stream().filter(i -> !i.isPaid() && i.getDueDate() != null && i.getDueDate().isBefore(LocalDate.now(PMSUtils.getZoneId()))).count();
        List<Map<String, Object>> rows = data.stream().map(i -> row(
                "Reference", i.getRef(), "Type", value(i.getBillingType(), "GENERAL"), "Property", i.getPropertyId(), "Unit", i.getUnitId(),
                "Amount", money(i.getAmount()), "Currency", i.getCurrency(), "Collected", money(i.getAmount() - i.getPendingAmount()),
                "Outstanding", money(i.getPendingAmount()), "Due date", i.getDueDate(), "Status", i.isPaid() ? "PAID" : overdueStatus(i.getDueDate()), "Created", i.getCreatedOn())).toList();
        return data(definition, range, map("Invoices", data.size(),
                "Billed by currency", totalsByCurrency(data, i -> i.getCurrency(), i -> BigDecimal.valueOf(i.getAmount())),
                "Collected by currency", totalsByCurrency(data, i -> i.getCurrency(), i -> BigDecimal.valueOf(i.getAmount() - i.getPendingAmount())),
                "Outstanding by currency", totalsByCurrency(data, i -> i.getCurrency(), i -> BigDecimal.valueOf(i.getPendingAmount())), "Overdue", overdue), rows, truncated, rowLimit);
    }

    private ReportModels.Data paymentReconciliation(ReportModels.Definition definition, Range range, int rowLimit) {
        List<PMSPayment> fetched = payments.findForReport(userId(), privileged(), range.start(), range.end(), page(rowLimit));
        boolean truncated = isTruncated(fetched, rowLimit);
        List<PMSPayment> data = cap(fetched, rowLimit);
        long successful = data.stream().filter(this::successful).count();
        long pending = data.stream().filter(PMSPayment::isInProgress).count();
        List<Map<String, Object>> rows = data.stream().map(p -> row(
                "Reference", p.getBillReference(), "Channel", p.getChannel(), "Category", p.getCategory(), "Amount", money(p.getAmount()),
                "Status", value(p.getStatus(), p.isInProgress() ? "PENDING" : "UNKNOWN"), "Transaction", p.getThirdPartyTransId(), "Created", p.getCreatedOn())).toList();
        return data(definition, range, map("Payments", data.size(), "Successful", successful, "Pending", pending, "Exceptions", data.size() - successful - pending), rows, truncated, rowLimit);
    }

    private ReportModels.Data accountStatement(ReportModels.Definition definition, Range range, int rowLimit) {
        List<FinancialLedgerLine> fetched = ledgerLines.findForStatement(userId(), privileged(), range.start(), range.end(), page(rowLimit));
        boolean truncated = isTruncated(fetched, rowLimit);
        List<FinancialLedgerLine> data = cap(fetched, rowLimit);
        List<Map<String, Object>> rows = data.stream().map(l -> row(
                "Date", l.getCreatedOn(), "Journal", l.getJournalId(), "Account", l.getAccountCode(),
                "Property", l.getPropertyId(), "Unit", l.getUnitId(), "Currency", l.getCurrency(),
                "Debit", l.getDebit(), "Credit", l.getCredit(), "Description", l.getDescription())).toList();
        return data(definition, range, map("Entries", data.size(),
                "Debits by currency", totalsByCurrency(data, FinancialLedgerLine::getCurrency, FinancialLedgerLine::getDebit),
                "Credits by currency", totalsByCurrency(data, FinancialLedgerLine::getCurrency, FinancialLedgerLine::getCredit)), rows, truncated, rowLimit);
    }

    private ReportModels.Data leaseExpiry(ReportModels.Definition definition, Range range, int rowLimit) {
        List<LeaseExpiryProjection> fetched = leases.findExpiringForReport(userId(), privileged(), range.from(), range.to(), page(rowLimit));
        boolean truncated = isTruncated(fetched, rowLimit);
        List<LeaseExpiryProjection> data = cap(fetched, rowLimit);
        LocalDate today = LocalDate.now(PMSUtils.getZoneId());
        List<Map<String, Object>> rows = data.stream().map(l -> row(
                "Lease", l.getLeaseId(), "Property", l.getPropertyId(), "Unit", l.getUnitRef(),
                "Move in", l.getMoveInDate(), "Expiry", l.getMoveOutDate(),
                "Days remaining", ChronoUnit.DAYS.between(today, l.getMoveOutDate()),
                "Status", Boolean.TRUE.equals(l.getSigned()) ? "SIGNED" : "DRAFT",
                "Auto renew", yesNo(Boolean.TRUE.equals(l.getSelfRenew())), "Notice months", l.getNoticePeriodInMonths(),
                "Rent", money(l.getPrice()), "Currency", l.getCurrency())).toList();
        return data(definition, range, map("Expiring leases", data.size(),
                "Within 30 days", count(data, l -> !l.getMoveOutDate().isAfter(today.plusDays(30))),
                "Auto renew", count(data, l -> Boolean.TRUE.equals(l.getSelfRenew())),
                "Unsigned", count(data, l -> !Boolean.TRUE.equals(l.getSigned()))), rows, truncated, rowLimit);
    }

    private ReportModels.Data occupancy(ReportModels.Definition definition, Range range, int rowLimit) {
        List<Unit> fetched = units.findForReport(userId(), privileged(), page(rowLimit));
        boolean truncated = isTruncated(fetched, rowLimit);
        List<Unit> data = cap(fetched, rowLimit);
        long occupied = data.stream().filter(Unit::isOccupied).count();
        List<Map<String, Object>> rows = data.stream().map(u -> row(
                "Property", u.getProperty() == null ? u.getPropertyId() : u.getProperty().getName(), "Unit", u.getRef(), "Use", u.getLeaseMode(),
                "Type", u.getUnitType(), "Price", money(u.getPrice()), "Currency", u.getCurrency(), "Occupied", yesNo(u.isOccupied()), "Advertised", yesNo(u.isAdvertise()))).toList();
        double rate = data.isEmpty() ? 0 : occupied * 100.0 / data.size();
        return data(definition, range, map("Units", data.size(), "Occupied", occupied, "Vacant", data.size() - occupied, "Occupancy %", rounded(rate),
                "Occupied value by currency", totalsByCurrency(data.stream().filter(Unit::isOccupied).toList(), Unit::getCurrency, u -> BigDecimal.valueOf(u.getPrice()))), rows, truncated, rowLimit);
    }

    private ReportModels.Data visitorActivity(ReportModels.Definition definition, Range range, int rowLimit) {
        List<Visitor> fetched = visitors.findForReport(userId(), privileged(), range.start(), range.end(), page(rowLimit));
        boolean truncated = isTruncated(fetched, rowLimit);
        List<Visitor> data = cap(fetched, rowLimit);
        long inside = data.stream().filter(v -> "CHECKED_IN".equalsIgnoreCase(v.getStatus()) || (v.getCheckedInAt() != null && v.getCheckedOutAt() == null)).count();
        List<Map<String, Object>> rows = data.stream().map(v -> row(
                "Property", v.getPropertyName(), "Unit", v.getUnitRef(), "Visit type", value(v.getVisitType(), v.getCategory()), "Purpose", v.getPurpose(),
                "Vehicle", maskPlate(v.getVehiclePlate()), "Expected", v.getExpectedArrivalTime(), "Checked in", v.getCheckedInAt(), "Checked out", v.getCheckedOutAt(), "Status", v.getStatus())).toList();
        return data(definition, range, map("Visits", data.size(), "Currently inside", inside, "Walk-ins", count(data, v -> "WALK_IN".equals(v.getVisitType())),
                "Drive-ins", count(data, v -> "DRIVE_IN".equals(v.getVisitType())), "Deliveries", count(data, v -> "DELIVERY".equals(v.getVisitType()))), rows, truncated, rowLimit);
    }

    private ReportModels.Data salesPipeline(ReportModels.Definition definition, Range range, int rowLimit) {
        List<SaleTransaction> fetched = sales.findForReport(userId(), privileged(), range.start(), range.end(), page(rowLimit));
        boolean truncated = isTruncated(fetched, rowLimit);
        List<SaleTransaction> data = cap(fetched, rowLimit);
        List<Map<String, Object>> rows = data.stream().map(s -> row(
                "Property", s.getPropertyId(), "Unit", s.getUnitId(), "Status", s.getStatus(), "Asking price", s.getAskingPrice(),
                "Offer", s.getOfferAmount(), "Currency", s.getCurrency(), "Offered", s.getOfferAcceptedAt(), "Completed", s.getCompletedAt(), "Created", s.getCreatedOn())).toList();
        return data(definition, range, map("Transactions", data.size(), "Offers", count(data, s -> "OFFERED".equals(s.getStatus().name())),
                "Due diligence", count(data, s -> "DUE_DILIGENCE".equals(s.getStatus().name())), "Completed", count(data, s -> "COMPLETED".equals(s.getStatus().name()))), rows, truncated, rowLimit);
    }

    private ReportModels.Data estateCharges(ReportModels.Definition definition, Range range, int rowLimit) {
        List<EstateServiceCharge> fetched = estateCharges.findForReport(userId(), privileged(), range.start(), range.end(), page(rowLimit));
        boolean truncated = isTruncated(fetched, rowLimit);
        List<EstateServiceCharge> data = cap(fetched, rowLimit);
        Set<Long> invoiceIds = data.stream().map(EstateServiceCharge::getInvoiceId).collect(java.util.stream.Collectors.toSet());
        Map<Long, PMSInvoice> invoiceMap = invoices.findAllById(invoiceIds).stream().collect(java.util.stream.Collectors.toMap(PMSInvoice::getId, Function.identity()));
        List<Map<String, Object>> rows = data.stream().map(c -> { PMSInvoice i = invoiceMap.get(c.getInvoiceId()); return row(
                "Property", c.getPropertyId(), "Unit", c.getUnitId(), "Description", c.getDescription(), "Amount", c.getAmount(), "Currency", c.getCurrency(),
                "Due date", c.getDueDate(), "Status", i != null && i.isPaid() ? "PAID" : overdueStatus(c.getDueDate()), "Invoice", i == null ? c.getInvoiceId() : i.getRef()); }).toList();
        return data(definition, range, map("Charges", data.size(),
                "Billed by currency", totalsByCurrency(data, EstateServiceCharge::getCurrency, EstateServiceCharge::getAmount),
                "Outstanding by currency", totalsByCurrency(data, EstateServiceCharge::getCurrency,
                        c -> Optional.ofNullable(invoiceMap.get(c.getInvoiceId())).filter(i -> !i.isPaid()).map(i -> BigDecimal.valueOf(i.getPendingAmount())).orElse(BigDecimal.ZERO))), rows, truncated, rowLimit);
    }

    private ReportModels.Data serviceBookings(ReportModels.Definition definition, Range range, int rowLimit) {
        List<ServiceBooking> fetched = serviceBookings.findForReport(userId(), privileged(), range.start(), range.end(), page(rowLimit));
        boolean truncated = isTruncated(fetched, rowLimit);
        List<ServiceBooking> data = cap(fetched, rowLimit);
        List<Map<String, Object>> rows = data.stream().map(b -> row(
                "Booking", b.getId(), "Service", b.getServiceId(), "Scheduled", b.getScheduledAt(), "Completed", b.getCompletedAt(), "Status", b.getStatus(),
                "Quoted", b.getQuotedAmount(), "Currency", b.getCurrency(), "Pricing unit", b.getPricingUnit(), "Created", b.getCreatedOn())).toList();
        return data(definition, range, map("Bookings", data.size(), "Completed", count(data, b -> "COMPLETED".equals(b.getStatus())),
                "Cancelled", count(data, b -> "CANCELLED".equals(b.getStatus())),
                "Quoted by currency", totalsByCurrency(data, ServiceBooking::getCurrency, b -> Optional.ofNullable(b.getQuotedAmount()).orElse(BigDecimal.ZERO))), rows, truncated, rowLimit);
    }

    private ReportModels.Data sokoOrders(ReportModels.Definition definition, Range range, int rowLimit) {
        List<SokoOrder> fetched = sokoOrders.findForReport(userId(), privileged(), range.start(), range.end(), page(rowLimit));
        boolean truncated = isTruncated(fetched, rowLimit);
        List<SokoOrder> data = cap(fetched, rowLimit);
        List<Map<String, Object>> rows = data.stream().map(o -> row(
                "Order", o.getOrderNumber(), "Store", o.getStoreId(), "Status", o.getStatus(), "Payment", o.getPaymentStatus(), "Delivery", o.getDeliveryMethod(),
                "Subtotal", o.getSubtotal(), "Delivery fee", o.getDeliveryFee(), "Total", o.getTotal(), "Currency", o.getCurrency(), "Placed", o.getPlacedAt(),
                "Completed", o.getCompletedAt(), "Delivery code verified", yesNo(o.isDeliveryCodeVerified()))).toList();
        return data(definition, range, map("Orders", data.size(), "Completed", count(data, o -> "COMPLETED".equals(o.getStatus())),
                "Paid", count(data, o -> "PAID".equals(o.getPaymentStatus())), "Delivery verified", count(data, SokoOrder::isDeliveryCodeVerified),
                "Order value by currency", totalsByCurrency(data, SokoOrder::getCurrency, o -> Optional.ofNullable(o.getTotal()).orElse(BigDecimal.ZERO))), rows, truncated, rowLimit);
    }

    private ReportModels.Data subscriptionLifecycle(ReportModels.Definition definition, Range range, int rowLimit) {
        List<UserSubscription> fetched=subscriptions.findForReport(userId(),privileged(),range.start(),range.end(),page(rowLimit));boolean truncated=isTruncated(fetched,rowLimit);List<UserSubscription> data=cap(fetched,rowLimit);
        List<Map<String,Object>> rows=data.stream().map(s->row("Subscription",s.getId(),"Role",s.getRole(),"Plan",s.getPlanCode(),"Status",s.getStatus(),"Start",s.getStartAt(),"End",s.getEndAt(),"Auto renew",yesNo(s.isAutoRenew()),"Payment reference",s.getSourcePaymentRef())).toList();
        return data(definition,range,map("Subscriptions",data.size(),"Active",count(data,s->"ACTIVE".equals(String.valueOf(s.getStatus()))),"Trials",count(data,s->"TRIAL".equals(String.valueOf(s.getStatus()))),"Expiring within 30 days",count(data,s->s.getEndAt()!=null&&!s.getEndAt().isAfter(ZonedDateTime.now(PMSUtils.getZoneId()).plusDays(30)))),rows,truncated,rowLimit);
    }

    private ReportModels.Data affiliateEarnings(ReportModels.Definition definition, Range range, int rowLimit) {
        List<AffiliateCommission> fetched=affiliateCommissions.findForReport(userId(),privileged(),range.start(),range.end(),page(rowLimit));boolean truncated=isTruncated(fetched,rowLimit);List<AffiliateCommission> data=cap(fetched,rowLimit);
        List<Map<String,Object>> rows=data.stream().map(c->row("Invoice",c.getInvoiceRef(),"Status",c.getStatus(),"Qualifying amount",c.getQualifyingAmount(),"Rate %",c.getCommissionRate(),"Commission",affiliateNet(c),"Currency",c.getCurrency(),"Earned",c.getEarnedAt(),"Payout",c.getPayoutId())).toList();
        return data(definition,range,map("Commissions",data.size(),"Pending",count(data,c->"PENDING".equals(c.getStatus())),"Available",count(data,c->"EARNED".equals(c.getStatus())),"Paid",count(data,c->"PAID".equals(c.getStatus())),"Earnings by currency",totalsByCurrency(data,AffiliateCommission::getCurrency,this::affiliateNet)),rows,truncated,rowLimit);
    }

    private BigDecimal affiliateNet(AffiliateCommission commission){return List.of("REVERSED","CLAWBACK_DUE","CLAWBACK_REQUESTED","CLAWBACK_SETTLED").contains(commission.getStatus())?BigDecimal.ZERO:commission.getCommissionAmount();}

    private ReportModels.Data kycOperations(ReportModels.Definition definition, Range range, int rowLimit) {
        List<KycCase> fetched=kycCases.findForReport(userId(),true,range.start(),range.end(),page(rowLimit));boolean truncated=isTruncated(fetched,rowLimit);List<KycCase> data=cap(fetched,rowLimit);
        List<Map<String,Object>> rows=data.stream().map(k->row("Case",k.getId(),"User",k.getUserId(),"Status",k.getStatus(),"Phone verified",yesNo(k.isPhoneVerified()),"Registry",value(k.getRegistryStatus(),"NOT_CHECKED"),"Consent version",k.getConsentVersion(),"Submitted",k.getSubmittedAt(),"Reviewed",k.getReviewedAt())).toList();
        return data(definition,range,map("Cases",data.size(),"Phone verified",count(data,KycCase::isPhoneVerified),"Submitted",count(data,k->k.getSubmittedAt()!=null),"Reviewed",count(data,k->k.getReviewedAt()!=null)),rows,truncated,rowLimit);
    }

    private ReportModels.Data notificationDelivery(ReportModels.Definition definition, Range range, int rowLimit) {
        List<Notification> fetched=notifications.findForReport(range.start(),range.end(),page(rowLimit));boolean truncated=isTruncated(fetched,rowLimit);List<Notification> data=cap(fetched,rowLimit);
        List<Map<String,Object>> rows=data.stream().map(n->row("Notification",n.getId(),"Channel",n.getChannel(),"Type",n.getType(),"Recipient",maskRecipient(n.getRecipient()),"Delivered",yesNo(n.isDelivered()),"Retry",yesNo(n.isRetry()),"Retries",n.getRetries(),"Created",n.getCreatedOn(),"Updated",n.getUpdatedOn())).toList();
        return data(definition,range,map("Notifications",data.size(),"Delivered",count(data,Notification::isDelivered),"Failed or pending",count(data,n->!n.isDelivered()),"Retried",count(data,n->n.getRetries()>0)),rows,truncated,rowLimit);
    }

    private ReportModels.Data smartGateHealth(ReportModels.Definition definition, Range range, int rowLimit) {
        List<GateDevice> fetched=gateDevices.findForReport(userId(),privileged(),page(rowLimit));boolean truncated=isTruncated(fetched,rowLimit);List<GateDevice> data=cap(fetched,rowLimit);ZonedDateTime staleBefore=ZonedDateTime.now(PMSUtils.getZoneId()).minusMinutes(15);
        List<Map<String,Object>> rows=data.stream().map(g->row("Device",g.getDeviceCode(),"Name",g.getDisplayName(),"Property",g.getPropertyId(),"Gate",g.getGateName(),"Lane",g.getLaneName(),"Enabled",yesNo(g.isEnabled()),"Last seen",g.getLastSeenAt(),"Health",!g.isEnabled()?"DISABLED":g.getLastSeenAt()==null||g.getLastSeenAt().isBefore(staleBefore)?"OFFLINE":"ONLINE")).toList();
        return data(definition,range,map("Devices",data.size(),"Enabled",count(data,GateDevice::isEnabled),"Online",count(data,g->g.isEnabled()&&g.getLastSeenAt()!=null&&!g.getLastSeenAt().isBefore(staleBefore)),"Offline",count(data,g->g.isEnabled()&&(g.getLastSeenAt()==null||g.getLastSeenAt().isBefore(staleBefore)))),rows,truncated,rowLimit);
    }

    private ReportModels.Data maintenanceOperations(ReportModels.Definition definition, Range range, int rowLimit) {
        List<MaintenanceWorkOrder> fetched=maintenanceOrders.findForReport(userId(),privileged(),range.start(),range.end(),page(rowLimit));boolean truncated=isTruncated(fetched,rowLimit);List<MaintenanceWorkOrder> data=cap(fetched,rowLimit);
        List<Map<String,Object>> rows=data.stream().map(w->row("Work order",w.getWorkOrderNumber(),"Property",w.getPropertyId(),"Unit",w.getUnitId(),"Title",w.getTitle(),"Category",w.getCategory(),"Priority",w.getPriority(),"Status",w.getStatus(),"Scheduled",w.getScheduledAt(),"Completed",w.getCompletedAt(),"Estimated",w.getEstimatedCost(),"Actual",w.getActualCost(),"Currency",w.getCurrency())).toList();
        return data(definition,range,map("Work orders",data.size(),"Open",count(data,w->"OPEN".equals(w.getStatus())),"In progress",count(data,w->"IN_PROGRESS".equals(w.getStatus())),"Completed",count(data,w->"COMPLETED".equals(w.getStatus())),"Emergency",count(data,w->"EMERGENCY".equals(w.getPriority()))),rows,truncated,rowLimit);
    }

    private ReportModels.Data data(ReportModels.Definition definition, Range range, Map<String, Object> metrics,
                                   List<Map<String, Object>> rows, boolean truncated, int rowLimit) {
        List<String> columns = rows.isEmpty() ? List.of() : List.copyOf(rows.getFirst().keySet());
        return new ReportModels.Data(definition, range.from(), range.to(), ZonedDateTime.now(PMSUtils.getZoneId()), metrics, columns, rows, truncated, rowLimit);
    }

    private Range validateRange(ReportModels.Definition definition, LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate today = LocalDate.now(PMSUtils.getZoneId());
        if ("SNAPSHOT".equals(definition.dateMode())) {
            return new Range(today, today, today.atStartOfDay(PMSUtils.getZoneId()), today.plusDays(1).atStartOfDay(PMSUtils.getZoneId()));
        }
        LocalDate to = requestedTo == null ? ("FORWARD".equals(definition.dateMode()) ? today.plusDays(90) : today) : requestedTo;
        LocalDate from = requestedFrom == null ? ("FORWARD".equals(definition.dateMode()) ? today : to.minusDays(29)) : requestedFrom;
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        if ("HISTORICAL".equals(definition.dateMode()) && to.isAfter(today)) throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        return new Range(from, to, from.atStartOfDay(PMSUtils.getZoneId()), to.plusDays(1).atStartOfDay(PMSUtils.getZoneId()));
    }

    private ReportModels.Definition definitionFor(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        return DEFINITIONS.stream().filter(d -> d.code().equals(normalized)).findFirst().orElseThrow(() -> new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));
    }

    private void authorize(ReportModels.Definition definition) {
        if (!definition.availableToRoles().contains(users.getActiveRole().getName())) throw new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS);
    }

    private static ReportModels.Definition definition(String code, String title, String description, String category, PMSRole... roles) {
        return new ReportModels.Definition(code, title, description, category, true, "HISTORICAL", Arrays.stream(roles).map(PMSRole::getName).toList());
    }

    private static ReportModels.Definition forwardDefinition(String code, String title, String description, String category, PMSRole... roles) {
        return new ReportModels.Definition(code, title, description, category, true, "FORWARD", Arrays.stream(roles).map(PMSRole::getName).toList());
    }

    private static ReportModels.Definition snapshotDefinition(String code, String title, String description, String category, PMSRole... roles) {
        return new ReportModels.Definition(code, title, description, category, false, "SNAPSHOT", Arrays.stream(roles).map(PMSRole::getName).toList());
    }

    private long userId() { return Objects.requireNonNull(users.getUserId()); }
    private boolean privileged() { PMSRole role = users.getActiveRole(); return role == PMSRole.SUPER_ADMIN || role == PMSRole.FINANCE; }
    private PageRequest page(int rowLimit) { return PageRequest.of(0, rowLimit + 1); }
    private <T> boolean isTruncated(List<T> data, int rowLimit) { return data.size() > rowLimit; }
    private <T> List<T> cap(List<T> data, int rowLimit) { return data.size() <= rowLimit ? data : List.copyOf(data.subList(0, rowLimit)); }
    private boolean successful(PMSPayment p) { String s = value(p.getStatus(), "").toLowerCase(Locale.ROOT); return !p.isInProgress() && Set.of("success", "successful", "completed", "paid").contains(s); }
    private String overdueStatus(LocalDate due) { return due != null && due.isBefore(LocalDate.now(PMSUtils.getZoneId())) ? "OVERDUE" : "OUTSTANDING"; }
    private String maskPlate(String plate) { if (plate == null || plate.isBlank()) return ""; return plate.length() <= 3 ? "***" : plate.substring(0, 2) + "***" + plate.substring(plate.length() - 1); }
    private String maskRecipient(String recipient) { if (recipient == null || recipient.isBlank()) return ""; int at=recipient.indexOf('@'); if(at>1)return recipient.substring(0,1)+"***"+recipient.substring(at); return recipient.length()<5?"***":"***"+recipient.substring(recipient.length()-4); }
    private String yesNo(boolean value) { return value ? "Yes" : "No"; }
    private String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private BigDecimal money(Double value) { return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal money(double value) { return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP); }
    private double rounded(double value) { return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue(); }
    private <T> long count(Collection<T> data, java.util.function.Predicate<T> predicate) { return data.stream().filter(predicate).count(); }
    private <T> String totalsByCurrency(Collection<T> data, Function<T, String> currency, Function<T, BigDecimal> amount) {
        Map<String, BigDecimal> totals = new TreeMap<>();
        for (T item : data) {
            String code = Optional.ofNullable(currency.apply(item)).filter(v -> !v.isBlank()).orElse("UNSPECIFIED").toUpperCase(Locale.ROOT);
            totals.merge(code, Optional.ofNullable(amount.apply(item)).orElse(BigDecimal.ZERO), BigDecimal::add);
        }
        return totals.isEmpty() ? "—" : totals.entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue().setScale(2, RoundingMode.HALF_UP).toPlainString())
                .collect(java.util.stream.Collectors.joining(" · "));
    }

    private Map<String, Object> row(Object... values) { return linkedMap(values); }
    private Map<String, Object> map(Object... values) { return linkedMap(values); }
    private Map<String, Object> linkedMap(Object... values) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) map.put(String.valueOf(values[i]), values[i + 1]);
        return map;
    }
    private String display(Object value) { return value == null ? "" : value.toString(); }
    private void appendCsvRow(StringBuilder csv, List<?> values) { csv.append(values.stream().map(this::display).map(this::escapeCsv).collect(java.util.stream.Collectors.joining(","))).append("\r\n"); }
    private String escapeCsv(String value) {
        String safe = value == null ? "" : value;
        int first = 0;
        while (first < safe.length() && Character.isWhitespace(safe.charAt(first))) first++;
        if (first < safe.length() && "=+-@".indexOf(safe.charAt(first)) >= 0) safe = "'" + safe;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private record Range(LocalDate from, LocalDate to, ZonedDateTime start, ZonedDateTime end) {}
}
