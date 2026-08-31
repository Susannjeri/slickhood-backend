package org.pms.silverocean.service.leasedocument;

import org.pms.silverocean.database.pms.LeaseDocumentTemplateRepo;
import org.pms.silverocean.database.pms.entities.LeaseDocumentTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

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
        return Map.of(
                LeaseDocumentType.RENTAL_LETTER_OF_OFFER, "Residential Tenancy Letter of Offer",
                LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT, "Residential Tenancy Agreement",
                LeaseDocumentType.COMMERCIAL_LEASE_AGREEMENT, "Commercial Lease Agreement",
                LeaseDocumentType.LATE_RENT_NOTICE, "Late Rent Notice",
                LeaseDocumentType.RENT_DEFAULT_CURE_NOTICE, "Rent Default and Cure Notice",
                LeaseDocumentType.LANDLORD_TERMINATION_NOTICE, "Landlord Notice of Termination",
                LeaseDocumentType.TENANT_TERMINATION_NOTICE, "Tenant Notice of Termination",
                LeaseDocumentType.ESTATE_AGREEMENT, "Estate Management Agreement",
                LeaseDocumentType.PROPERTY_SALE_AGREEMENT, "Property Sale Agreement").get(type);
    }

    private String html(LeaseDocumentType type) {
        String detail = switch (type) {
            case RENTAL_LETTER_OF_OFFER -> "This letter offers the identified premises to the prospective tenant on the recorded rent, deposit, commencement date and response deadline. Acceptance remains subject to the final tenancy agreement and any required checks.";
            case RESIDENTIAL_LEASE_AGREEMENT -> "This agreement records the residential tenancy, rent, term, deposit, maintenance, notice and house-rule obligations accepted by the parties.";
            case COMMERCIAL_LEASE_AGREEMENT -> "This agreement records the commercial premises, permitted use, rent, term, deposit, maintenance, access, insurance and notice obligations accepted by the parties.";
            case LATE_RENT_NOTICE -> "Our records show that rent is overdue. Please review the amount below and pay through the SlickHood checkout or contact the issuer to dispute the balance.";
            case RENT_DEFAULT_CURE_NOTICE -> "This is a formal notice of payment default and an opportunity to cure it by the response date. It is not an automated eviction order.";
            case LANDLORD_TERMINATION_NOTICE -> "The issuer gives notice of the proposed tenancy termination on the effective date, subject to the agreement and applicable law.";
            case TENANT_TERMINATION_NOTICE -> "The tenant gives notice of the intention to end the tenancy on the effective date and requests move-out, inspection and deposit-settlement arrangements.";
            case ESTATE_AGREEMENT -> "This agreement records estate onboarding, service charges, community rules, security, visitors, common areas, maintenance, notices and electronic acceptance.";
            case PROPERTY_SALE_AGREEMENT -> "This agreement records the sale parties, property, due-diligence and completion milestones. SlickHood is a workflow platform and does not replace advocates, title due diligence or statutory transfer.";
        };
        return """
                <!doctype html><html><head><meta charset=\"UTF-8\"><style>
                @page{margin:20mm}body{font-family:Arial,sans-serif;color:#17212b;line-height:1.5}h1{color:#0f766e;font-size:23px}
                .meta{background:#f1f5f9;padding:12px;border-radius:6px}.review{border:1px solid #d97706;background:#fffbeb;padding:10px;margin:14px 0}
                .sig{margin-top:40px;display:flex;justify-content:space-between}.sig div{width:45%%;border-top:1px solid #333;padding-top:6px}
                </style></head><body><h1>{{documentName}}</h1>
                <div class=\"meta\"><strong>Property:</strong> {{propertyName}}<br><strong>Address:</strong> {{propertyAddress}}<br>
                <strong>Unit:</strong> {{unitRef}}<br><strong>Issuer:</strong> {{issuerName}}<br><strong>Recipient:</strong> {{recipientName}}<br>
                <strong>Effective date:</strong> {{effectiveDate}}<br><strong>Response due:</strong> {{responseDueDate}}</div>
                <p>%s</p><p><strong>Amount:</strong> {{currency}} {{amount}}</p><p><strong>Reason / terms:</strong> {{reason}}</p>
                {{#legalReviewRequired}}<div class=\"review\">Pending legal approval. Review this template for the relevant property, agreement and laws of Kenya before issue.</div>{{/legalReviewRequired}}
                <p>This generated copy is an immutable snapshot of template version {{templateVersion}}. Payment references must use the SlickHood invoice and approved checkout route.</p>
                <div class=\"sig\"><div>Issuer signature</div><div>Recipient signature</div></div></body></html>""".formatted(detail);
    }
}
