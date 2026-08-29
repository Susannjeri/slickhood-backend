package org.pms.silverocean.service.helpdesk;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HelpDeskService {
    private final HelpConversationRepo conversations;
    private final HelpMessageRepo messages;
    private final HelpArticleRepo articles;
    private final UserDao users;
    private final OpenAiHelpDeskClient ai;
    @Value("${helpdesk.ai.max-input-chars:4000}") private int maxInputChars;
    @Value("${helpdesk.ai.max-context-messages:12}") private int maxContextMessages;
    private final Cache<Long, AtomicInteger> rate = CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).maximumSize(10000).build();

    @Transactional
    public HelpDeskModels.ConversationView start(HelpDeskModels.StartConversation request) {
        long userId = requireUser();
        HelpConversation c = new HelpConversation();
        c.setUserId(userId); c.setCreatedBy(userId); c.setActiveRole(users.getActiveRole().getName());
        c.setSubject(request.subject().trim()); c.setStatus("OPEN"); c.setPriority("NORMAL"); c.setActive(true);
        c.setLastMessageAt(LocalDateTime.now()); conversations.save(c);
        return view(c);
    }

    public Page<HelpDeskModels.ConversationView> mine(Pageable pageable) {
        return conversations.findByUserIdAndActiveTrueOrderByLastMessageAtDesc(requireUser(), pageable).map(this::view);
    }

    public HelpDeskModels.ConversationView get(long id) { return view(owned(id)); }

    public HelpDeskModels.ConversationView send(long id, HelpDeskModels.SendMessage request) {
        long userId = requireUser();
        if (rate.asMap().computeIfAbsent(userId, ignored -> new AtomicInteger()).incrementAndGet() > 20) {
            throw new IllegalArgumentException("Please wait before sending more messages.");
        }
        HelpConversation c = owned(id);
        if ("RESOLVED".equals(c.getStatus())) throw new IllegalArgumentException("This conversation is resolved.");
        String input = request.message().trim();
        if (input.length() > maxInputChars) throw new IllegalArgumentException("Message is too long.");
        saveMessage(c, "USER", input, null, null, null, userId);
        List<HelpArticle> sources = relevant(input, c.getActiveRole());
        if (ai.flagged(input)) {
            markEscalated(c, "HIGH");
            saveMessage(c, "SYSTEM", "This request has been transferred to a human support specialist.", null, null, ids(sources), userId);
            return view(c);
        }
        try {
            HelpDeskModels.AiAnswer answer = ai.answer(instructions(), prompt(c, input, sources));
            if (answer.escalated()) markEscalated(c, "NORMAL");
            saveMessage(c, "AI", answer.text(), answer.model(), answer.responseId(), ids(sources), userId);
        } catch (Exception e) {
            markEscalated(c, "NORMAL");
            String fallback = sources.isEmpty() ? "I could not confirm that safely. A human support agent will review your request."
                    : sources.getFirst().getBody() + "\n\nIf this does not resolve the issue, a human support agent will review the conversation.";
            saveMessage(c, "SYSTEM", fallback, null, null, ids(sources), userId);
        }
        return view(c);
    }

    @Transactional
    public HelpDeskModels.ConversationView escalate(long id, HelpDeskModels.Escalate request) {
        HelpConversation c = owned(id);
        markEscalated(c, normalizePriority(request.priority()));
        if (request.reason() != null && !request.reason().isBlank()) {
            saveMessage(c, "USER", request.reason().trim(), null, null, null, requireUser());
        }
        return view(c);
    }

    public List<HelpDeskModels.ArticleView> publicArticles() {
        String role = users.getActiveRole().getName();
        return articles.findByPublishedTrueAndActiveTrueOrderByCategoryAscTitleAsc().stream()
                .filter(a -> visible(a, role)).map(HelpDeskModels.ArticleView::new).toList();
    }

    public Page<HelpDeskModels.ConversationView> queue(Pageable pageable) {
        return conversations.findByStatusInAndActiveTrueOrderByLastMessageAtDesc(List.of("ESCALATED", "ASSIGNED"), pageable).map(this::view);
    }

    @Transactional
    public HelpDeskModels.ConversationView agentReply(long id, HelpDeskModels.AgentReply request) {
        HelpConversation c = conversations.findByIdAndActiveTrue(id).orElseThrow();
        long agent = requireUser(); c.setAssignedToUserId(agent); c.setStatus("ASSIGNED");
        saveMessage(c, "AGENT", request.message().trim(), null, null, null, agent); return view(c);
    }

    @Transactional
    public HelpDeskModels.ConversationView resolve(long id) {
        HelpConversation c = conversations.findByIdAndActiveTrue(id).orElseThrow();
        c.setStatus("RESOLVED"); c.setResolvedAt(LocalDateTime.now()); conversations.save(c); return view(c);
    }

    public List<HelpDeskModels.ArticleView> adminArticles() {
        return articles.findByActiveTrueOrderByCategoryAscTitleAsc().stream().map(HelpDeskModels.ArticleView::new).toList();
    }

    @Transactional
    public HelpDeskModels.ArticleView saveArticle(Long id, HelpDeskModels.ArticleUpsert r) {
        HelpArticle a = id == null ? new HelpArticle() : articles.findByIdAndActiveTrue(id).orElseThrow();
        if (articles.existsBySlugAndIdNot(r.slug(), id == null ? -1 : id)) throw new IllegalArgumentException("Article slug already exists.");
        a.setSlug(r.slug().trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-")); a.setTitle(r.title().trim());
        a.setCategory(r.category().trim()); a.setBody(r.body().trim()); a.setKeywords(r.keywords()); a.setAudienceRoles(r.audienceRoles());
        a.setPublished(r.published()); a.setActive(true); a.setCreatedBy(requireUser()); return new HelpDeskModels.ArticleView(articles.save(a));
    }

    private HelpConversation owned(long id) { return conversations.findByIdAndUserIdAndActiveTrue(id, requireUser()).orElseThrow(); }
    private long requireUser() { return Objects.requireNonNull(users.getUserId(), "Authenticated user required"); }
    private HelpDeskModels.ConversationView view(HelpConversation c) { return new HelpDeskModels.ConversationView(c, messages.findByConversationIdAndActiveTrueOrderByCreatedOnAsc(c.getId())); }
    private void markEscalated(HelpConversation c, String priority) { c.setStatus("ESCALATED"); c.setPriority(priority); c.setEscalatedAt(LocalDateTime.now()); conversations.save(c); }
    private String normalizePriority(String p) { String value=String.valueOf(p).toUpperCase(Locale.ROOT); return Set.of("LOW","NORMAL","HIGH","URGENT").contains(value) ? value : "NORMAL"; }
    private void saveMessage(HelpConversation c, String sender, String content, String model, String responseId, String sourceIds, long creator) {
        HelpMessage m = new HelpMessage(); m.setConversationId(c.getId()); m.setSenderType(sender); m.setContent(content); m.setModel(model);
        m.setProviderResponseId(responseId); m.setSourceArticleIds(sourceIds); m.setCreatedBy(creator); m.setActive(true); messages.save(m);
        c.setLastMessageAt(LocalDateTime.now()); conversations.save(c);
    }
    private List<HelpArticle> relevant(String input, String role) {
        Set<String> terms = Arrays.stream(input.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(t -> t.length() > 2).collect(Collectors.toSet());
        return articles.findByPublishedTrueAndActiveTrueOrderByCategoryAscTitleAsc().stream().filter(a -> visible(a, role))
                .map(a -> Map.entry(a, score(a, terms))).filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<HelpArticle,Integer>comparingByValue().reversed()).limit(4).map(Map.Entry::getKey).toList();
    }
    private int score(HelpArticle a, Set<String> terms) {
        String hay=(a.getTitle()+" "+Objects.toString(a.getKeywords(),"")+" "+a.getBody()).toLowerCase(Locale.ROOT);
        return (int)terms.stream().filter(hay::contains).count();
    }
    private boolean visible(HelpArticle a, String role) { return a.getAudienceRoles()==null || a.getAudienceRoles().isBlank() || Arrays.stream(a.getAudienceRoles().split(",")).map(String::trim).anyMatch(role::equalsIgnoreCase); }
    private String ids(List<HelpArticle> a) { return a.stream().map(x -> String.valueOf(x.getId())).collect(Collectors.joining(",")); }
    private String prompt(HelpConversation c, String input, List<HelpArticle> sources) {
        List<HelpMessage> history = new ArrayList<>(messages.findByConversationIdAndActiveTrueOrderByCreatedOnDesc(c.getId(), PageRequest.of(0, maxContextMessages)));
        Collections.reverse(history);
        String context = sources.stream().map(a -> "ARTICLE " + a.getId() + ": " + a.getTitle() + "\n" + a.getBody()).collect(Collectors.joining("\n\n"));
        String transcript = history.stream().map(m -> m.getSenderType()+": "+m.getContent()).collect(Collectors.joining("\n"));
        return "ACTIVE ROLE: " + c.getActiveRole() + "\n\nAPPROVED HELP ARTICLES:\n" + context + "\n\nCONVERSATION:\n" + transcript + "\n\nLATEST QUESTION:\n" + input;
    }
    private String instructions() {
        return "You are Slickhood Help, a concise support assistant for a Kenyan property platform. Answer only from APPROVED HELP ARTICLES and the conversation. Never invent account, property, payment, legal or subscription facts. Never request passwords, OTPs, PINs, full card data, API keys, identity document numbers or private keys. Never claim to perform an action. For payment disputes, legal decisions, KYC decisions, emergencies, account access problems, missing evidence, or insufficient articles, begin exactly NEEDS_HUMAN_SUPPORT: and explain the safe next step. Mention article numbers used in square brackets, for example [Article 3].";
    }
}
