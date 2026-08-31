package org.pms.silverocean.service.leasedocument;

import org.pms.silverocean.database.pms.LeaseDocumentTemplateRepo;
import org.pms.silverocean.database.pms.entities.LeaseDocumentTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class LeaseDocumentTemplateBootstrap implements ApplicationRunner {
    private final LeaseDocumentTemplateRepo repo;
    private final boolean legalReviewRequired;

    public LeaseDocumentTemplateBootstrap(LeaseDocumentTemplateRepo repo,
            @Value("${lease.documents.legal-review-required:true}") boolean legalReviewRequired) {
        this.repo = repo;
        this.legalReviewRequired = legalReviewRequired;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (LeaseDocumentType type : LeaseDocumentType.values()) {
            if (type.isLegacy()) continue;
            if (!repo.existsByDocumentTypeAndActiveTrue(type)) {
                LeaseDocumentTemplate template = new LeaseDocumentTemplate();
                template.setDocumentType(type);
                template.setDisplayName(title(type));
                template.setVersion(1);
                template.setBodyHtml(html(type));
                template.setLegalReviewRequired(legalReviewRequired);
                template.setActive(true);
                repo.save(template);
            }
        }
    }

    private String title(LeaseDocumentType type) {
        return switch (type) {
            case RESIDENTIAL_LEASE_AGREEMENT -> "Residential Lease Agreement";
            case COMMERCIAL_LEASE_AGREEMENT -> "Commercial Lease Agreement";
            case LATE_RENT_NOTICE -> "Late Rent Notice";
            case RENT_DEFAULT_CURE_NOTICE -> "Rent Default and Cure Notice";
            case LANDLORD_TERMINATION_NOTICE -> "Landlord Notice of Termination";
            case TENANT_TERMINATION_NOTICE -> "Tenant Notice of Termination";
            case ESTATE_RESIDENTIAL_AGREEMENT -> "Estate Residential Agreement";
            case PROPERTY_SALE_LETTER_OF_OFFER -> "Property Sale Letter of Offer";
            case PROPERTY_SALE_AGREEMENT -> "Property Sale Agreement";
            case RENTAL_LETTER_OF_OFFER, ESTATE_AGREEMENT -> throw new IllegalArgumentException("Legacy document type");
        };
    }

    private String html(LeaseDocumentType type) {
        String detail = switch (type) {
            case RENTAL_LETTER_OF_OFFER -> throw new IllegalArgumentException("Legacy document type");
            case RESIDENTIAL_LEASE_AGREEMENT -> "This agreement records the residential tenancy, rent, term, deposit, maintenance, notice and house-rule obligations accepted by the parties.";
            case COMMERCIAL_LEASE_AGREEMENT -> "This agreement records the commercial premises, permitted use, rent, term, deposit, maintenance, access, insurance and notice obligations accepted by the parties.";
            case LATE_RENT_NOTICE -> "Our records show that rent is overdue. Please review the amount below and follow the document owner's payment instructions, or contact the issuer to dispute the balance.";
            case RENT_DEFAULT_CURE_NOTICE -> "This is a formal notice of payment default and an opportunity to cure it by the response date. It is not an automated eviction order.";
            case LANDLORD_TERMINATION_NOTICE -> "The issuer gives notice of the proposed tenancy termination on the effective date, subject to the agreement and applicable law.";
            case TENANT_TERMINATION_NOTICE -> "The tenant gives notice of the intention to end the tenancy on the effective date and requests move-out, inspection and deposit-settlement arrangements.";
            case ESTATE_AGREEMENT -> throw new IllegalArgumentException("Legacy document type");
            case ESTATE_RESIDENTIAL_AGREEMENT -> "This residential agreement records homeowner or resident onboarding, service charges, community rules, security, visitors, common areas, maintenance and notices. It does not create a tenancy or transfer title.";
            case PROPERTY_SALE_LETTER_OF_OFFER -> "This letter records the proposed purchase price, response deadline and principal sale terms. It is linked to the sale transaction and remains subject to due diligence and the advocate-reviewed agreement for sale.";
            case PROPERTY_SALE_AGREEMENT -> "This agreement records the sale parties, property, due-diligence and completion milestones. SlickHood is a workflow platform and does not replace advocates, title due diligence or statutory transfer.";
        };
        return """
                <!doctype html><html><head><meta charset=\"UTF-8\"><style>
                @page{margin:20mm}body{font-family:Arial,sans-serif;color:#17212b;line-height:1.5}h1{color:#0f766e;font-size:23px}
                .brand{display:flex;align-items:center;gap:14px;margin-bottom:22px}.brand img{max-width:150px;max-height:72px;object-fit:contain}.brand-name{font-size:18px;font-weight:700}
                .meta{background:#f1f5f9;padding:12px;border-radius:6px}.review{border:1px solid #d97706;background:#fffbeb;padding:10px;margin:14px 0}
                .sig{margin-top:40px;display:flex;justify-content:space-between}.sig div{width:45%%;border-top:1px solid #333;padding-top:6px}.powered{margin-top:44px;border-top:1px solid #e2e8f0;padding-top:10px;text-align:center;color:#64748b;font-size:11px}
                </style></head><body><div class="brand">{{#hasOwnerLogo}}<img src="{{ownerLogoDataUri}}" alt="Document owner logo">{{/hasOwnerLogo}}<div class="brand-name">{{documentOwnerName}}</div></div><h1>{{documentName}}</h1>
                <div class=\"meta\"><strong>Property:</strong> {{propertyName}}<br><strong>Address:</strong> {{propertyAddress}}<br>
                <strong>Unit:</strong> {{unitRef}}<br><strong>Issuer:</strong> {{issuerName}}<br><strong>Recipient:</strong> {{recipientName}}<br>
                <strong>Effective date:</strong> {{effectiveDate}}<br><strong>Response due:</strong> {{responseDueDate}}</div>
                <p>%s</p><p><strong>Amount:</strong> {{currency}} {{amount}}</p><p><strong>Reason / terms:</strong> {{reason}}</p>
                {{#legalReviewRequired}}<div class=\"review\">Pending legal approval. Review this template for the relevant property, agreement and laws of Kenya before issue.</div>{{/legalReviewRequired}}
                <p>This generated copy is an immutable snapshot of template version {{templateVersion}}. Any payment is made to the beneficiary identified by the document owner or linked payment instruction; SlickHood is not the contracting party or recipient of funds.</p>
                <div class=\"sig\"><div>Issuer signature / date</div><div>Recipient signature / date</div></div>
                {{#saleDocument}}<div class=\"sig\"><div>Issuer witness / attestation</div><div>Recipient witness / attestation</div></div>{{/saleDocument}}
                <div class="powered">Powered by SlickHood</div></body></html>""".formatted(detail);
    }
}
