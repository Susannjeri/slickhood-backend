package org.pms.silverocean.service.leasedocument;

import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.LeaseDocumentRepo;
import org.pms.silverocean.database.pms.LeaseDocumentTemplateRepo;
import org.pms.silverocean.database.pms.PropertyRepo;
import org.pms.silverocean.database.pms.entities.Invite;
import org.pms.silverocean.database.pms.entities.LeaseDocument;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.PropertyOwnership;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.mustache.RenderService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Creates the immutable homeowner agreement frozen when an estate invitation is sent. */
@Service
public class HomeownerAgreementService {
    private final LeaseDocumentRepo documents;
    private final LeaseDocumentTemplateRepo templates;
    private final PropertyRepo properties;
    private final UserDao users;
    private final RenderService renderer;
    private final DocumentBrandingService branding;

    public HomeownerAgreementService(LeaseDocumentRepo documents, LeaseDocumentTemplateRepo templates,
                                     PropertyRepo properties, UserDao users, RenderService renderer,
                                     DocumentBrandingService branding) {
        this.documents = documents;
        this.templates = templates;
        this.properties = properties;
        this.users = users;
        this.renderer = renderer;
        this.branding = branding;
    }

    public LeaseDocument createIssuedAgreement(PropertyOwnership ownership, Unit unit, Invite invite, Users homeowner) {
        if (ownership == null || unit == null || invite == null || homeowner == null
                || invite.getAgreementTemplateId() == null || invite.getLeaseStartDate() == null
                || !Objects.equals(ownership.getUnitId(), unit.getId())
                || ownership.getHomeownerUserId() != homeowner.getId()
                || !invite.getLeaseStartDate().equals(ownership.getOwnershipStart())
                || (ownership.getCreatedOn() != null && documents.existsCurrentEstateAgreement(
                ownership.getPropertyId(), unit.getId(), homeowner.getId(), ownership.getOwnershipStart(), ownership.getCreatedOn()))) {
            throw invalid();
        }
        Property property = properties.findById(ownership.getPropertyId()).filter(Property::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
        // The authorized estate manager who sent the invitation is the agreement
        // issuer and therefore the party who must countersign it.
        long issuerId = invite.getCreatedBy();
        if (homeowner.getId() == issuerId) throw invalid();
        Users issuer = users.findById(issuerId).filter(Users::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LOAD_USER_ERROR));
        var template = templates.findById(invite.getAgreementTemplateId())
                .filter(value -> value.isActive()
                        && value.getDocumentType() == LeaseDocumentType.ESTATE_RESIDENTIAL_AGREEMENT
                        && !value.isLegalReviewRequired() && value.getLegalReviewedAt() != null
                        && DocumentTemplateIntegrity.sha256(value.getBodyHtml()).equals(value.getContentSha256()))
                .orElseThrow(this::invalid);

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("documentName", template.getDisplayName());
        model.put("propertyName", property.getName());
        model.put("propertyAddress", property.getAddress());
        model.put("propertyType", property.getType());
        model.put("unitRef", unit.getRef());
        model.put("issuerName", issuer.getFullName());
        model.put("issuerEmail", StringUtils.defaultString(issuer.getEmail(), "Not recorded"));
        model.put("issuerPhone", StringUtils.defaultString(issuer.getPhoneNumber(), "Not recorded"));
        model.put("issuerIdentification", StringUtils.defaultString(issuer.getIdentificationNumber(), "Not recorded"));
        model.put("recipientName", homeowner.getFullName());
        model.put("recipientEmail", StringUtils.defaultString(homeowner.getEmail(), "Not recorded"));
        model.put("recipientPhone", StringUtils.defaultString(homeowner.getPhoneNumber(), "Not recorded"));
        model.put("recipientIdentification", StringUtils.defaultString(homeowner.getIdentificationNumber(), "Not recorded"));
        model.put("effectiveDate", ownership.getOwnershipStart().toString());
        model.put("responseDueDate", "Not applicable");
        model.put("amount", "Not applicable");
        model.put("currency", property.getCurrency());
        model.put("reason", "Estate services and homeowner responsibilities for the assigned home.");
        model.put("hasAdditionalTerms", false);
        model.put("legalReviewRequired", false);
        model.put("templateVersion", template.getVersion());
        model.put("saleDocument", false);
        model.put("generatedDate", LocalDate.now(PMSUtils.getZoneId()).toString());
        String ownerLogo = branding.dataUri(issuerId);
        model.put("hasOwnerLogo", StringUtils.isNotBlank(ownerLogo));
        model.put("ownerLogoDataUri", ownerLogo);
        model.put("documentOwnerName", StringUtils.defaultIfBlank(issuer.getOrganizationName(), issuer.getFullName()));

        LeaseDocument document = new LeaseDocument();
        document.setPropertyId(property.getId());
        document.setUnitId(unit.getId());
        document.setTemplateId(template.getId());
        document.setTemplateVersion(template.getVersion());
        document.setDocumentType(LeaseDocumentType.ESTATE_RESIDENTIAL_AGREEMENT);
        document.setStatus(LeaseDocumentStatus.ISSUED);
        document.setName(template.getDisplayName());
        document.setRenderedHtml(renderer.renderInline(template.getBodyHtml(), model));
        document.setIssuerUserId(issuerId);
        document.setRecipientUserId(homeowner.getId());
        document.setEffectiveDate(ownership.getOwnershipStart());
        document.setCurrency(property.getCurrency());
        document.setDeliveryChannel("EMAIL_INVITE_AND_IN_APP");
        document.setLegalReviewRequired(false);
        document.setIssuedAt(LocalDateTime.now());
        document.setCreatedBy(issuerId);
        document.setActive(true);
        return documents.save(document);
    }

    private PMSCustomException invalid() {
        return new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
    }
}
