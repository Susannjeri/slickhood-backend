package org.pms.silverocean.database.pms.entities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.sp.wrappers.ComplaintCaseDTO;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProviderMarketplaceAuditSerializationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void providerProfileAuditJsonEscapesHostileBusinessName() throws Exception {
        ProviderProfile profile = new ProviderProfile();
        profile.setId(41L);
        profile.setUserId(73L);
        profile.setBusinessName("A \"quoted\" provider\nwith Unicode — 家");
        profile.setStatus("PENDING_VERIFICATION");
        profile.setConsentTimestamp(ZonedDateTime.of(2026, 10, 11, 9, 30, 0, 0, ZoneId.of("UTC")));

        JsonNode audit = objectMapper.readTree(profile.toAuditJSON());

        assertEquals(41L, audit.get("id").longValue());
        assertEquals(73L, audit.get("userId").longValue());
        assertEquals("A \"quoted\" provider\nwith Unicode — 家", audit.get("businessName").textValue());
        assertEquals("PENDING_VERIFICATION", audit.get("status").textValue());
        assertEquals("2026-10-11T09:30Z[UTC]", audit.get("consentTimestamp").textValue());
    }

    @Test
    void providerServiceAuditJsonEscapesHostileCatalogueText() throws Exception {
        ProviderService service = new ProviderService();
        service.setId(91L);
        service.setProfileId(41L);
        service.setCategoryId(12L);
        service.setCategoryName("Repairs \"and\" maintenance\n服务");
        service.setTitle("Fix taps \\ pipes \"today\"\n✓");
        service.setStatus("DRAFT");

        JsonNode audit = objectMapper.readTree(service.toAuditJSON());

        assertEquals(91L, audit.get("id").longValue());
        assertEquals(41L, audit.get("profileId").longValue());
        assertEquals(12L, audit.get("categoryId").longValue());
        assertEquals("Repairs \"and\" maintenance\n服务", audit.get("categoryName").textValue());
        assertEquals("Fix taps \\ pipes \"today\"\n✓", audit.get("title").textValue());
        assertEquals("DRAFT", audit.get("status").textValue());
    }

    @Test
    void publicComplaintViewRedactsInternalReviewDetails() {
        ComplaintCase complaint = new ComplaintCase();
        complaint.setId(17L);
        complaint.setServiceId(91L);
        complaint.setBookingId(42L);
        complaint.setFiledByUserId(73L);
        complaint.setDescription("The provider did not arrive.");
        complaint.setStatus("UNDER_REVIEW");
        complaint.setAdminNotes("Internal fraud-screening note");
        complaint.setPublicResolution("We are still reviewing the report.");
        complaint.setAssignedAdminId(5L);

        ComplaintCaseDTO publicView = new ComplaintCaseDTO(complaint).withoutInternalReviewDetails();

        assertEquals("UNDER_REVIEW", publicView.status());
        assertEquals("We are still reviewing the report.", publicView.publicResolution());
        assertNull(publicView.adminNotes());
        assertNull(publicView.assignedAdminId());
        assertNull(publicView.filedByUserId());
        assertNull(publicView.complaintCreatorName());
    }
}
