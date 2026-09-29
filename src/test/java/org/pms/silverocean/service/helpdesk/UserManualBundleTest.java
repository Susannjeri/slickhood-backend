package org.pms.silverocean.service.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class UserManualBundleTest {
    private JsonNode bundle() throws Exception {
        try (var stream=getClass().getResourceAsStream("/helpdesk/user-manual.json")) {
            assertNotNull(stream);
            return new ObjectMapper().readTree(stream);
        }
    }

    @Test void allThirtyDraftChaptersMatchTheWrittenManual() throws Exception {
        JsonNode bundle=bundle();
        String written=Files.readString(Path.of("docs/SLICKHOOD_USER_MANUAL.md"),StandardCharsets.UTF_8)
                .replace("\r\n","\n");
        assertEquals("2026-09-25.1",bundle.path("version").asText());
        assertTrue(written.contains("Version "+bundle.path("version").asText()));
        assertTrue(bundle.path("releaseStatus").asText().contains("preserve publication approval"));
        assertEquals(30,bundle.path("chapters").size());
        Set<String> slugs=new HashSet<>();
        for (JsonNode chapter:bundle.path("chapters")) {
            JsonNode article=chapter.path("article");
            assertTrue(slugs.add(article.path("slug").asText()));
            assertFalse(article.path("published").asBoolean());
            var matcher=Pattern.compile("(?ms)^## "+Pattern.quote(article.path("title").asText())
                    +"\\n\\nAudience: [^\\n]+\\.\\n\\n(?<body>.*?)(?=\\n## |\\z)").matcher(written);
            assertTrue(matcher.find(),article.path("title").asText());
            assertEquals(article.path("body").asText().replace("\r\n","\n").stripTrailing(),
                    matcher.group("body").stripTrailing(),article.path("slug").asText());
        }
    }

    @Test void restrictedStaffGuidanceKeepsItsExistingAudience() throws Exception {
        for (JsonNode chapter:bundle().path("chapters")) {
            JsonNode article=chapter.path("article");
            switch (article.path("slug").asText()) {
                case "manual-admin","manual-knowledge-publishing" -> assertEquals("Superadmin",article.path("audienceRoles").asText());
                case "manual-support-operations" -> assertEquals("Support,Superadmin",article.path("audienceRoles").asText());
                default -> { }
            }
        }
    }

    @Test void revisedGuidanceCoversScopeLimitsAndSafePublication() throws Exception {
        String daily="",publishing="";
        for (JsonNode chapter:bundle().path("chapters")) {
            JsonNode article=chapter.path("article");
            if(article.path("slug").asText().equals("manual-documents-notifications"))daily=article.path("body").asText();
            if(article.path("slug").asText().equals("manual-knowledge-publishing"))publishing=article.path("body").asText();
        }
        assertTrue(daily.contains("at most 500 rows"));
        assertTrue(daily.contains("at most 5,000"));
        assertTrue(daily.contains("same financial access restrictions"));
        assertTrue(daily.contains("count once"));
        assertTrue(daily.contains("not shown in the ordinary notification inbox"));
        assertTrue(publishing.contains("preserves the database ID"));
        assertTrue(publishing.contains("publication approval"));
    }

    @Test void insuranceGuidanceCoversMarineCargoDocumentsAndCicBoundary() throws Exception {
        String insurance="";
        for (JsonNode chapter:bundle().path("chapters")) {
            JsonNode article=chapter.path("article");
            if(article.path("slug").asText().equals("manual-insurance"))insurance=article.path("body").asText();
        }
        assertTrue(insurance.contains("Import Declaration Form (IDF/IM0)"));
        assertTrue(insurance.contains("commercial invoice"));
        assertTrue(insurance.contains("CIC Insurance"));
        assertTrue(insurance.contains("does not mean CIC has accepted the risk or issued cover"));
    }

    @Test void propertyJourneysExplainTheSharedSubscriptionAndUnitAllowance() throws Exception {
        String subscriptions="",rental="",estate="",sales="";
        for (JsonNode chapter:bundle().path("chapters")) {
            JsonNode article=chapter.path("article");
            switch (article.path("slug").asText()) {
                case "manual-subscriptions" -> subscriptions=article.path("body").asText();
                case "manual-rental-setup" -> rental=article.path("body").asText();
                case "manual-estate-setup" -> estate=article.path("body").asText();
                case "manual-sales-setup" -> sales=article.path("body").asText();
                default -> { }
            }
        }
        assertTrue(subscriptions.contains("one active property subscription covers"));
        assertTrue(subscriptions.contains("unit allowance is shared"));
        assertTrue(rental.contains("shared unit allowance"));
        assertTrue(estate.contains("same property subscription can cover rental, estate and sale work"));
        assertTrue(sales.contains("shared unit allowance"));
    }
}
