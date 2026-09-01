package org.pms.silverocean.service.helpdesk;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.pms.silverocean.database.pms.entities.HelpArticle;
import org.pms.silverocean.database.pms.entities.HelpConversation;
import org.pms.silverocean.database.pms.entities.HelpMessage;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

public final class HelpDeskModels {
    private HelpDeskModels() {}
    public record StartConversation(@NotBlank @Size(max=180) String subject, @Size(max=60) String category,
                                    @Size(max=255) String pageContext) {
        public StartConversation(String subject) { this(subject, null, null); }
    }
    public record GuestStart(@NotBlank @Size(max=180) String subject, @Size(max=60) String category,
                             @Size(max=255) String pageContext) {}
    public record SendMessage(@NotBlank @Size(max=4000) String message, @Size(max=64) String idempotencyKey) {
        public SendMessage(String message) { this(message, null); }
    }
    public record Escalate(@Size(max=500) String reason, String priority) {}
    public record AgentReply(@NotBlank @Size(max=4000) String message, @Size(max=64) String idempotencyKey) {
        public AgentReply(String message) { this(message, null); }
    }
    public record InternalNote(@NotBlank @Size(max=4000) String message) {}
    public record ArticleUpsert(@NotBlank @Size(max=160) String slug, @NotBlank @Size(max=200) String title,
                                @NotBlank @Size(max=80) String category, @NotBlank String body,
                                @Size(max=500) String keywords, @Size(max=500) String audienceRoles, boolean published) {}
    public record MessageView(long id, String senderType, String content, ZonedDateTime createdOn,
                              String model, String sourceArticleIds, boolean internalNote) {
        public MessageView(HelpMessage m) { this(m.getId(), m.getSenderType(), m.getContent(), m.getCreatedOn(), m.getModel(), m.getSourceArticleIds(), m.isInternalNote()); }
    }
    public record ConversationView(long id, String ticketNumber, String subject, String category, String pageContext,
                                   String status, String priority, String activeRole, Long assignedToUserId,
                                   LocalDateTime lastMessageAt, LocalDateTime waitingSince, LocalDateTime slaDueAt,
                                   LocalDateTime slaBreachedAt, LocalDateTime firstResponseAt,
                                   int customerUnreadCount, int agentUnreadCount, List<MessageView> messages) {
        public ConversationView(HelpConversation c, List<HelpMessage> messages, boolean includeInternalNotes) {
            this(c.getId(), c.getTicketNumber(), c.getSubject(), c.getCategory(), c.getPageContext(), c.getStatus(),
                    c.getPriority(), c.getActiveRole(), c.getAssignedToUserId(), c.getLastMessageAt(), c.getWaitingSince(),
                    c.getSlaDueAt(), c.getSlaBreachedAt(), c.getFirstResponseAt(), c.getCustomerUnreadCount(), c.getAgentUnreadCount(), messages.stream()
                            .filter(m -> includeInternalNotes || !m.isInternalNote()).map(MessageView::new).toList());
        }
    }
    public record GuestConversation(ConversationView conversation, String accessToken, LocalDateTime expiresAt) {}
    public record SupportSummary(long waitingForSupport, long unassigned, long slaBreached, long waitingForCustomer) {}
    public record ArticleView(long id, String slug, String title, String category, String body,
                              String keywords, String audienceRoles, boolean published) {
        public ArticleView(HelpArticle a) { this(a.getId(), a.getSlug(), a.getTitle(), a.getCategory(), a.getBody(), a.getKeywords(), a.getAudienceRoles(), a.isPublished()); }
    }
    public record AiAnswer(String text, String responseId, String model, boolean escalated) {}
}
