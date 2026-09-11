package org.pms.silverocean.service.leasedocument;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.LeaseDocumentRepo;
import org.pms.silverocean.database.pms.LeaseDocumentTemplateRepo;
import org.pms.silverocean.database.pms.PropertyRepo;
import org.pms.silverocean.database.pms.entities.Invite;
import org.pms.silverocean.database.pms.entities.LeaseDocument;
import org.pms.silverocean.database.pms.entities.LeaseDocumentTemplate;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.PropertyOwnership;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.mustache.RenderService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeownerAgreementServiceTest {
    @Mock LeaseDocumentRepo documents;
    @Mock LeaseDocumentTemplateRepo templates;
    @Mock PropertyRepo properties;
    @Mock UserDao users;
    @Mock RenderService renderer;
    @Mock DocumentBrandingService branding;
    private HomeownerAgreementService service;

    @BeforeEach
    void setUp() {
        service = new HomeownerAgreementService(documents, templates, properties, users, renderer, branding);
    }

    @Test
    void acceptedHomeownerInviteCreatesFrozenIssuedAgreementForRecipientFirstSignature() {
        LocalDate effectiveDate = LocalDate.of(2026, 9, 1);
        Property property = new Property(); property.setId(11L); property.setCreatedBy(9L); property.setActive(true);
        property.setName("Acacia Estate"); property.setAddress("Nairobi"); property.setType("APARTMENT_BLOCK"); property.setCurrency("KES");
        Unit unit = new Unit(); unit.setId(77L); unit.setPropertyId(11L); unit.setRef("A-12"); unit.setActive(true); unit.setLeaseMode("SERVICE_CHARGE");
        Users issuer = new Users(); issuer.setId(9L); issuer.setActive(true); issuer.setFullName("Estate Manager"); issuer.setEmail("manager@example.test");
        Users homeowner = new Users(); homeowner.setId(200L); homeowner.setActive(true); homeowner.setFullName("Mama Njeri"); homeowner.setEmail("owner@example.test");
        PropertyOwnership ownership = new PropertyOwnership(); ownership.setId(80L); ownership.setPropertyId(11L); ownership.setUnitId(77L);
        ownership.setHomeownerUserId(200L); ownership.setOwnershipStart(effectiveDate); ownership.setActive(true);
        Invite invite = new Invite(); invite.setAgreementTemplateId(41L); invite.setLeaseStartDate(effectiveDate); invite.setCreatedBy(9L); invite.setEntityId(77L);
        LeaseDocumentTemplate template = new LeaseDocumentTemplate(); template.setId(41L); template.setVersion(3); template.setActive(true);
        template.setDisplayName("Estate Residential Agreement"); template.setDocumentType(LeaseDocumentType.ESTATE_RESIDENTIAL_AGREEMENT);
        template.setBodyHtml("<html><body>{{propertyName}} / {{unitRef}} / {{recipientName}}</body></html>");
        template.setContentSha256(DocumentTemplateIntegrity.sha256(template.getBodyHtml())); template.setLegalReviewRequired(false); template.setLegalReviewedAt(LocalDateTime.now());

        when(properties.findById(11L)).thenReturn(Optional.of(property));
        when(users.findById(9L)).thenReturn(Optional.of(issuer));
        when(templates.findById(41L)).thenReturn(Optional.of(template));
        when(renderer.renderInline(any(String.class), anyMap())).thenReturn("<html>frozen agreement</html>");
        when(documents.save(any(LeaseDocument.class))).thenAnswer(call -> call.getArgument(0));

        LeaseDocument saved = service.createIssuedAgreement(ownership, unit, invite, homeowner);

        assertEquals(LeaseDocumentStatus.ISSUED, saved.getStatus());
        assertEquals(LeaseDocumentType.ESTATE_RESIDENTIAL_AGREEMENT, saved.getDocumentType());
        assertEquals(9L, saved.getIssuerUserId());
        assertEquals(200L, saved.getRecipientUserId());
        assertEquals(effectiveDate, saved.getEffectiveDate());
        assertEquals(3, saved.getTemplateVersion());
        assertEquals("<html>frozen agreement</html>", saved.getRenderedHtml());
        assertNotNull(saved.getIssuedAt());
        assertFalse(saved.isLegalReviewRequired());
    }
}
