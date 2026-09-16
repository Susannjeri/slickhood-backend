package org.pms.silverocean.service.helpdesk;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.HelpArticle;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HelpKnowledgeSearchTest {
    @Test void genericQuestionDoesNotMatchCommonWords() {
        assertTrue(HelpKnowledgeSearch.rank(List.of(article(1, "Rental guide", "The user should have this lease.")),
                "How could you help me with this please?").isEmpty());
    }
    @Test void wordsAreNotSubstringMatches() {
        assertTrue(HelpKnowledgeSearch.rank(List.of(article(1, "Corporate accounts", "Use the corporate dashboard.")), "Where is my port?").isEmpty());
    }
    @Test void titleAndKeywordsOutrankIncidentalBodyMatches() {
        var broad = article(1, "Platform manual", "A workspace contains an invoice and a receipt.");
        var focused = article(2, "Payment receipts", "Open Billing to view your receipt.");
        assertEquals(2L, HelpKnowledgeSearch.rank(List.of(broad, focused), "Where can I find payment receipts?").getFirst().getId());
    }
    @Test void invitationAndPluralFormsMatch() {
        assertEquals(1, HelpKnowledgeSearch.rank(List.of(article(1, "Invite homeowners", "Select the home.")), "How do invitations work?").size());
    }
    @Test void loginPhrasesHaveTheSameIntent() {
        var article = article(1, "Sign in to SlickHood", "Enter your email and password on the login page.");
        for (String question : List.of("How do I log in?", "I need to access my account", "Where is account access?", "I cannot sign in")) {
            assertEquals(List.of(1L), HelpKnowledgeSearch.rank(List.of(article), question).stream().map(HelpArticle::getId).toList(), question);
        }
    }
    @Test void relatedTermsFindTheSameCustomerJourney() {
        var delivery = article(1, "Rider delivery code", "The courier enters the handover code after delivery.");
        var property = article(2, "Property sale buyers", "A purchaser receives a letter of offer.");
        assertEquals(1L, HelpKnowledgeSearch.rank(List.of(delivery, property), "Where does the delivery driver enter the delivery PIN?").getFirst().getId());
        assertEquals(2L, HelpKnowledgeSearch.rank(List.of(delivery, property), "How does a home buyer receive the offer letter?").getFirst().getId());
    }
    @Test void punctuationAndApostrophesDoNotBreakIntentRecognition() {
        var article = article(1, "Password reset", "Recover your account from the sign-in page.");
        assertEquals(1, HelpKnowledgeSearch.rank(List.of(article), "I can't remember it; how do I recover my account?").size());
    }
    @Test void relatedVocabularyDoesNotUseSubstringMatching() {
        var article = article(1, "Sign in", "Access your account.");
        assertTrue(HelpKnowledgeSearch.rank(List.of(article), "Show me signal reports").isEmpty());
    }
    @Test void commonActionPhrasesMatchWithoutConflatingDifferentLifecycleActions() {
        var edit = article(1, "Edit a team member", "Update the staff member's details.");
        var suspend = article(2, "Suspend a team member", "Temporarily disable access without deleting the user.");
        assertEquals(1L, HelpKnowledgeSearch.rank(List.of(edit, suspend), "How can I modify a staff member?").getFirst().getId());
        assertEquals(2L, HelpKnowledgeSearch.rank(List.of(edit, suspend), "How can I pause an internal user?").getFirst().getId());
        assertNotEquals(2L, HelpKnowledgeSearch.rank(List.of(edit, suspend), "How can I remove a staff member?").stream()
                .map(HelpArticle::getId).findFirst().orElse(-1L));
    }
    @Test void oneIncidentalBodyWordIsNotEnoughEvidence() {
        assertTrue(HelpKnowledgeSearch.rank(List.of(article(1, "Rentals", "Check the document.")), "Explain my document?").isEmpty());
    }
    @Test void excerptsRetainRelevantLaterParagraphWithoutUnboundedContext() {
        var a = article(1, "Reports", "Report overview.\n\n" + "Unrelated text ".repeat(400)
                + "\n\nExport CSV from Reports. Financial reports use your selected workspace.");
        String excerpt = HelpKnowledgeSearch.excerpt(a, "How do I export reports?");
        assertTrue(excerpt.contains("Export CSV")); assertTrue(excerpt.length() < 4600);
        assertTrue(excerpt.startsWith("Report overview."));
    }
    @Test void retrievalHasStableOrderAndFourSourceLimit() {
        var list = java.util.stream.LongStream.rangeClosed(1, 8).mapToObj(id -> article(id, "Payments", "Open Billing.")).toList();
        assertEquals(List.of(1L,2L,3L,4L), HelpKnowledgeSearch.rank(list.reversed(), "Payments").stream().map(HelpArticle::getId).toList());
    }
    private HelpArticle article(long id, String title, String body) {
        HelpArticle a = new HelpArticle(); a.setId(id); a.setTitle(title); a.setBody(body); return a;
    }
}
