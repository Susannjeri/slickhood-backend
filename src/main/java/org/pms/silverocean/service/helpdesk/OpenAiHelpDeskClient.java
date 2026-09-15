package org.pms.silverocean.service.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.util.HashMap;
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
                                @Value("${helpdesk.ai.base-url:${HELPDESK_AI_BASE_URL:https://api.openai.com/v1}}") String baseUrl,
                                @Value("${helpdesk.ai.api-key:${OPENAI_API_KEY:}}") String apiKey,
                                @Value("${helpdesk.ai.model:${OPENAI_HELPDESK_MODEL:gpt-5-mini}}") String model,
                                @Value("${helpdesk.ai.enabled:${HELPDESK_AI_ENABLED:true}}") boolean enabled,
                                @Value("${helpdesk.ai.connect-timeout:PT3S}") Duration connectTimeout,
                                @Value("${helpdesk.ai.read-timeout:PT20S}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.client = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = enabled;
    }

    public boolean available() { return enabled && apiKey != null && !apiKey.isBlank(); }

    public ModerationResult moderate(String input) {
        if (!available()) return new ModerationResult(false, false);
        try {
            JsonNode result = client.post().uri("/moderations").contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of("model", "omni-moderation-latest", "input", input))
                    .retrieve().body(JsonNode.class);
            JsonNode flagged = result == null ? null : result.path("results").path(0).path("flagged");
            if (flagged == null || !flagged.isBoolean()) return new ModerationResult(false, false);
            return new ModerationResult(true, flagged.booleanValue());
        } catch (Exception e) {
            log.warn("Help-desk moderation unavailable: {}", e.getClass().getSimpleName());
            return new ModerationResult(false, false);
        }
    }

    public HelpDeskModels.AiAnswer answer(String instructions, String prompt, String safetyIdentifier) {
        if (!available()) throw new IllegalStateException("AI help desk is not configured");
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("store", false);
        body.put("instructions", instructions);
        body.put("input", List.of(Map.of("role", "user", "content",
                List.of(Map.of("type", "input_text", "text", prompt)))));
        body.put("max_output_tokens", 2000);
        body.put("safety_identifier", safetyIdentifier);
        body.put("prompt_cache_key", "slickhood-help-v3");
        body.put("text", Map.of("format", Map.of("type", "json_schema", "name", "slickhood_help_answer", "strict", true,
                "schema", Map.of("type", "object", "additionalProperties", false,
                        "required", List.of("answer", "needs_human_support", "article_ids"),
                        "properties", Map.of("answer", Map.of("type", "string"),
                                "needs_human_support", Map.of("type", "boolean"),
                                "article_ids", Map.of("type", "array", "items", Map.of("type", "integer")))))));
        JsonNode result = client.post().uri("/responses").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve().body(JsonNode.class);
        if (result == null || !"completed".equals(result.path("status").asText()) || !result.path("output").isArray())
            throw new IllegalStateException("AI response was not completed");
        StringBuilder text = new StringBuilder();
        for (JsonNode output : result.path("output")) {
            if (!"message".equals(output.path("type").asText())) continue;
            if (!"completed".equals(output.path("status").asText()) || !"assistant".equals(output.path("role").asText()))
                throw new IllegalStateException("Incomplete AI message");
            for (JsonNode content : output.path("content")) {
                if ("refusal".equals(content.path("type").asText())) throw new IllegalStateException("AI refused the request");
                if ("output_text".equals(content.path("type").asText())) text.append(content.path("text").asText());
            }
        }
        if (text.isEmpty()) throw new IllegalStateException("AI response contained no text");
        try {
            JsonNode parsed = new ObjectMapper().readTree(text.toString());
            if (!parsed.isObject() || parsed.size() != 3 || !parsed.path("answer").isTextual()
                    || !parsed.path("needs_human_support").isBoolean() || !parsed.path("article_ids").isArray())
                throw new IllegalStateException("Invalid AI answer format");
            String answer = parsed.path("answer").asText().trim();
            if (answer.isBlank() || answer.length() > 4000 || parsed.path("article_ids").size() > 4)
                throw new IllegalStateException("Invalid AI answer bounds");
            java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
            for (JsonNode id : parsed.path("article_ids")) {
                if (!id.isIntegralNumber() || !id.canConvertToLong() || id.asLong() <= 0)
                    throw new IllegalStateException("Invalid AI citation");
                ids.add(id.asLong());
            }
            boolean escalated = parsed.path("needs_human_support").asBoolean();
            if (!escalated && ids.isEmpty()) throw new IllegalStateException("AI answer has no evidence");
            String citations = ids.stream().map(id -> "[Article " + id + "]").collect(java.util.stream.Collectors.joining(" "));
            return new HelpDeskModels.AiAnswer(answer + (citations.isBlank() ? "" : "\n\n" + citations),
                    result.path("id").asText(null), result.path("model").asText(model), escalated, List.copyOf(ids));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Invalid AI answer JSON");
        }
    }

    public record ModerationResult(boolean available, boolean flagged) {}
}
