package org.pms.silverocean.service.leasedocument;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.lease.LeaseDao;
import org.pms.silverocean.service.lease.LeaseService;
import org.pms.silverocean.service.mustache.RenderService;
import org.pms.silverocean.service.notification.email.EmailService;
import org.pms.silverocean.service.sales.SaleStatus;
import org.pms.silverocean.service.sales.SalesService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaseDocumentServiceTest {
    @Mock LeaseDocumentRepo documents;
    @Mock LeaseDocumentTemplateRepo templates;
    @Mock LeaseDao leases;
    @Mock PropertyRepo properties;
    @Mock UnitRepo units;
    @Mock UserDao users;
    @Mock RenderService renderer;
    @Mock EmailService email;
    @Mock LeaseService leaseService;
    @Mock SaleTransactionRepo sales;
    @Mock SalesService salesService;
    @Mock DocumentBrandingService brandingService;
    @Mock PropertyOwnershipRepo ownershipRepo;
    LeaseDocumentService service;
    Lease lease;

    @BeforeEach void setup() {
        service = new LeaseDocumentService(documents, templates, leases, properties, units, users, renderer, email,
                leaseService, sales, salesService, brandingService, ownershipRepo);
        lease = new Lease(); lease.setId(11L); lease.setTenantId(12L); lease.setLeaseMode("RENT");
        lease.setPrice(45_000); lease.setCurrency("KES"); lease.setMoveInDate(LocalDate.of(2026, 10, 1)); lease.setActive(true);
        UnitTenant tenancy = new UnitTenant(); tenancy.setId(12L); tenancy.setUnitId(13L); tenancy.setUserId(14L); tenancy.setActive(true);
        Unit unit = new Unit(); unit.setId(13L); unit.setPropertyId(15L); unit.setRef("A-1"); unit.setActive(true);
        Property property = new Property(); property.setId(15L); property.setCreatedBy(16L); property.setName("Acacia"); property.setCurrency("KES"); property.setActive(true);
        Users owner = new Users(); owner.setId(16L); owner.setEmail("owner@example.com"); owner.setFullName("Owner");
        Users tenant = new Users(); tenant.setId(14L); tenant.setEmail("tenant@example.com"); tenant.setFullName("Tenant"); tenant.setActive(true);
        lenient().when(users.getUserId()).thenReturn(16L);
        lenient().when(users.getActiveRole()).thenReturn(PMSRole.LANDLORD);
        lenient().when(users.getUserObject()).thenReturn(owner);
        lenient().when(leases.getLeaseByIdAndOwner(11L, 16L)).thenReturn(Optional.of(lease));
        lenient().when(leases.getUnitTenantByTenantId(12L)).thenReturn(Optional.of(tenancy));
        lenient().when(units.findById(13L)).thenReturn(Optional.of(unit));
        lenient().when(properties.findById(15L)).thenReturn(Optional.of(property));
        lenient().when(users.findById(14L)).thenReturn(Optional.of(tenant));
        lenient().when(users.findById(16L)).thenReturn(Optional.of(owner));
    }

    @Test void rentalAgreementDoesNotRequireASalesLetterOfOffer() {
        when(documents.existsOpen(11L, LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT)).thenReturn(false);
        LeaseDocumentTemplate template = new LeaseDocumentTemplate(); template.setId(21L); template.setVersion(3);
        template.setDisplayName("Residential Lease Agreement"); template.setBodyHtml("<p>{{documentName}}</p>");
        template.setContentSha256(DocumentTemplateIntegrity.sha256(template.getBodyHtml()));
        template.setLegalReviewRequired(true); template.setActive(true);
        when(templates.findFirstByDocumentTypeAndActiveTrueOrderByVersionDesc(LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT)).thenReturn(Optional.of(template));
        when(renderer.renderInline(any(), any())).thenReturn("<p>Residential Lease Agreement</p>");
        when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LeaseDocumentDTO result = service.generate(new GenerateLeaseDocumentRequest(
                11L, null, null, null, LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT,
                lease.getMoveInDate(), null, new BigDecimal("45000.00"), "KES", null));

        assertEquals(LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT, result.documentType());
        @SuppressWarnings("unchecked")
        var modelCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(renderer).renderInline(any(), modelCaptor.capture());
        assertEquals("2026-10-01", modelCaptor.getValue().get("moveInDate"));
        assertEquals(false, modelCaptor.getValue().get("hasAdditionalTerms"));
        assertEquals("Owner", modelCaptor.getValue().get("documentOwnerName"));
    }

    @Test void validPropertySaleLetterOfOfferCreatesSaleLinkedSnapshot() {
        LeaseDocumentTemplate template = new LeaseDocumentTemplate(); template.setId(21L); template.setVersion(3);
        template.setDisplayName("Property Sale Letter of Offer"); template.setBodyHtml("<p>{{documentName}}</p>");
        template.setContentSha256(DocumentTemplateIntegrity.sha256(template.getBodyHtml()));
        template.setLegalReviewRequired(true); template.setActive(true);
        SaleTransaction sale = new SaleTransaction(); sale.setId(91L); sale.setPropertyId(15L); sale.setUnitId(13L);
        sale.setBuyerUserId(14L); sale.setStatus(SaleStatus.OFFERED); sale.setOfferAmount(new BigDecimal("14500000"));
        sale.setCurrency("KES"); sale.setActive(true);
        when(sales.findByIdAndPropertyAccess(91L, 16L)).thenReturn(Optional.of(sale));
        when(documents.existsOpenForSale(91L, LeaseDocumentType.PROPERTY_SALE_LETTER_OF_OFFER)).thenReturn(false);
        when(templates.findFirstByDocumentTypeAndActiveTrueOrderByVersionDesc(LeaseDocumentType.PROPERTY_SALE_LETTER_OF_OFFER)).thenReturn(Optional.of(template));
        when(renderer.renderInline(any(), any())).thenReturn("<p>Property Sale Letter of Offer</p>");
        when(documents.save(any())).thenAnswer(invocation -> { LeaseDocument d = invocation.getArgument(0); d.setId(31L); return d; });

        LeaseDocumentDTO result = service.generate(new GenerateLeaseDocumentRequest(null, 91L, null, null,
                LeaseDocumentType.PROPERTY_SALE_LETTER_OF_OFFER, null, LocalDate.now().plusDays(7),
                new BigDecimal("14500000.00"), "KES", "Subject to due diligence"));

        assertEquals(LeaseDocumentStatus.DRAFT, result.status());
        assertEquals(3, result.templateVersion());
        assertEquals(91L, result.saleId());
        assertEquals(LeaseDocumentType.PROPERTY_SALE_LETTER_OF_OFFER, result.documentType());
    }

    @Test void fullySignedResidentialAgreementActivatesGovernedLease() {
        LeaseDocument agreement = new LeaseDocument(); agreement.setId(41L); agreement.setLeaseId(11L);
        agreement.setDocumentType(LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT);
        agreement.setStatus(LeaseDocumentStatus.PARTIALLY_SIGNED); agreement.setIssuerUserId(16L);
        agreement.setRecipientUserId(14L); agreement.setIssuerSignedAt(LocalDateTime.now().minusMinutes(1)); agreement.setActive(true);
        when(users.getUserId()).thenReturn(14L);
        when(documents.findAccessible(41L, 14L)).thenReturn(Optional.of(agreement));
        when(documents.save(agreement)).thenReturn(agreement);

        LeaseDocumentDTO result = service.sign(41L);

        assertEquals(LeaseDocumentStatus.SIGNED, result.status());
        verify(leaseService).activateFromGovernedAgreement(eq(11L), eq(16L), eq(14L),
                eq(agreement.getIssuerSignedAt()), eq(agreement.getRecipientSignedAt()));
    }

    @Test void fullySignedSaleLetterOfOfferReservesTheLinkedSale() {
        LeaseDocument offer = new LeaseDocument(); offer.setId(42L); offer.setSaleId(91L);
        offer.setDocumentType(LeaseDocumentType.PROPERTY_SALE_LETTER_OF_OFFER);
        offer.setStatus(LeaseDocumentStatus.PARTIALLY_SIGNED); offer.setIssuerUserId(16L);
        offer.setRecipientUserId(14L); offer.setIssuerSignedAt(LocalDateTime.now().minusMinutes(1));
        offer.setAmount(new BigDecimal("14500000")); offer.setActive(true);
        when(users.getUserId()).thenReturn(14L);
        when(documents.findAccessible(42L, 14L)).thenReturn(Optional.of(offer));
        when(documents.save(offer)).thenReturn(offer);

        LeaseDocumentDTO result = service.sign(42L);

        assertEquals(LeaseDocumentStatus.SIGNED, result.status());
        verify(salesService).acceptSignedOffer(91L, 42L, new BigDecimal("14500000"));
    }

    @Test void legallyUnreviewedStarterTemplateCannotBeIssued() {
        LeaseDocument draft = new LeaseDocument(); draft.setId(51L); draft.setStatus(LeaseDocumentStatus.DRAFT);
        draft.setIssuerUserId(16L); draft.setRecipientUserId(14L); draft.setLegalReviewRequired(true); draft.setActive(true);
        when(documents.findAccessible(51L, 16L)).thenReturn(Optional.of(draft));

        assertThrows(PMSCustomException.class, () -> service.issue(51L));

        verifyNoInteractions(email);
        verify(documents, never()).save(draft);
    }

    @Test void recordsManualApprovalAgainstTheExactTemplateContent() {
        when(templates.findFirstByDocumentTypeAndActiveTrueOrderByVersionDesc(LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT))
                .thenReturn(Optional.empty());
        when(templates.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String body = "<h1>{{documentName}}</h1><p>Approved wording</p>";

        LeaseDocumentTemplate saved = service.createTemplateVersion(new TemplateVersionRequest(
                LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT, "Residential Lease Agreement", body, false));

        assertFalse(saved.isLegalReviewRequired());
        assertEquals(16L, saved.getLegalReviewedBy());
        assertNotNull(saved.getLegalReviewedAt());
        assertEquals(DocumentTemplateIntegrity.sha256(body), saved.getContentSha256());
    }

    @Test void rejectsTemplateContentThatNoLongerMatchesItsApprovedHash() {
        when(documents.existsOpen(11L, LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT)).thenReturn(false);
        LeaseDocumentTemplate template = new LeaseDocumentTemplate();
        template.setDisplayName("Residential Lease Agreement"); template.setBodyHtml("<p>changed</p>");
        template.setContentSha256(DocumentTemplateIntegrity.sha256("<p>approved</p>"));
        template.setLegalReviewRequired(true); template.setActive(true);
        when(templates.findFirstByDocumentTypeAndActiveTrueOrderByVersionDesc(LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT))
                .thenReturn(Optional.of(template));

        assertThrows(PMSCustomException.class, () -> service.generate(new GenerateLeaseDocumentRequest(
                11L, null, null, null, LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT,
                lease.getMoveInDate(), null, new BigDecimal("45000.00"), "KES", null)));
        verifyNoInteractions(renderer);
    }

    @Test void rejectsActiveOrExternalTemplateContentBeforePersistence() {
        for (String body : List.of("<script>alert(1)</script>", "<img src='file:///etc/passwd'>",
                "<div onclick='steal()'>x</div>", "<style>@import 'x';</style>",
                "<iframe srcdoc='<p>x</p>'></iframe>")) {
            assertThrows(PMSCustomException.class, () -> service.createTemplateVersion(new TemplateVersionRequest(
                    LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT, "Unsafe", body, true)));
        }
        verifyNoInteractions(templates);
    }
}
