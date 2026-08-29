package org.pms.silverocean.service.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OpenAiHelpDeskClient {
    private final RestClient client;
    private final String apiKey;
    private final String model;
    private final boolean enabled;

    public OpenAiHelpDeskClient(RestClient.Builder builder,
                                @Value("${helpdesk.ai.base-url}") String baseUrl,
                                @Value("${helpdesk.ai.api-key:}") String apiKey,
                                @Value("${helpdesk.ai.model:gpt-5-mini}") String model,
                                @Value("${helpdesk.ai.enabled:true}") boolean enabled) {
        this.client = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = enabled;
    }

    public boolean available() { return enabled && apiKey != null && !apiKey.isBlank(); }

    public boolean flagged(String input) {
        if (!available()) return false;
        try {
            JsonNode result = client.post().uri("/moderations").contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of("model", "omni-moderation-latest", "input", input))
                    .retrieve().body(JsonNode.class);
            return result != null && result.path("results").path(0).path("flagged").asBoolean(false);
        } catch (Exception e) {
            log.warn("Help-desk moderation unavailable: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    public HelpDeskModels.AiAnswer answer(String instructions, String prompt) {
        if (!available()) throw new IllegalStateException("AI help desk is not configured");
        JsonNode result = client.post().uri("/responses").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(Map.of("model", model, "store", false, "instructions", instructions,
                        "input", List.of(Map.of("role", "user", "content", List.of(Map.of("type", "input_text", "text", prompt)))),
                        "max_output_tokens", 700))
                .retrieve().body(JsonNode.class);
        if (result == null) throw new IllegalStateException("Empty AI response");
        StringBuilder text = new StringBuilder();
        for (JsonNode output : result.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText())) text.append(content.path("text").asText());
            }
        }
        if (text.isEmpty()) throw new IllegalStateException("AI response contained no text");
        String answer = text.toString().trim();
        boolean escalated = answer.startsWith("NEEDS_HUMAN_SUPPORT:");
        if (escalated) answer = answer.substring(answer.indexOf(':') + 1).trim();
        return new HelpDeskModels.AiAnswer(answer, result.path("id").asText(null), result.path("model").asText(model), escalated);
    }
}
