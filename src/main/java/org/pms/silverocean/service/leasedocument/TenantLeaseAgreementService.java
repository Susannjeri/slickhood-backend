package org.pms.silverocean.service.leasedocument;

import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.LeaseDocumentRepo;
import org.pms.silverocean.database.pms.LeaseDocumentTemplateRepo;
import org.pms.silverocean.database.pms.PropertyRepo;
import org.pms.silverocean.database.pms.entities.Invite;
import org.pms.silverocean.database.pms.entities.Lease;
import org.pms.silverocean.database.pms.entities.LeaseDocument;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.RoleService;
import org.pms.silverocean.service.lease.LeaseDao;
import org.pms.silverocean.service.mustache.RenderService;
import org.pms.silverocean.service.property.PMSPropertyCategory;
import org.pms.silverocean.service.property.PMSPropertyType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Creates the immutable, tenant-visible agreement from the terms accepted by the landlord at invite time. */
@Service
public class TenantLeaseAgreementService {
    private final LeaseDocumentRepo documents;
    private final LeaseDocumentTemplateRepo templates;
    private final PropertyRepo properties;
    private final UserDao users;
    private final LeaseDao leases;
    private final RenderService renderer;
    private final DocumentBrandingService branding;
    private final RoleService roles;

    public TenantLeaseAgreementService(LeaseDocumentRepo documents, LeaseDocumentTemplateRepo templates,
                                       PropertyRepo properties, UserDao users, LeaseDao leases,
                                       RenderService renderer, DocumentBrandingService branding, RoleService roles) {
        this.documents = documents;
        this.templates = templates;
        this.properties = properties;
        this.users = users;
        this.leases = leases;
        this.renderer = renderer;
        this.branding = branding;
        this.roles = roles;
    }

    public LeaseDocument createIssuedAgreement(Lease lease, Unit unit, Invite invite, Users tenant) {
        if (lease == null || unit == null || invite == null || tenant == null
                || invite.getAgreementTemplateId() == null || invite.getLeaseStartDate() == null
                || invite.getLeaseEndDate() == null || !invite.getLeaseEndDate().isAfter(invite.getLeaseStartDate())
                || !invite.getLeaseStartDate().equals(lease.getMoveInDate())
                || !invite.getLeaseEndDate().equals(lease.getMoveOutDate())
                || documents.existsCurrentAgreement(lease.getId())) {
            throw invalid();
        }
        Property property = properties.findById(unit.getPropertyId()).filter(Property::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
        if (invite.getCreatedBy() == null || (!Objects.equals(property.getCreatedBy(), invite.getCreatedBy())
                && !roles.checkIfStaffInProperty(invite.getCreatedBy(), unit.getId()))) throw invalid();
        // The property owner remains the legal issuer even when an authorised manager sends the assignment.
        long issuerId = property.getCreatedBy();
        if (tenant.getId() == issuerId) throw invalid();
        Users issuer = users.findById(issuerId).filter(Users::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LOAD_USER_ERROR));

        LeaseDocumentType expectedType = agreementType(property);
        var template = templates.findById(invite.getAgreementTemplateId())
                .filter(value -> value.isActive() && value.getDocumentType() == expectedType
                        && !value.isLegalReviewRequired() && value.getLegalReviewedAt() != null
                        && DocumentTemplateIntegrity.sha256(value.getBodyHtml()).equals(value.getContentSha256()))
                .orElseThrow(this::invalid);

        Map<String, Object> model = model(property, unit, issuer, tenant, lease, template.getVersion());
        String schedule = "<section><h2>Recorded lease schedule</h2>"
                + "<h3>Payment timing</h3><p>The first month's rent is due on {{firstRentDueDate}}. "
                + "Any one-time deposit recorded below is also due on {{depositDueDate}}. "
                + "After the first payment, recurring rent is payable in advance by day {{rentDueDay}} of each month.</p>"
                + "<p>The initial invoice is issued when both parties have signed and must be paid by the stated due date.</p>"
                + "<p>Pet policy: {{petsPolicy}}</p>"
                + "<h3>Additional charges</h3>{{#leaseCharges}}<p>{{name}}: {{currency}} {{amount}} ({{period}})</p>{{/leaseCharges}}"
                + "{{^leaseCharges}}<p>No additional charges recorded.</p>{{/leaseCharges}}</section>";
        String body = insertBeforeBodyEnd(template.getBodyHtml(), schedule);

        LeaseDocument document = new LeaseDocument();
        document.setLeaseId(lease.getId());
        document.setPropertyId(property.getId());
        document.setUnitId(unit.getId());
        document.setTemplateId(template.getId());
        document.setTemplateVersion(template.getVersion());
        document.setDocumentType(expectedType);
        // Assigning the tenant explicitly authorizes issue of this approved, frozen template.
        document.setStatus(LeaseDocumentStatus.ISSUED);
        document.setName(template.getDisplayName());
        document.setRenderedHtml(renderer.renderInline(body, model));
        document.setIssuerUserId(issuerId);
        document.setRecipientUserId(tenant.getId());
        document.setEffectiveDate(lease.getMoveInDate());
        document.setAmount(BigDecimal.valueOf(lease.getPrice()));
        document.setCurrency(lease.getCurrency());
        document.setDeliveryChannel("EMAIL_INVITE_AND_IN_APP");
        document.setLegalReviewRequired(false);
        document.setIssuedAt(LocalDateTime.now());
        document.setCreatedBy(issuerId);
        document.setActive(true);
        return documents.save(document);
    }

