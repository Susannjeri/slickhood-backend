package org.pms.silverocean.service.leasedocument;

import org.pms.silverocean.database.pms.LeaseDocumentTemplateRepo;
import org.pms.silverocean.database.pms.entities.LeaseDocumentTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

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
    public void run(ApplicationArguments args) throws IOException {
        for (LeaseDocumentType type : LeaseDocumentType.values()) {
            if (type.isLegacy()) continue;
            var active = repo.findFirstByDocumentTypeAndActiveTrueOrderByVersionDesc(type);
            if (active.isPresent() && active.get().getContentSha256() == null) {
                // Expand/contract safety: a rollback artifact may create a row without the new integrity field.
                // Preserve the wording, hash it, and require approval again instead of trusting unknown content.
                active.get().setContentSha256(DocumentTemplateIntegrity.sha256(active.get().getBodyHtml()));
                active.get().setLegalReviewRequired(true);
                active.get().setLegalReviewedAt(null);
                active.get().setLegalReviewedBy(null);
                repo.save(active.get());
            }
            if (active.isPresent() && isGenericStarter(active.get())) {
                active.get().setActive(false);
                repo.save(active.get());
                active = java.util.Optional.empty();
            }
            if (active.isEmpty()) {
                LeaseDocumentTemplate template = new LeaseDocumentTemplate();
                template.setDocumentType(type);
                template.setDisplayName(title(type));
                template.setVersion(repo.findFirstByDocumentTypeOrderByVersionDesc(type)
                        .map(previous -> previous.getVersion() + 1).orElse(1));
                String bodyHtml = html(type);
                template.setBodyHtml(bodyHtml);
                template.setContentSha256(DocumentTemplateIntegrity.sha256(bodyHtml));
                template.setLegalReviewRequired(legalReviewRequired);
                if (!legalReviewRequired) template.setLegalReviewedAt(LocalDateTime.now());
                template.setActive(true);
                repo.save(template);
            }
        }
    }

    private boolean isGenericStarter(LeaseDocumentTemplate template) {
        String body = template.getBodyHtml();
        return body != null && (body.contains("This agreement records the residential tenancy")
                || body.contains("This agreement records the commercial premises")
                || body.contains("Our records show that rent is overdue")
                || body.contains("This is a formal notice of payment default")
                || body.contains("The issuer gives notice of the proposed tenancy termination")
                || body.contains("The tenant gives notice of the intention to end the tenancy")
                || body.contains("This residential agreement records homeowner or resident onboarding")
                || body.contains("This letter records the proposed purchase price")
                || body.contains("This agreement records the sale parties"));
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

    private String html(LeaseDocumentType type) throws IOException {
        String shell = resource("templates/documents/document-shell.mustache");
        return shell.replace("<!--DOCUMENT_BODY-->", resource("templates/documents/" + fileName(type)));
    }

    private String fileName(LeaseDocumentType type) {
        return switch (type) {
            case RESIDENTIAL_LEASE_AGREEMENT -> "residential-lease.html";
            case COMMERCIAL_LEASE_AGREEMENT -> "commercial-lease.html";
            case LATE_RENT_NOTICE -> "late-rent-notice.html";
            case RENT_DEFAULT_CURE_NOTICE -> "rent-default-cure-notice.html";
            case LANDLORD_TERMINATION_NOTICE -> "landlord-termination-notice.html";
            case TENANT_TERMINATION_NOTICE -> "tenant-termination-notice.html";
            case ESTATE_RESIDENTIAL_AGREEMENT -> "estate-residential-agreement.html";
            case PROPERTY_SALE_LETTER_OF_OFFER -> "property-sale-letter-of-offer.html";
            case PROPERTY_SALE_AGREEMENT -> "property-sale-agreement.html";
            case RENTAL_LETTER_OF_OFFER, ESTATE_AGREEMENT -> throw new IllegalArgumentException("Legacy document type");
        };
    }

    private String resource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
