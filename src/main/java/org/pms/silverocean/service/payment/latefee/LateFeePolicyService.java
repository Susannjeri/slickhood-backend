package org.pms.silverocean.service.payment.latefee;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.PMSInvoiceRepo;
import org.pms.silverocean.database.pms.ReceivableLateFeePolicyRepo;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.ReceivableLateFeePolicy;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.payment.invoice.InvoiceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LateFeePolicyService {
    private static final Set<String> SUPPORTED_TYPES = Set.of("RENTAL", "SALE", "SERVICE_CHARGE");
    private final ReceivableLateFeePolicyRepo policies;
    private final PMSInvoiceRepo invoices;
    private final InvoiceService invoiceService;
    private final UserDao users;
    private final NotificationService notifications;
    private final I18NService i18n;

    @Transactional(readOnly = true)
    public LateFeePolicyModels.View view(String rawBillingType) {
        String billingType = billingType(rawBillingType);
        long billerId = authorisedBiller(billingType);
        return policies.findByCreatedByAndBillingTypeAndActiveTrue(billerId, billingType)
                .map(policy -> view(policy, true))
                .orElse(new LateFeePolicyModels.View(billingType, BigDecimal.ZERO.setScale(4), 0, false, false, null));
    }

    @Transactional
    public LateFeePolicyModels.View save(String rawBillingType, LateFeePolicyModels.Update request) {
        String billingType = billingType(rawBillingType);
        long billerId = authorisedBiller(billingType);
        BigDecimal rate = request.percentageRate().setScale(4, RoundingMode.HALF_UP);
        if (request.enabled() && rate.signum() <= 0) throw invalid();
        ReceivableLateFeePolicy policy = policies
                .findByCreatedByAndBillingTypeAndActiveTrue(billerId, billingType)
                .orElseGet(ReceivableLateFeePolicy::new);
        if (policy.getId() == null) {
            policy.setCreatedBy(billerId);
            policy.setBillingType(billingType);
            policy.setActive(true);
        }
        policy.setPercentageRate(rate);
        policy.setGraceDays(request.graceDays());
        policy.setEffectiveFrom(LocalDate.now(PMSUtils.getZoneId()));
        policy.setEnabled(request.enabled());
        return view(policies.save(policy), true);
    }

    /**
     * Assesses at most one non-compounding fee against an overdue source invoice.
     * The fee is a separate invoice so the issued source invoice remains immutable.
     */
    @Transactional
    public Optional<PMSInvoice> assess(long sourceInvoiceId) {
        PMSInvoice source = invoices.findByIdForUpdate(sourceInvoiceId).orElse(null);
        if (source == null || !source.isActive() || source.isPaid() || source.getPendingAmount() <= 0
                || source.getLateFeeSourceInvoiceId() != null || source.getDueDate() == null
                || !SUPPORTED_TYPES.contains(source.getBillingType())
                || invoices.existsByLateFeeSourceInvoiceId(source.getId())) return Optional.empty();
        ReceivableLateFeePolicy policy = policies
                .findByCreatedByAndBillingTypeAndActiveTrue(source.getPayToUserId(), source.getBillingType())
                .filter(ReceivableLateFeePolicy::isEnabled).orElse(null);
        LocalDate today = LocalDate.now(PMSUtils.getZoneId());
        if (policy == null || policy.getPercentageRate().signum() <= 0 || policy.getEffectiveFrom() == null
                || source.getDueDate().isBefore(policy.getEffectiveFrom())
                || !today.isAfter(source.getDueDate().plusDays(policy.getGraceDays()))) return Optional.empty();
        BigDecimal principal = BigDecimal.valueOf(source.getPendingAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = principal.multiply(policy.getPercentageRate())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        if (fee.signum() <= 0) return Optional.empty();
        PMSInvoice feeInvoice = invoiceService.createLateFeeInvoice(source, fee, policy.getPercentageRate(), today);
        notifyAssessment(source, feeInvoice, principal, policy.getPercentageRate());
        return Optional.of(feeInvoice);
    }

    private void notifyAssessment(PMSInvoice source, PMSInvoice feeInvoice, BigDecimal principal, BigDecimal rate) {
        String rateText = rate.stripTrailingZeros().toPlainString();
        users.findById(source.getBilledUserId()).filter(Users::isActive)
                .filter(user -> user.getEmail() != null && !user.getEmail().isBlank())
                .ifPresent(payer -> {
                    String body = String.format(i18n.getLocalizedMessage(NotificationType.LATE_FEE_ASSESSED_EMAIL.getBody()),
                            escape(payer.getFullName()), rateText, source.getCurrency(), principal.toPlainString(),
                            escape(source.getRef()), source.getCurrency(), money(feeInvoice.getAmount()), escape(feeInvoice.getRef()));
                    notifications.queueEmailAndInApp(payer.getEmail(), NotificationType.LATE_FEE_ASSESSED_EMAIL, body,
                            source.getBillingType() + "_LATE_FEE_ASSESSED",
                            "A " + rateText + "% late fee was assessed on invoice " + source.getRef()
                                    + ". Fee invoice " + feeInvoice.getRef() + " is " + source.getCurrency() + " "
                                    + money(feeInvoice.getAmount()) + ". Open /dashboard/invoices to review or pay it.");
                });
        users.findById(source.getPayToUserId()).filter(Users::isActive)
                .filter(user -> user.getEmail() != null && !user.getEmail().isBlank())
                .ifPresent(biller -> notifications.queueEmailAndInApp(biller.getEmail(),
                        NotificationType.LATE_FEE_BILLER_EMAIL,
                        String.format(i18n.getLocalizedMessage(NotificationType.LATE_FEE_BILLER_EMAIL.getBody()),
                                escape(source.getRef()), rateText, source.getCurrency(), money(feeInvoice.getAmount()),
                                escape(feeInvoice.getRef())),
                        source.getBillingType() + "_LATE_FEE_ISSUED",
                        "Late-fee invoice " + feeInvoice.getRef() + " was issued at " + rateText
                                + "% for overdue invoice " + source.getRef() + "."));
    }

    private LateFeePolicyModels.View view(ReceivableLateFeePolicy policy, boolean configured) {
        return new LateFeePolicyModels.View(policy.getBillingType(), policy.getPercentageRate(),
                policy.getGraceDays(), policy.isEnabled(), configured, policy.getEffectiveFrom());
    }

    private long authorisedBiller(String billingType) {
        PMSRole required = switch (billingType) {
            case "RENTAL" -> PMSRole.LANDLORD;
            case "SALE" -> PMSRole.SALES_AGENT;
            case "SERVICE_CHARGE" -> PMSRole.ESTATE_MANAGER;
            default -> throw invalid();
        };
        if (users.getActiveRole() != required) throw new PMSCustomException(ResponseCode.INVALID_ROLE);
        return users.getUserId();
    }

    private String billingType(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(value)) throw invalid();
        return value;
    }

    private String money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    private String escape(String value) { return HtmlUtils.htmlEscape(value == null ? "" : value); }
    private PMSCustomException invalid() { return new PMSCustomException(ResponseCode.GENERAL_FAILURE); }
}
