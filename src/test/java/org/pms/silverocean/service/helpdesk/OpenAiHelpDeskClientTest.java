package org.pms.silverocean.service.helpdesk;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import java.net.InetSocketAddress;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiHelpDeskClientTest {
    @Test void completedStructuredAnswerUsesExplicitCitationsAndDoesNotStoreProviderData() throws Exception {
        var answer = answer("completed", "{\"answer\":\"Open Billing.\",\"needs_human_support\":false,\"article_ids\":[6]}");
        assertEquals("Open Billing.\n\n[Article 6]", answer.text());
        assertEquals(java.util.List.of(6L), answer.articleIds()); assertFalse(answer.escalated());
    }
    @Test void humanHandoffMayHaveNoCitation() throws Exception {
        assertTrue(answer("completed", "{\"answer\":\"Support must investigate this payment.\",\"needs_human_support\":true,\"article_ids\":[]}").escalated());
    }
    @Test void incompleteAndMalformedAnswersFailClosed() throws Exception {
        assertThrows(IllegalStateException.class, () -> answer("incomplete", "{\"answer\":\"Open Billing.\",\"needs_human_support\":false,\"article_ids\":[6]}"));
        for (String invalid : new String[]{"not json", "{}", "{\"answer\":\"Open Billing\",\"needs_human_support\":false,\"article_ids\":[]}",
                "{\"answer\":\"Open Billing\",\"needs_human_support\":\"false\",\"article_ids\":[6]}",
                "{\"answer\":\"\",\"needs_human_support\":false,\"article_ids\":[6]}",
                "{\"answer\":\"Open Billing\",\"needs_human_support\":false,\"article_ids\":[\"6\"]}",
                "{\"answer\":\"Open Billing\",\"needs_human_support\":false,\"article_ids\":[-1]}"}) {
            assertThrows(IllegalStateException.class, () -> answer("completed", invalid));
        }
    }
    @Test void refusalAndIncompleteMessageAreNotAnswers() throws Exception {
        assertThrows(IllegalStateException.class, () -> requestAnswer("{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"status\":\"completed\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"No\"}]}]}"));
        assertThrows(IllegalStateException.class, () -> requestAnswer("{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"status\":\"incomplete\",\"content\":[]}]}"));
    }
    private HelpDeskModels.AiAnswer answer(String status, String json) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return requestAnswer(mapper.writeValueAsString(java.util.Map.of("id", "synthetic-response", "model", "gpt-5-mini", "status", status,
                "output", java.util.List.of(java.util.Map.of("type", "message", "role", "assistant", "status", "completed",
                        "content", java.util.List.of(java.util.Map.of("type", "output_text", "text", json)))))));
    }
    private HelpDeskModels.AiAnswer requestAnswer(String payload) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var captured = new java.util.concurrent.atomic.AtomicReference<com.fasterxml.jackson.databind.JsonNode>();
        server.createContext("/responses", exchange -> {
            captured.set(new com.fasterxml.jackson.databind.ObjectMapper().readTree(exchange.getRequestBody()));
            byte[] bytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        server.start();
        try {
            var client = new OpenAiHelpDeskClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(),
                    "synthetic-test", "gpt-5-mini", true, Duration.ofSeconds(1), Duration.ofSeconds(1));
            return client.answer("Synthetic instructions", "Synthetic question", "hashed-synthetic-user");
        } finally {
            server.stop(0);
            assertNotNull(captured.get()); assertFalse(captured.get().path("store").asBoolean(true));
            assertTrue(captured.get().path("text").path("format").path("strict").asBoolean());
            assertEquals("json_schema", captured.get().path("text").path("format").path("type").asText());
        }
    }
    @Test void malformedModerationFailsClosed() throws Exception {
        for (String payload : new String[]{"{}", "{\"results\":[]}", "{\"results\":[{\"flagged\":\"false\"}]}"}) {
            assertFalse(moderate(payload).available());
        }
    }
    @Test void validModerationRetainsFlag() throws Exception {
        assertTrue(moderate("{\"results\":[{\"flagged\":true}]}").flagged());
        var allowed = moderate("{\"results\":[{\"flagged\":false}]}");
        assertTrue(allowed.available()); assertFalse(allowed.flagged());
    }
    private OpenAiHelpDeskClient.ModerationResult moderate(String payload) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/moderations", exchange -> {
            byte[] bytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        server.start();
        try {
            var client = new OpenAiHelpDeskClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(),
                    "synthetic-test", "gpt-5-mini", true, Duration.ofSeconds(1), Duration.ofSeconds(1));
            return client.moderate("How do I register?");
        } finally { server.stop(0); }
    }
}
