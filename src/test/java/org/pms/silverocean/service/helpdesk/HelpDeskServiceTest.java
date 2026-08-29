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
import org.springframework.test.util.ReflectionTestUtils;

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
    HelpDeskService service;

    @BeforeEach void setup() {
        service = new HelpDeskService(conversations, messages, articles, users, ai);
        ReflectionTestUtils.setField(service, "maxInputChars", 4000);
        ReflectionTestUtils.setField(service, "maxContextMessages", 12);
        when(users.getUserId()).thenReturn(17L);
    }

    @Test void startsConversationForAuthenticatedUserAndActiveRole() {
        when(users.getActiveRole()).thenReturn(PMSRole.LANDLORD);
        when(conversations.save(any())).thenAnswer(inv -> { HelpConversation c=inv.getArgument(0); c.setId(9L); return c; });
        when(messages.findByConversationIdAndActiveTrueOrderByCreatedOnAsc(9L)).thenReturn(List.of());
        var result = service.start(new HelpDeskModels.StartConversation("Rent receipt help"));
        assertEquals(9L, result.id());
        assertEquals("Landlord", result.activeRole());
        ArgumentCaptor<HelpConversation> saved = ArgumentCaptor.forClass(HelpConversation.class);
        verify(conversations).save(saved.capture());
        assertEquals(17L, saved.getValue().getUserId());
        assertTrue(saved.getValue().isActive());
    }

    @Test void conversationLookupIsScopedToAuthenticatedOwner() {
        when(conversations.findByIdAndUserIdAndActiveTrue(44L, 17L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.get(44L));
        verify(conversations, never()).findByIdAndActiveTrue(anyLong());
    }

    @Test void aiFailureEscalatesAndProvidesSafeFallback() {
        HelpConversation conversation = new HelpConversation();
        conversation.setId(5L); conversation.setUserId(17L); conversation.setActiveRole("Tenant");
        conversation.setStatus("OPEN"); conversation.setPriority("NORMAL"); conversation.setActive(true);
        when(conversations.findByIdAndUserIdAndActiveTrue(5L, 17L)).thenReturn(Optional.of(conversation));
        when(conversations.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messages.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messages.findByConversationIdAndActiveTrueOrderByCreatedOnDesc(eq(5L), any())).thenReturn(List.of());
        when(messages.findByConversationIdAndActiveTrueOrderByCreatedOnAsc(5L)).thenReturn(List.of());
        when(articles.findByPublishedTrueAndActiveTrueOrderByCategoryAscTitleAsc()).thenReturn(List.of());
        when(ai.flagged(anyString())).thenReturn(false);
        when(ai.answer(anyString(), anyString())).thenThrow(new IllegalStateException("provider unavailable"));

        var result = service.send(5L, new HelpDeskModels.SendMessage("My payment is missing"));
        assertEquals("ESCALATED", result.status());
        ArgumentCaptor<HelpMessage> saved = ArgumentCaptor.forClass(HelpMessage.class);
        verify(messages, times(2)).save(saved.capture());
        assertEquals(List.of("USER", "SYSTEM"), saved.getAllValues().stream().map(HelpMessage::getSenderType).toList());
        assertFalse(saved.getAllValues().get(1).getContent().toLowerCase().contains("api key"));
    }
}
