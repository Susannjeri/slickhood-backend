package org.pms.silverocean.service.helpdesk;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import java.net.InetSocketAddress;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiHelpDeskClientTest {
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