    private LeaseDocumentType agreementType(Property property) {
        try {
            PMSPropertyCategory category = PMSPropertyType.valueOf(property.getType()).getCategory();
            return category == PMSPropertyCategory.COMMERCIAL || category == PMSPropertyCategory.INDUSTRIAL
                    ? LeaseDocumentType.COMMERCIAL_LEASE_AGREEMENT
                    : LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT;
        } catch (RuntimeException ignored) {
            return LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT;
        }
    }

    private Map<String, Object> model(Property property, Unit unit, Users issuer, Users tenant,
                                      Lease lease, int templateVersion) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("documentName", agreementType(property) == LeaseDocumentType.COMMERCIAL_LEASE_AGREEMENT
                ? "Commercial Lease Agreement" : "Residential Lease Agreement");
        model.put("propertyName", property.getName());
        model.put("propertyAddress", property.getAddress());
        model.put("unitRef", unit.getRef());
        model.put("issuerName", issuer.getFullName());
        model.put("recipientName", tenant.getFullName());
        model.put("effectiveDate", lease.getMoveInDate().toString());
        model.put("responseDueDate", "Not applicable");
        model.put("amount", BigDecimal.valueOf(lease.getPrice()).toPlainString());
        model.put("currency", lease.getCurrency());
        model.put("reason", "As recorded in the related agreement and workflow.");
        model.put("hasAdditionalTerms", false);
        model.put("legalReviewRequired", false);
        model.put("templateVersion", templateVersion);
        model.put("saleDocument", false);
        model.put("generatedDate", LocalDate.now(PMSUtils.getZoneId()).toString());
        model.put("issuerEmail", StringUtils.defaultString(issuer.getEmail(), "Not recorded"));
        model.put("issuerPhone", StringUtils.defaultString(issuer.getPhoneNumber(), "Not recorded"));
        model.put("issuerIdentification", StringUtils.defaultString(issuer.getIdentificationNumber(), "Not recorded"));
        model.put("recipientEmail", StringUtils.defaultString(tenant.getEmail(), "Not recorded"));
        model.put("recipientPhone", StringUtils.defaultString(tenant.getPhoneNumber(), "Not recorded"));
        model.put("recipientIdentification", StringUtils.defaultString(tenant.getIdentificationNumber(), "Not recorded"));
        model.put("propertyType", property.getType());
        model.put("leaseDate", lease.getLeaseDate().toString());
        model.put("moveInDate", lease.getMoveInDate().toString());
        model.put("moveOutDate", lease.getMoveOutDate().toString());
        model.put("rentDueDay", value(lease.getRentDueDayOfMonth()));
        model.put("firstRentDueDate", lease.getMoveInDate().toString());
        model.put("depositDueDate", lease.getMoveInDate().toString());
        model.put("leaseDurationMonths", value(lease.getLeaseDurationInMonths()));
        model.put("noticePeriodMonths", value(lease.getNoticePeriodInMonths()));
        model.put("depositReturnDays", value(lease.getDepositReturnDays()));
        model.put("repairThreshold", value(lease.getRepairThreshold()));
        model.put("entryNoticeDays", value(lease.getEntryNoticeDays()));
        model.put("selfRenewing", lease.isSelfRenew());
        model.put("petsPolicy", lease.getPetsPolicy() == null ? "Not recorded" : new String(lease.getPetsPolicy(), java.nio.charset.StandardCharsets.UTF_8));
        model.put("leaseCharges", leases.getLeaseChargeByLeaseId(lease.getId()).stream().map(charge -> Map.of(
                "name", StringUtils.defaultIfBlank(charge.getChargeName(), "Charge " + charge.getChargeId()),
                "currency", lease.getCurrency(),
                "amount", BigDecimal.valueOf(charge.getAmount()).toPlainString(),
                "period", StringUtils.defaultIfBlank(charge.getPeriodId(), "Not recorded"))).toList());
        String ownerLogo = branding.dataUri(issuer.getId());
        model.put("hasOwnerLogo", StringUtils.isNotBlank(ownerLogo));
        model.put("ownerLogoDataUri", ownerLogo);
        model.put("documentOwnerName", StringUtils.defaultIfBlank(issuer.getOrganizationName(), issuer.getFullName()));
        return model;
    }

    private String insertBeforeBodyEnd(String html, String insertion) {
        var matcher = java.util.regex.Pattern.compile("(?i)</body\\s*>").matcher(html);
        return matcher.find() ? html.substring(0, matcher.start()) + insertion + html.substring(matcher.start()) : html + insertion;
    }

    private String value(Object value) { return value == null ? "Not recorded" : value.toString(); }
    private PMSCustomException invalid() { return new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE); }
}
