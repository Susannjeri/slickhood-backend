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
    public record StartConversation(@NotBlank @Size(max=180) String subject) {}
    public record SendMessage(@NotBlank @Size(max=4000) String message) {}
    public record Escalate(@Size(max=500) String reason, String priority) {}
    public record AgentReply(@NotBlank @Size(max=4000) String message) {}
    public record ArticleUpsert(@NotBlank @Size(max=160) String slug, @NotBlank @Size(max=200) String title,
                                @NotBlank @Size(max=80) String category, @NotBlank String body,
                                @Size(max=500) String keywords, @Size(max=500) String audienceRoles, boolean published) {}
    public record MessageView(long id, String senderType, String content, ZonedDateTime createdOn,
                              String model, String sourceArticleIds) {
        public MessageView(HelpMessage m) { this(m.getId(), m.getSenderType(), m.getContent(), m.getCreatedOn(), m.getModel(), m.getSourceArticleIds()); }
    }
    public record ConversationView(long id, String subject, String status, String priority, String activeRole,
                                   Long assignedToUserId, LocalDateTime lastMessageAt, List<MessageView> messages) {
        public ConversationView(HelpConversation c, List<HelpMessage> messages) {
            this(c.getId(), c.getSubject(), c.getStatus(), c.getPriority(), c.getActiveRole(), c.getAssignedToUserId(),
                    c.getLastMessageAt(), messages.stream().map(MessageView::new).toList());
        }
    }
    public record ArticleView(long id, String slug, String title, String category, String body,
                              String keywords, String audienceRoles, boolean published) {
        public ArticleView(HelpArticle a) { this(a.getId(), a.getSlug(), a.getTitle(), a.getCategory(), a.getBody(), a.getKeywords(), a.getAudienceRoles(), a.isPublished()); }
    }
    public record AiAnswer(String text, String responseId, String model, boolean escalated) {}
}
