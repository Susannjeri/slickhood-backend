package org.pms.silverocean.service.tax;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.tax.TaxModels.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class TaxAssistService {
    static final String MRI = "KENYA_MRI";
    static final String CGT = "KENYA_CGT_PROPERTY";
    static final String CONSENT_VERSION = "kra-connection-2026-09";
    private static final Map<String, Set<String>> CONNECTION_TRANSITIONS = Map.of(
            "REQUESTED", Set.of("AWAITING_KRA_APPROVAL", "REJECTED", "SUSPENDED"),
            "AWAITING_KRA_APPROVAL", Set.of("SANDBOX_READY", "REJECTED", "SUSPENDED"),
            "SANDBOX_READY", Set.of("AWAITING_KRA_APPROVAL", "SUSPENDED"),
            "SUSPENDED", Set.of("AWAITING_KRA_APPROVAL", "REJECTED"));

    private final TaxRuleVersionRepo rules;
    private final TaxCalculationRepo calculations;
    private final TaxConnectionRequestRepo connections;
    private final TaxAssistConfigurationRepo configurations;
    private final UserDao users;
    private final ObjectMapper json;

    public TaxAssistService(TaxRuleVersionRepo rules, TaxCalculationRepo calculations,
                            TaxConnectionRequestRepo connections, TaxAssistConfigurationRepo configurations,
                            UserDao users, ObjectMapper json) {
        this.rules = rules; this.calculations = calculations; this.connections = connections;
        this.configurations = configurations; this.users = users; this.json = json;
    }

    @Transactional
    public CalculationView estimateMri(MriEstimateRequest input) {
        requireEstimatesEnabled();
        LocalDate periodEnd = input.period().atEndOfMonth();
        TaxRuleVersion rule = effective(MRI, periodEnd);
        String outcome;
        String explanation;
        BigDecimal taxable = zero();
        BigDecimal tax = zero();
        BigDecimal credit = input.withholdingCredits().setScale(2, RoundingMode.HALF_UP);
        LocalDate due = input.period().plusMonths(1).atDay(20);

        if (!input.kenyaResidentialProperty()) {
            outcome = "NOT_MRI";
            explanation = "MRI applies only to qualifying residential rental income from property in Kenya. Review this income under the appropriate commercial or annual tax regime.";
        } else if (!input.residentTaxpayer()) {
            outcome = "SPECIALIST_REVIEW";
            explanation = "The standard resident MRI calculation does not apply. Non-resident rental rules changed in 2026 and require the taxpayer's registration or withholding position to be confirmed.";
        } else if (input.electedOutOfMri()) {
            outcome = "ANNUAL_REGIME";
            explanation = "You indicated that the taxpayer elected out of MRI. Rental income should be handled under the applicable annual income-tax regime.";
        } else if (rule.getLowerThreshold() != null && input.projectedAnnualGrossRent().compareTo(rule.getLowerThreshold()) <= 0) {
            outcome = "BELOW_THRESHOLD";
            explanation = "Projected annual residential rent does not exceed the statutory MRI entry threshold. Keep the estimate and review it if annual receipts increase.";
        } else if (rule.getUpperThreshold() != null && input.projectedAnnualGrossRent().compareTo(rule.getUpperThreshold()) > 0) {
            outcome = "ANNUAL_REGIME";
            explanation = "Projected annual residential rent exceeds the MRI ceiling and should be handled under the applicable annual income-tax regime.";
        } else if (input.grossRentReceived().signum() == 0) {
            outcome = "NIL_RETURN";
            explanation = "No rent was received for this month. If the taxpayer is registered for MRI, file a NIL return by the due date.";
            credit = zero();
        } else {
            outcome = "MRI_ESTIMATE";
            taxable = input.grossRentReceived().setScale(2, RoundingMode.HALF_UP);
            tax = taxable.multiply(rule.getRate()).setScale(2, RoundingMode.HALF_UP);
            if (credit.compareTo(tax) > 0) credit = tax;
            explanation = "Estimated MRI is based on gross residential rent received. Expenses and capital deductions are not deducted. Review any KRA withholding certificates before filing.";
        }
        return save("MRI", input.period().toString(), input.grossRentReceived(), taxable, tax, credit,
                tax.subtract(credit).max(zero()), outcome, explanation, due, input, rule);
    }

    @Transactional
    public CalculationView estimateCgt(CgtEstimateRequest input) {
        requireEstimatesEnabled();
        TaxRuleVersion rule = effective(CGT, input.transferDate());
        BigDecimal gross = input.transferValue().setScale(2, RoundingMode.HALF_UP);
        BigDecimal netTransfer = gross.subtract(input.transferCosts());
        BigDecimal adjustedCost = input.acquisitionCost().add(input.acquisitionCosts()).add(input.enhancementCosts());
        BigDecimal gain = netTransfer.subtract(adjustedCost).setScale(2, RoundingMode.HALF_UP);
        String outcome;
        String explanation;
        BigDecimal taxable = gain.max(zero());
        BigDecimal tax = zero();

        if (input.propertyDealer()) {
            outcome = "BUSINESS_INCOME_REVIEW";
            explanation = "The property may be trading stock of a property business. Do not rely on a simple CGT estimate; obtain the correct business-income treatment.";
            taxable = zero();
        } else if (input.potentialExemption()) {
            outcome = "POTENTIAL_EXEMPTION";
            explanation = "A possible exemption was selected" + (input.exemptionReason() == null || input.exemptionReason().isBlank() ? "." : ": " + input.exemptionReason().trim()) + " Preserve supporting documents and confirm the exemption before filing.";
            taxable = zero();
        } else if (gain.signum() <= 0) {
            outcome = "NO_TAXABLE_GAIN";
            explanation = "The supplied figures do not produce a positive capital gain. Preserve all cost evidence and obtain advice on any capital-loss treatment.";
            taxable = zero();
        } else {
            outcome = "CGT_ESTIMATE";
            tax = taxable.multiply(rule.getRate()).setScale(2, RoundingMode.HALF_UP);
            explanation = "Estimated CGT is based on the transfer value less allowable transfer, acquisition and enhancement costs. Confirm evidence, exemptions and the legal transfer date before filing.";
        }
        LocalDate due = YearMonth.from(input.transferDate()).plusMonths(1).atDay(20);
        return save("CGT", input.transferDate().toString(), gross, taxable, tax, zero(), tax,
                outcome, explanation, due, input, rule);
    }

    @Transactional(readOnly = true)
    public Page<CalculationView> history(Pageable pageable) {
        Pageable bounded = PageRequest.of(Math.max(0, pageable.getPageNumber()), Math.min(50, Math.max(1, pageable.getPageSize())));
        return calculations.findByOwnerUserIdOrderByCreatedOnDesc(users.getUserId(), bounded).map(this::view);
    }

    @Transactional
    public ConnectionView requestConnection(ConnectionRequest request) {
        if (!configurationEntity().isConnectionRequestsEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "KRA connection requests are currently paused by the Slickhood system owner.");
        validateScopes(request.provider(), request.requestedScopes());
        long ownerId = users.getUserId();
        if (connections.existsByOwnerUserIdAndProviderAndActiveTrue(ownerId, request.provider())) throw bad("An active request already exists for this provider.");
        TaxConnectionRequest c = new TaxConnectionRequest();
        c.setOwnerUserId(ownerId);
        c.setProvider(request.provider());
        c.setTaxpayerPinMasked(maskPin(request.taxpayerPin()));
        c.setRequestedScopes(String.join(",", new TreeSet<>(request.requestedScopes())));
        c.setConsentVersion(CONSENT_VERSION);
        c.setConsentedAt(ZonedDateTime.now());
        c.setStatus("REQUESTED");
        c.setEnvironment("SANDBOX");
        c.setActive(true);
        return connectionView(connections.save(c));
    }

    @Transactional(readOnly = true)
    public List<ConnectionView> myConnections() {
        return connections.findByOwnerUserIdAndActiveTrueOrderByCreatedOnDesc(users.getUserId()).stream().map(this::connectionView).toList();
    }

    @Transactional
    public void disconnect(long id) {
        TaxConnectionRequest c = connections.findById(id).filter(v -> v.getOwnerUserId() == users.getUserId()).orElseThrow(TaxAssistService::notFound);
        c.setActive(false); c.setStatus("DISCONNECTED"); connections.save(c);
    }

    @Transactional(readOnly = true)
    public List<RuleView> rules() { return rules.findAllByOrderByRuleCodeAscEffectiveFromDesc().stream().map(this::ruleView).toList(); }

    @Transactional(readOnly = true)
    public ConfigurationView configuration() { return configurationView(configurationEntity()); }

    @Transactional
    public ConfigurationView updateConfiguration(ConfigurationRequest request) {
        TaxAssistConfiguration c = configurationEntity();
        c.setEstimatesEnabled(request.estimatesEnabled()); c.setConnectionRequestsEnabled(request.connectionRequestsEnabled());
        c.setLegalNoticeVersion(request.legalNoticeVersion()); c.setUpdatedBy(users.getUserId());
        return configurationView(configurations.save(c));
    }

    @Transactional
    public RuleView createRule(RuleRequest request) {
        if (rules.existsByRuleCodeAndVersion(request.ruleCode(), request.version())) throw bad("Rule version already exists.");
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) throw bad("Effective-to date cannot precede effective-from date.");
        if (request.lowerThreshold() != null && request.upperThreshold() != null && request.lowerThreshold().compareTo(request.upperThreshold()) >= 0) throw bad("Lower threshold must be below upper threshold.");
        boolean overlaps = rules.findAllByOrderByRuleCodeAscEffectiveFromDesc().stream().filter(r -> r.isActive() && r.getRuleCode().equals(request.ruleCode())).anyMatch(r -> overlaps(request.effectiveFrom(), request.effectiveTo(), r.getEffectiveFrom(), r.getEffectiveTo()));
        if (overlaps) throw bad("The effective period overlaps an active rule. Close the prior rule explicitly before introducing a replacement.");
        TaxRuleVersion r = new TaxRuleVersion();
        r.setRuleCode(request.ruleCode()); r.setVersion(request.version()); r.setEffectiveFrom(request.effectiveFrom()); r.setEffectiveTo(request.effectiveTo());
        r.setRate(request.rate()); r.setLowerThreshold(request.lowerThreshold()); r.setUpperThreshold(request.upperThreshold()); r.setCurrency("KES");
        r.setSourceUrl(request.sourceUrl()); r.setSourceNote(request.sourceNote()); r.setApprovedBy(users.getUserId()); r.setApprovedAt(ZonedDateTime.now()); r.setActive(true);
        return ruleView(rules.save(r));
    }

    @Transactional
    public RuleView closeRule(long id, RuleCloseRequest request) {
        TaxRuleVersion r = rules.findById(id).orElseThrow(TaxAssistService::notFound);
        if (r.getEffectiveTo() != null) throw bad("This rule already has a closing date.");
        if (request.effectiveTo().isBefore(r.getEffectiveFrom())) throw bad("Effective-to date cannot precede effective-from date.");
        if (request.effectiveTo().isBefore(LocalDate.now().minusDays(1))) throw bad("A tax rule cannot be closed retrospectively through this control.");
        r.setEffectiveTo(request.effectiveTo());
        return ruleView(rules.save(r));
    }

    @Transactional(readOnly = true)
    public Page<ConnectionView> adminConnections(Pageable pageable) {
        Pageable bounded = PageRequest.of(Math.max(0, pageable.getPageNumber()), Math.min(100, Math.max(1, pageable.getPageSize())));
        return connections.findAllByOrderByCreatedOnDesc(bounded).map(this::connectionView);
    }

    @Transactional
    public ConnectionView reviewConnection(long id, ConnectionReview review) {
        TaxConnectionRequest c = connections.findById(id).orElseThrow(TaxAssistService::notFound);
        if (!c.isActive()) throw bad("A disconnected request cannot be reviewed.");
        if (!CONNECTION_TRANSITIONS.getOrDefault(c.getStatus(), Set.of()).contains(review.status())) throw bad("This connection status transition is not permitted.");
        c.setStatus(review.status()); c.setReviewNote(review.reviewNote().trim()); c.setReviewedBy(users.getUserId()); c.setReviewedAt(ZonedDateTime.now());
        return connectionView(connections.save(c));
    }

    private CalculationView save(String type, String period, BigDecimal gross, BigDecimal taxable, BigDecimal tax,
                                 BigDecimal credit, BigDecimal payable, String outcome, String explanation,
                                 LocalDate due, Object input, TaxRuleVersion rule) {
        TaxCalculation c = new TaxCalculation();
        c.setOwnerUserId(users.getUserId()); c.setCalculationType(type); c.setRuleVersionId(rule.getId()); c.setTaxPeriod(period); c.setCurrency(rule.getCurrency());
        c.setGrossAmount(money(gross)); c.setTaxableAmount(money(taxable)); c.setEstimatedTax(money(tax)); c.setCreditAmount(money(credit)); c.setEstimatedPayable(money(payable));
        c.setOutcome(outcome); c.setExplanation(explanation); c.setDueDate(due); c.setInputSnapshot(toJson(input)); c.setRuleSnapshot(toJson(ruleView(rule)));
        return view(calculations.save(c));
    }

    private TaxRuleVersion effective(String code, LocalDate date) {
        return rules.effectiveCandidates(code, date, PageRequest.of(0, 1)).stream().findFirst().orElseThrow(() -> new IllegalStateException("No approved tax rule is configured for " + code + " on " + date));
    }
    private CalculationView view(TaxCalculation c) {
        return new CalculationView(c.getId(), c.getCalculationType(), c.getTaxPeriod(), c.getCurrency(), c.getGrossAmount(), c.getTaxableAmount(), c.getEstimatedTax(), c.getCreditAmount(), c.getEstimatedPayable(), c.getOutcome(), c.getExplanation(), c.getDueDate(), ruleView(rules.findById(c.getRuleVersionId()).orElseThrow(() -> new IllegalStateException("Calculation rule snapshot is unavailable"))), c.getCreatedOn());
    }
    private RuleView ruleView(TaxRuleVersion r) { return new RuleView(r.getId(), r.getRuleCode(), r.getVersion(), r.getEffectiveFrom(), r.getEffectiveTo(), r.getRate(), r.getLowerThreshold(), r.getUpperThreshold(), r.getCurrency(), r.getSourceUrl(), r.getSourceNote(), r.isActive()); }
    private ConnectionView connectionView(TaxConnectionRequest c) { return new ConnectionView(c.getId(), c.getProvider(), c.getTaxpayerPinMasked(), Set.copyOf(Arrays.asList(c.getRequestedScopes().split(","))), c.getConsentVersion(), c.getConsentedAt(), c.getStatus(), c.getEnvironment(), c.getReviewNote(), c.getCreatedOn()); }
    private String toJson(Object value) { try { return json.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException("Could not preserve the calculation audit snapshot", e); } }
    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private static BigDecimal zero() { return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP); }
    private static boolean overlaps(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) { return (aEnd == null || !aEnd.isBefore(bStart)) && (bEnd == null || !bEnd.isBefore(aStart)); }
    private static String maskPin(String pin) { String normalized = pin.toUpperCase(Locale.ROOT); return normalized.substring(0, 1) + "*********" + normalized.substring(normalized.length() - 1); }
    private static void validateScopes(String provider, Set<String> scopes) {
        Set<String> allowed = provider.equals("ETIMS") ? Set.of("ISSUE_TAX_INVOICES", "QUERY_INVOICE_STATUS") : Set.of("FILE_TAX_RETURN", "CREATE_PAYMENT_REFERENCE", "QUERY_SUBMISSION_STATUS");
        if (!allowed.containsAll(scopes)) throw bad("A requested scope is not available for this provider.");
    }
    private TaxAssistConfiguration configurationEntity() {
        return configurations.findById(1L).orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Tax Assist configuration is unavailable"));
    }
    private void requireEstimatesEnabled() {
        if (!configurationEntity().isEstimatesEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Tax estimates are temporarily paused by the Slickhood system owner.");
    }
    private static ConfigurationView configurationView(TaxAssistConfiguration c) {
        return new ConfigurationView(c.isEstimatesEnabled(), c.isConnectionRequestsEnabled(), false,
                c.getLegalNoticeVersion(), c.getUpdatedBy(), c.getUpdatedAt());
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Tax Assist record not found"); }
}
