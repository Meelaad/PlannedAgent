package ca.yorku.plannedagent.ai;

import ca.yorku.plannedagent.StubServer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OllamaClientTest {

    private static final LlmRequest REQUEST = new LlmRequest("You plan weeks.", List.of("user: hi"), true);

    private StubServer server;
    private OllamaClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubServer();
        client = new OllamaClient(server.url() + "/", "qwen3.5", HttpClient.newHttpClient());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void buildsTheOllamaRequest() {
        server.reply = "{\"message\":{\"role\":\"assistant\",\"content\":\"{}\"},\"done\":true}";
        client.complete(REQUEST);
        assertEquals("/api/chat", server.lastPath);
        JsonObject body = JsonParser.parseString(server.lastBody).getAsJsonObject();
        assertEquals("qwen3.5", body.get("model").getAsString());
        assertFalse(body.get("stream").getAsBoolean());
        assertEquals("json", body.get("format").getAsString());
        assertEquals("system", body.getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("user: hi", body.getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString());
    }

    @Test
    void plainTextRequestHasNoFormat() {
        server.reply = "{\"message\":{\"content\":\"Nice week!\"}}";
        assertEquals("Nice week!", client.complete(new LlmRequest("s", List.of("user: x"), false)));
        assertFalse(JsonParser.parseString(server.lastBody).getAsJsonObject().has("format"));
    }

    @Test
    void missingModelTellsHowToPullIt() {
        server.status = 404;
        server.reply = "{\"error\":\"model 'qwen3.5' not found\"}";
        LlmException e = assertThrows(LlmException.class, () -> client.complete(REQUEST));
        assertTrue(e.getMessage().contains("ollama pull qwen3.5"));
    }

    @Test
    void emptyAnswerIsAnError() {
        server.reply = "{\"message\":{\"content\":\"  \"}}";
        assertThrows(LlmException.class, () -> client.complete(REQUEST));
    }

    @Test
    void notRunningIsAClearError() {
        server.close();
        LlmException e = assertThrows(LlmException.class, () -> client.complete(REQUEST));
        assertTrue(e.getMessage().contains("Ollama"));
    }

    @Test
    void rejectsBadUrl() {
        assertThrows(IllegalArgumentException.class, () -> new OllamaClient("file:///etc", "qwen3.5"));
    }
}
