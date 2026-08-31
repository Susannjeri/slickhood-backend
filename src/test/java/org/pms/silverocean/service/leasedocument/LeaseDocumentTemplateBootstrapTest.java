package org.pms.silverocean.service.leasedocument;

import com.samskivert.mustache.Mustache;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.LeaseDocumentTemplateRepo;
import org.pms.silverocean.database.pms.entities.LeaseDocumentTemplate;

import java.util.Optional;
import java.io.StringWriter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LeaseDocumentTemplateBootstrapTest {

    @Test
    void seedsStructuredTemplatesFromClasspathResources() throws Exception {
        LeaseDocumentTemplateRepo repo = mock(LeaseDocumentTemplateRepo.class);
        when(repo.findFirstByDocumentTypeAndActiveTrueOrderByVersionDesc(any())).thenReturn(Optional.empty());
        when(repo.findFirstByDocumentTypeOrderByVersionDesc(any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new LeaseDocumentTemplateBootstrap(repo, true).run(null);

        var captor = org.mockito.ArgumentCaptor.forClass(LeaseDocumentTemplate.class);
        verify(repo, times(9)).save(captor.capture());
        assertEquals(9, captor.getAllValues().size());
        for (LeaseDocumentTemplate template : captor.getAllValues()) {
            assertTrue(template.getBodyHtml().startsWith("<!doctype html>"));
            assertTrue(template.getBodyHtml().contains("Powered by SlickHood"));
            assertTrue(template.getBodyHtml().contains("immutable snapshot"));
            assertFalse(template.getBodyHtml().contains("<!--DOCUMENT_BODY-->"));
            assertTrue(template.isLegalReviewRequired());
            assertEquals(DocumentTemplateIntegrity.sha256(template.getBodyHtml()), template.getContentSha256());
            StringWriter rendered = new StringWriter();
            Mustache.compiler().defaultValue("").compile(template.getBodyHtml()).execute(Map.of(
                    "documentName", template.getDisplayName(), "documentOwnerName", "Acacia Estates",
                    "propertyName", "Acacia", "unitRef", "A-1"), rendered);
            assertTrue(rendered.toString().contains(template.getDisplayName()));
        }
        LeaseDocumentTemplate residential = captor.getAllValues().stream()
                .filter(template -> template.getDocumentType() == LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT)
                .findFirst().orElseThrow();
        assertTrue(residential.getBodyHtml().contains("Rent and other charges"));
        assertTrue(residential.getBodyHtml().contains("Default, notices and termination"));
    }

    @Test
    void replacesOnlyKnownGenericStarterAndPreservesItsHistory() throws Exception {
        LeaseDocumentTemplateRepo repo = mock(LeaseDocumentTemplateRepo.class);
        LeaseDocumentTemplate generic = new LeaseDocumentTemplate();
        generic.setDocumentType(LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT);
        generic.setVersion(3);
        generic.setActive(true);
        generic.setBodyHtml("<p>This agreement records the residential tenancy, rent and term.</p>");
        when(repo.findFirstByDocumentTypeAndActiveTrueOrderByVersionDesc(any())).thenAnswer(invocation ->
                invocation.getArgument(0) == LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT
                        ? Optional.of(generic) : Optional.empty());
        when(repo.findFirstByDocumentTypeOrderByVersionDesc(LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT))
                .thenReturn(Optional.of(generic));
        when(repo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new LeaseDocumentTemplateBootstrap(repo, true).run(null);

        assertFalse(generic.isActive());
        var captor = org.mockito.ArgumentCaptor.forClass(LeaseDocumentTemplate.class);
        verify(repo, atLeast(2)).save(captor.capture());
        LeaseDocumentTemplate replacement = captor.getAllValues().stream()
                .filter(template -> template != generic).findFirst().orElseThrow();
        assertEquals(4, replacement.getVersion());
        assertTrue(replacement.getBodyHtml().contains("Parties and premises"));
    }

    @Test
    void quarantinesAHashlessTemplateCreatedDuringRollback() throws Exception {
        LeaseDocumentTemplateRepo repo = mock(LeaseDocumentTemplateRepo.class);
        LeaseDocumentTemplate rollbackRow = new LeaseDocumentTemplate();
        rollbackRow.setDocumentType(LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT);
        rollbackRow.setVersion(7); rollbackRow.setActive(true);
        rollbackRow.setBodyHtml("<p>Custom wording created by the previous artifact</p>");
        rollbackRow.setLegalReviewRequired(false);
        when(repo.findFirstByDocumentTypeAndActiveTrueOrderByVersionDesc(any())).thenAnswer(invocation ->
                invocation.getArgument(0) == LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT
                        ? Optional.of(rollbackRow) : Optional.empty());
        when(repo.findFirstByDocumentTypeOrderByVersionDesc(any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new LeaseDocumentTemplateBootstrap(repo, true).run(null);

        assertEquals(DocumentTemplateIntegrity.sha256(rollbackRow.getBodyHtml()), rollbackRow.getContentSha256());
        assertTrue(rollbackRow.isLegalReviewRequired());
        assertNull(rollbackRow.getLegalReviewedAt());
        assertNull(rollbackRow.getLegalReviewedBy());
    }
}
