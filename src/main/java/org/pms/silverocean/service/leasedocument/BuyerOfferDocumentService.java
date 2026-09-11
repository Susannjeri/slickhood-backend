package org.pms.silverocean.service.leasedocument;

import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.LeaseDocumentRepo;
import org.pms.silverocean.database.pms.LeaseDocumentTemplateRepo;
import org.pms.silverocean.database.pms.PropertyRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.entities.Invite;
import org.pms.silverocean.database.pms.entities.LeaseDocument;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.SaleTransaction;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.mustache.RenderService;
import org.pms.silverocean.service.sales.SaleStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Creates the immutable Letter of Offer frozen when a buyer invitation is sent. */
@Service
public class BuyerOfferDocumentService {
    private final LeaseDocumentRepo documents;
    private final LeaseDocumentTemplateRepo templates;
    private final PropertyRepo properties;
    private final UnitRepo units;
    private final UserDao users;
    private final RenderService renderer;
    private final DocumentBrandingService branding;

    public BuyerOfferDocumentService(LeaseDocumentRepo documents, LeaseDocumentTemplateRepo templates,
                                     PropertyRepo properties, UnitRepo units, UserDao users,
                                     RenderService renderer, DocumentBrandingService branding) {
        this.documents = documents;
        this.templates = templates;
        this.properties = properties;
        this.units = units;
        this.users = users;
        this.renderer = renderer;
        this.branding = branding;
    }

    public LeaseDocument createIssuedOffer(SaleTransaction sale, Invite invite, Users buyer) {
        if (sale == null || invite == null || buyer == null || sale.getId() == null
                || invite.getAgreementTemplateId() == null || invite.getLeaseEndDate() == null
                || sale.getStatus() != SaleStatus.OFFERED || sale.getOfferAmount() == null
                || !Objects.equals(invite.getEntityId(), sale.getId())
                || !Objects.equals(sale.getBuyerUserId(), buyer.getId())
                || !StringUtils.equalsIgnoreCase(invite.getRecipient(), buyer.getEmail())
                || !invite.getLeaseEndDate().isAfter(LocalDate.now(PMSUtils.getZoneId()))) {
            throw invalid();
        }
        if (documents.existsOpenForSale(sale.getId(), LeaseDocumentType.PROPERTY_SALE_LETTER_OF_OFFER)) {
            return null;
        }
        Property property = properties.findById(sale.getPropertyId()).filter(Property::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
        Unit unit = units.findById(sale.getUnitId()).filter(value -> value.isActive()
                        && Objects.equals(value.getPropertyId(), property.getId()) && "SALE".equals(value.getLeaseMode()))
                .orElseThrow(() -> new PMSCustomException(ResponseCode.UNIT_NOT_FOUND));
        long issuerId = invite.getCreatedBy();
        if (buyer.getId() == issuerId) throw invalid();
        Users issuer = users.findById(issuerId).filter(Users::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LOAD_USER_ERROR));
        var template = templates.findById(invite.getAgreementTemplateId())
                .filter(value -> value.isActive()
                        && value.getDocumentType() == LeaseDocumentType.PROPERTY_SALE_LETTER_OF_OFFER
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
        model.put("recipientName", buyer.getFullName());
        model.put("recipientEmail", StringUtils.defaultString(buyer.getEmail(), "Not recorded"));
        model.put("recipientPhone", StringUtils.defaultString(buyer.getPhoneNumber(), "Not recorded"));
        model.put("recipientIdentification", StringUtils.defaultString(buyer.getIdentificationNumber(), "Not recorded"));
        model.put("effectiveDate", LocalDate.now(PMSUtils.getZoneId()).toString());
        model.put("responseDueDate", invite.getLeaseEndDate().toString());
        model.put("amount", sale.getOfferAmount().toPlainString());
        model.put("currency", sale.getCurrency());
        model.put("reason", "The recorded offer for this property sale transaction.");
        model.put("hasAdditionalTerms", false);
        model.put("legalReviewRequired", false);
        model.put("templateVersion", template.getVersion());
        model.put("saleDocument", true);
        model.put("askingPrice", sale.getAskingPrice().toPlainString());
        model.put("offerAmount", sale.getOfferAmount().toPlainString());
        model.put("generatedDate", LocalDate.now(PMSUtils.getZoneId()).toString());
        String ownerLogo = branding.dataUri(property.getCreatedBy());
        Users documentOwner = users.findById(property.getCreatedBy()).orElse(issuer);
        model.put("hasOwnerLogo", StringUtils.isNotBlank(ownerLogo));
        model.put("ownerLogoDataUri", ownerLogo);
        model.put("documentOwnerName", StringUtils.defaultIfBlank(documentOwner.getOrganizationName(), documentOwner.getFullName()));

        LeaseDocument document = new LeaseDocument();
        document.setSaleId(sale.getId());
        document.setPropertyId(property.getId());
        document.setUnitId(unit.getId());
        document.setTemplateId(template.getId());
        document.setTemplateVersion(template.getVersion());
        document.setDocumentType(LeaseDocumentType.PROPERTY_SALE_LETTER_OF_OFFER);
        document.setStatus(LeaseDocumentStatus.ISSUED);
        document.setName(template.getDisplayName());
        document.setRenderedHtml(renderer.renderInline(template.getBodyHtml(), model));
        document.setIssuerUserId(issuerId);
        document.setRecipientUserId(buyer.getId());
        document.setEffectiveDate(LocalDate.now(PMSUtils.getZoneId()));
        document.setResponseDueDate(invite.getLeaseEndDate());
        document.setAmount(sale.getOfferAmount());
        document.setCurrency(sale.getCurrency());
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
