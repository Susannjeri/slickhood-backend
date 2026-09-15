package org.pms.silverocean.service.helpdesk;

import org.pms.silverocean.database.pms.entities.HelpArticle;
import java.util.*;
import java.util.stream.Collectors;

/** Local lexical retrieval only: no private business records or external search. */
final class HelpKnowledgeSearch {
    private HelpKnowledgeSearch() {}
    private static final Set<String> STOP = Set.of("the", "and", "for", "with", "this", "that", "have", "has", "how", "what",
            "which", "where", "when", "can", "could", "would", "should", "does", "did", "not", "are", "was", "were",
            "you", "your", "please", "help", "need", "want", "more", "about", "from", "into", "there", "here", "why", "will", "all");
    static Set<String> terms(String text) {
        return Arrays.stream(Objects.toString(text, "").toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(t -> t.length() > 2 && !STOP.contains(t)).map(HelpKnowledgeSearch::canonical).collect(Collectors.toSet());
    }
    private static String canonical(String word) {
        String value = word.endsWith("ies") ? word.substring(0, word.length() - 3) + "y"
                : word.endsWith("s") && word.length() > 4 && !word.endsWith("ss") ? word.substring(0, word.length() - 1) : word;
        return switch (value) {
            case "invitation", "invited", "inviting" -> "invite";
            case "registration", "registering", "registered", "signup" -> "register";
            case "verification", "verified", "verifying" -> "verify";
            case "started", "starting" -> "start";
            case "pay", "paid", "paying" -> "payment";
            case "courier", "rider" -> "rider";
            default -> value;
        };
    }
    static int score(HelpArticle article, Set<String> query) {
        Set<String> title = terms(article.getTitle()), keywords = terms(article.getKeywords()), body = terms(article.getBody());
        int weighted = 0, bodyMatches = 0;
        for (String term : query) {
            if (title.contains(term)) weighted += 6;
            if (keywords.contains(term)) weighted += 4;
            if (body.contains(term)) { weighted++; bodyMatches++; }
        }
        // One incidental body word is not evidence that a broad chapter answers a question.
        return weighted >= 4 || bodyMatches >= 2 ? weighted : 0;
    }
    static List<HelpArticle> rank(List<HelpArticle> articles, String input) {
        Set<String> query = terms(input);
        return articles.stream().map(a -> Map.entry(a, score(a, query))).filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<HelpArticle, Integer>comparingByValue().reversed()
                        .thenComparing(e -> e.getKey().getId())).limit(4).map(Map.Entry::getKey).toList();
    }
    static String excerpt(HelpArticle article, String input) {
        String body = Objects.toString(article.getBody(), "");
        if (body.length() <= 4500) return body;
        Set<String> query = terms(input);
        String[] paragraphs = body.split("\\n\\s*\\n");
        List<Integer> selected = new ArrayList<>();
        selected.add(0); // Keep the chapter's purpose and cautions.
        List<Integer> ranked = new ArrayList<>();
        for (int i = 1; i < paragraphs.length; i++) ranked.add(i);
        ranked.sort(Comparator.<Integer>comparingInt(i -> (int) terms(paragraphs[i]).stream().filter(query::contains).count()).reversed());
        int length = Math.min(paragraphs[0].length(), 4500);
        for (int i : ranked) {
            if (Collections.disjoint(terms(paragraphs[i]), query)) continue;
            if (length + paragraphs[i].length() + 2 <= 4500) { selected.add(i); length += paragraphs[i].length() + 2; }
        }
        Collections.sort(selected);
        String result = selected.stream().map(i -> paragraphs[i]).collect(Collectors.joining("\n\n"));
        return result.substring(0, Math.min(result.length(), 4500)) + "\n[Excerpt only; omitted text is not evidence.]";
    }
}
