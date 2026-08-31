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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

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
    LeaseDocumentService service;
    Lease lease;

    @BeforeEach void setup() {
        service = new LeaseDocumentService(documents, templates, leases, properties, units, users, renderer, email, leaseService);
        lease = new Lease(); lease.setId(11L); lease.setTenantId(12L); lease.setLeaseMode("RENT");
        lease.setPrice(45_000); lease.setCurrency("KES"); lease.setMoveInDate(LocalDate.of(2026, 10, 1)); lease.setActive(true);
        UnitTenant tenancy = new UnitTenant(); tenancy.setId(12L); tenancy.setUnitId(13L); tenancy.setUserId(14L); tenancy.setActive(true);
        Unit unit = new Unit(); unit.setId(13L); unit.setPropertyId(15L); unit.setRef("A-1"); unit.setActive(true);
        Property property = new Property(); property.setId(15L); property.setCreatedBy(16L); property.setName("Acacia"); property.setCurrency("KES"); property.setActive(true);
        Users owner = new Users(); owner.setId(16L); owner.setEmail("owner@example.com"); owner.setFullName("Owner");
        Users tenant = new Users(); tenant.setId(14L); tenant.setEmail("tenant@example.com"); tenant.setFullName("Tenant");
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

    @Test void agreementCannotBeCreatedBeforeSignedLetterOfOffer() {
        when(documents.existsOpen(11L, LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT)).thenReturn(false);
        when(documents.existsByLeaseIdAndDocumentTypeAndStatusAndActiveTrue(11L,
                LeaseDocumentType.RENTAL_LETTER_OF_OFFER, LeaseDocumentStatus.SIGNED)).thenReturn(false);

        assertThrows(PMSCustomException.class, () -> service.generate(new GenerateLeaseDocumentRequest(
                11L, null, null, LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT,
                lease.getMoveInDate(), null, null, "KES", null)));
        verify(documents, never()).save(any());
    }

    @Test void validLetterOfOfferCreatesImmutableDraftSnapshot() {
        LeaseDocumentTemplate template = new LeaseDocumentTemplate(); template.setId(21L); template.setVersion(3);
        template.setDisplayName("Residential Tenancy Letter of Offer"); template.setBodyHtml("<p>{{documentName}}</p>"); template.setActive(true);
        when(documents.existsOpen(11L, LeaseDocumentType.RENTAL_LETTER_OF_OFFER)).thenReturn(false);
        when(templates.findFirstByDocumentTypeAndActiveTrueOrderByVersionDesc(LeaseDocumentType.RENTAL_LETTER_OF_OFFER)).thenReturn(Optional.of(template));
        when(renderer.renderInline(any(), any())).thenReturn("<p>Residential Tenancy Letter of Offer</p>");
        when(documents.save(any())).thenAnswer(invocation -> { LeaseDocument d = invocation.getArgument(0); d.setId(31L); return d; });

        LeaseDocumentDTO result = service.generate(new GenerateLeaseDocumentRequest(11L, null, null,
                LeaseDocumentType.RENTAL_LETTER_OF_OFFER, lease.getMoveInDate(), LocalDate.now().plusDays(7),
                new BigDecimal("45000.00"), "KES", "Subject to agreement"));

        assertEquals(LeaseDocumentStatus.DRAFT, result.status());
        assertEquals(3, result.templateVersion());
        assertEquals(LeaseDocumentType.RENTAL_LETTER_OF_OFFER, result.documentType());
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

    @Test void legallyUnreviewedStarterTemplateCannotBeIssued() {
        LeaseDocument draft = new LeaseDocument(); draft.setId(51L); draft.setStatus(LeaseDocumentStatus.DRAFT);
        draft.setIssuerUserId(16L); draft.setRecipientUserId(14L); draft.setLegalReviewRequired(true); draft.setActive(true);
        when(documents.findAccessible(51L, 16L)).thenReturn(Optional.of(draft));

        assertThrows(PMSCustomException.class, () -> service.issue(51L));

        verifyNoInteractions(email);
        verify(documents, never()).save(draft);
    }
}
