package org.pms.silverocean.service.helpdesk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.HelpConversation;
import org.pms.silverocean.database.pms.entities.HelpMessage;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.notification.NotificationService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HelpDeskServiceTest {
    @Mock HelpConversationRepo conversations;
    @Mock HelpMessageRepo messages;
    @Mock HelpArticleRepo articles;
    @Mock UserDao users;
    @Mock OpenAiHelpDeskClient ai;
    @Mock HelpDeskRateLimiter rateLimiter;
    @Mock NotificationService notifications;
    HelpDeskService service;

    @BeforeEach void setup() {
        service = new HelpDeskService(conversations, messages, articles, users, ai, rateLimiter, notifications);
        ReflectionTestUtils.setField(service, "maxInputChars", 4000);
        ReflectionTestUtils.setField(service, "maxContextMessages", 12);
        ReflectionTestUtils.setField(service, "guestSessionHours", 24L);
        ReflectionTestUtils.setField(service, "rateLimitPerMinute", 20);
        ReflectionTestUtils.setField(service, "guestStartLimitPerMinute", 10);
        ReflectionTestUtils.setField(service, "urgentSla", java.time.Duration.ofMinutes(15));
        ReflectionTestUtils.setField(service, "highSla", java.time.Duration.ofHours(1));
        ReflectionTestUtils.setField(service, "normalSla", java.time.Duration.ofHours(4));
        ReflectionTestUtils.setField(service, "lowSla", java.time.Duration.ofHours(8));
    }

    @Test void startsConversationForAuthenticatedUserAndActiveRole() {
        when(users.getUserId()).thenReturn(17L);
        when(users.getActiveRole()).thenReturn(PMSRole.LANDLORD);
        when(conversations.save(any())).thenAnswer(inv -> { HelpConversation c=inv.getArgument(0); c.setId(9L); return c; });
        var result = service.start(new HelpDeskModels.StartConversation("Rent receipt help"));
        assertEquals(9L, result.id());
        assertEquals("Landlord", result.activeRole());
        ArgumentCaptor<HelpConversation> saved = ArgumentCaptor.forClass(HelpConversation.class);
        verify(conversations).save(saved.capture());
        assertEquals(17L, saved.getValue().getUserId());
        assertTrue(saved.getValue().isActive());
    }

    @Test void conversationLookupIsScopedToAuthenticatedOwner() {
        when(users.getUserId()).thenReturn(17L);
        when(conversations.findByIdAndUserIdAndActiveTrue(44L, 17L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.get(44L));
        verify(conversations, never()).findByIdAndActiveTrue(anyLong());
    }

    @Test void aiFailureEscalatesAndProvidesSafeFallback() {
        when(users.getUserId()).thenReturn(17L);
        HelpConversation conversation = new HelpConversation();
        conversation.setId(5L); conversation.setUserId(17L); conversation.setActiveRole("Tenant");
        conversation.setStatus("OPEN"); conversation.setPriority("NORMAL"); conversation.setActive(true);
        when(conversations.findByIdAndUserIdAndActiveTrue(5L, 17L)).thenReturn(Optional.of(conversation));
        when(conversations.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messages.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messages.findByConversationIdAndActiveTrueOrderByCreatedOnDesc(eq(5L), any())).thenReturn(List.of());
        when(articles.findByPublishedTrueAndActiveTrueOrderByCategoryAscTitleAsc()).thenReturn(List.of());
        when(ai.moderate(anyString())).thenReturn(new OpenAiHelpDeskClient.ModerationResult(true, false));

        var result = service.send(5L, new HelpDeskModels.SendMessage("My payment is missing"));
        assertEquals("ESCALATED", result.status());
        ArgumentCaptor<HelpMessage> saved = ArgumentCaptor.forClass(HelpMessage.class);
        verify(messages, times(2)).save(saved.capture());
        assertEquals(List.of("USER", "SYSTEM"), saved.getAllValues().stream().map(HelpMessage::getSenderType).toList());
        assertFalse(saved.getAllValues().get(1).getContent().toLowerCase().contains("api key"));
        verify(ai, never()).answer(anyString(), anyString(), anyString());
    }

    @Test void guestTokenIsReturnedOnlyInPlaintextAndStoredAsHash() {
        when(conversations.save(any())).thenAnswer(inv -> { HelpConversation c=inv.getArgument(0); c.setId(21L); return c; });
        var result = service.startGuest(new HelpDeskModels.GuestStart("Registration help", "REGISTRATION", "/register"));
        assertNotNull(result.accessToken());
        assertTrue(result.accessToken().length() >= 32);
        ArgumentCaptor<HelpConversation> saved = ArgumentCaptor.forClass(HelpConversation.class);
        verify(conversations).save(saved.capture());
        assertNotEquals(result.accessToken(), saved.getValue().getGuestTokenHash());
        assertEquals(64, saved.getValue().getGuestTokenHash().length());
        assertNull(saved.getValue().getUserId());
    }

    @Test void sensitiveMessageIsRejectedAndNotPersisted() {
        when(users.getUserId()).thenReturn(17L);
        HelpConversation conversation = new HelpConversation();
        conversation.setId(5L); conversation.setTicketNumber("SH-TEST"); conversation.setUserId(17L);
        conversation.setActiveRole("Tenant"); conversation.setStatus("OPEN"); conversation.setPriority("NORMAL"); conversation.setActive(true);
        when(conversations.findByIdAndUserIdAndActiveTrue(5L, 17L)).thenReturn(Optional.of(conversation));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.send(5L, new HelpDeskModels.SendMessage("My OTP is 123456")));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(messages, ai);
        verify(conversations, never()).save(any());
    }

    @Test void invalidGuestTokenIsAControlledNotFoundResponse() {
        String validLengthToken = "x".repeat(40);
        when(conversations.findByTicketNumberAndGuestTokenHashAndActiveTrue(eq("SH-TEST"), anyString()))
                .thenReturn(Optional.empty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.getGuest("SH-TEST", validLengthToken));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test void humanCaseResponseDoesNotInvokeAiAndKeepsDeadline() {
        HelpConversation c = ownedCase("WAITING_FOR_SUPPORT");
        c.setPriority("HIGH"); c.setWaitingSince(java.time.LocalDateTime.now().minusHours(2));
        c.setSlaDueAt(c.getWaitingSince().plusHours(1));
        var due = c.getSlaDueAt();
        when(ai.moderate(anyString())).thenReturn(new OpenAiHelpDeskClient.ModerationResult(true, false));
        assertEquals("ESCALATED", service.send(5L, new HelpDeskModels.SendMessage("Here are more details")).status());
        assertEquals(due, c.getSlaDueAt()); assertEquals("HIGH", c.getPriority());
        verify(ai, never()).answer(anyString(), anyString(), anyString());
        verifyNoInteractions(articles);
    }

    @Test void repeatedEscalationCannotExtendDeadlineOrDowngradePriority() {
        HelpConversation c = ownedCase("ESCALATED"); c.setPriority("URGENT");
        c.setWaitingSince(java.time.LocalDateTime.now().minusHours(1));
        c.setSlaDueAt(c.getWaitingSince().plusMinutes(15)); var due = c.getSlaDueAt();
        service.escalate(5L, new HelpDeskModels.Escalate(null, "LOW"));
        assertEquals(due, c.getSlaDueAt()); assertEquals("URGENT", c.getPriority());
        verifyNoInteractions(notifications);
    }

    @Test void secretInSubjectIsRejectedBeforeStorageAndUrlQueryIsRemoved() {
        assertThrows(ResponseStatusException.class, () -> service.startGuest(new HelpDeskModels.GuestStart("OTP is 123456", "GENERAL", "/register")));
        verify(conversations, never()).save(any());
        when(conversations.save(any())).thenAnswer(i -> { HelpConversation c=i.getArgument(0); c.setId(7L); return c; });
        var guest = service.startGuest(new HelpDeskModels.GuestStart("Help", "GENERAL", "/register?invite=private#secret"));
        assertEquals("/register", guest.conversation().pageContext());
    }

    @Test void internalNotesDoNotReachCustomerOrAiTranscript() {
        HelpConversation c = new HelpConversation(); c.setId(5L);
        HelpMessage note = new HelpMessage(); note.setId(2L); note.setInternalNote(true); note.setContent("private investigation");
        HelpMessage publicMessage = new HelpMessage(); publicMessage.setId(3L); publicMessage.setContent("public reply");
        assertEquals(1, new HelpDeskModels.ConversationView(c, List.of(note, publicMessage), false).messages().size());
        assertEquals(2, new HelpDeskModels.ConversationView(c, List.of(note, publicMessage), true).messages().size());
    }

    @Test void manualImportCreatesOnlyDraftsAndRetainsExistingSlugs() throws Exception {
        when(users.getUserId()).thenReturn(17L);
        when(articles.existsBySlugAndIdNot(anyString(), eq(-1L))).thenAnswer(i -> "manual-start".equals(i.getArgument(0)));
        when(articles.save(any())).thenAnswer(i -> { var a=(org.pms.silverocean.database.pms.entities.HelpArticle)i.getArgument(0); a.setId(9L); return a; });
        var result = service.importManualDrafts();
        assertEquals(29, result.get("created")); assertEquals(1, result.get("retained"));
        var captor = ArgumentCaptor.forClass(org.pms.silverocean.database.pms.entities.HelpArticle.class);
        verify(articles, times(29)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().noneMatch(org.pms.silverocean.database.pms.entities.HelpArticle::isPublished));
        assertTrue(captor.getAllValues().stream().filter(a -> a.getSlug().equals("manual-admin")).allMatch(a -> "Superadmin".equals(a.getAudienceRoles())));
        assertTrue(org.pms.silverocean.controller.HelpDeskController.class.getMethod("importManual")
                .getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value().contains("manage_helpdesk_articles"));
    }

    @Test void manualImportIsRepeatSafe() {
        when(users.getUserId()).thenReturn(17L);
        when(articles.existsBySlugAndIdNot(anyString(), eq(-1L))).thenReturn(true);
        assertEquals(0, service.importManualDrafts().get("created"));
        verify(articles, never()).save(any());
    }

    @Test void guestKnowledgeCannotIncludeRestrictedStaffArticles() {
        var publicArticle = new org.pms.silverocean.database.pms.entities.HelpArticle(); publicArticle.setId(1L);
        var internal = new org.pms.silverocean.database.pms.entities.HelpArticle(); internal.setId(2L); internal.setAudienceRoles("Superadmin,Support");
        when(articles.findByPublishedTrueAndActiveTrueOrderByCategoryAscTitleAsc()).thenReturn(List.of(publicArticle, internal));
        assertEquals(List.of(1L), service.guestArticles().stream().map(HelpDeskModels.ArticleView::id).toList());
    }

    private HelpConversation ownedCase(String status) {
        when(users.getUserId()).thenReturn(17L);
        HelpConversation c = new HelpConversation(); c.setId(5L); c.setStatus(status); c.setPriority("NORMAL"); c.setActiveRole("Tenant");
        when(conversations.findByIdAndUserIdAndActiveTrue(5L, 17L)).thenReturn(Optional.of(c));
        when(conversations.save(any())).thenAnswer(i -> i.getArgument(0));
        return c;
    }

    @Test void detachedConversationCarriesForwardMergeVersionAcrossAiResponse() {
        HelpConversation c = ownedCase("OPEN");
        var version = new java.util.concurrent.atomic.AtomicLong();
        when(conversations.save(any())).thenAnswer(i -> {
            HelpConversation incoming = i.getArgument(0);
            assertEquals(version.get(), incoming.getVersion(), "A stale detached version would fail the next JPA save");
            HelpConversation merged = new HelpConversation();
            org.springframework.beans.BeanUtils.copyProperties(incoming, merged);
            merged.setVersion(version.incrementAndGet());
            return merged;
        });
        var article = new org.pms.silverocean.database.pms.entities.HelpArticle(); article.setId(6L);
        article.setTitle("Workspace guidance"); article.setBody("Open Business Areas to select a workspace.");
        when(articles.findByPublishedTrueAndActiveTrueOrderByCategoryAscTitleAsc()).thenReturn(List.of(article));
        when(ai.moderate(anyString())).thenReturn(new OpenAiHelpDeskClient.ModerationResult(true, false));
        when(ai.answer(anyString(), anyString(), anyString())).thenReturn(new HelpDeskModels.AiAnswer("Open Business Areas. [Article 6]", "test-response", "test-model", false));
        assertEquals("OPEN", service.send(5L, new HelpDeskModels.SendMessage("Which workspace should I select?")).status());
        assertTrue(version.get() >= 2); assertEquals(version.get(), c.getVersion());
    }
}
