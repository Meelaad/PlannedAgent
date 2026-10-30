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

class GeminiClientTest {

    private static final String KEY = "test-key-123";
    private static final LlmRequest REQUEST = new LlmRequest("You plan weeks.", List.of("user: hi", "agent: hello"), true);

    private StubServer server;
    private GeminiClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubServer();
        client = new GeminiClient(KEY, "gemini-3.5-flash-lite", server.url() + "/v1beta", HttpClient.newHttpClient());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void sendsKeyInHeaderNotInUrl() {
        server.reply = reply("ok");
        client.complete(REQUEST);
        assertEquals(KEY, server.lastApiKeyHeader);
        assertNull(server.lastQuery);
        assertEquals("/v1beta/models/gemini-3.5-flash-lite:generateContent", server.lastPath);
    }

    @Test
    void buildsTheGeminiRequest() {
        server.reply = reply("ok");
        client.complete(REQUEST);
        JsonObject body = JsonParser.parseString(server.lastBody).getAsJsonObject();
        assertEquals("You plan weeks.", body.getAsJsonObject("systemInstruction")
                .getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString());
        JsonObject content = body.getAsJsonArray("contents").get(0).getAsJsonObject();
        assertEquals("user", content.get("role").getAsString());
        assertEquals("user: hi\n\nagent: hello",
                content.getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString());
        assertEquals("application/json",
                body.getAsJsonObject("generationConfig").get("responseMimeType").getAsString());
    }

    @Test
    void readsTheTextAndSkipsThoughts() {
        server.reply = """
                {"candidates":[{"content":{"role":"model","parts":[
                  {"text":"thinking...","thought":true},
                  {"text":"{\\"answer\\": "},
                  {"text":"\\"done\\"}"}]},"finishReason":"STOP"}]}
                """;
        assertEquals("{\"answer\": \"done\"}", client.complete(REQUEST));
    }

    @Test
    void quotaErrorIsClear() {
        server.status = 429;
        server.reply = "{\"error\":{\"code\":429,\"message\":\"Resource exhausted\"}}";
        LlmException e = assertThrows(LlmException.class, () -> client.complete(REQUEST));
        assertTrue(e.getMessage().contains("429"));
    }

    @Test
    void badKeyErrorShowsGoogleMessageButNotTheKey() {
        server.status = 400;
        server.reply = "{\"error\":{\"code\":400,\"message\":\"API key not valid. Please pass a valid API key.\"}}";
        LlmException e = assertThrows(LlmException.class, () -> client.complete(REQUEST));
        assertTrue(e.getMessage().contains("API key not valid"));
        assertFalse(e.getMessage().contains(KEY));
    }

    @Test
    void blockedPromptIsAnError() {
        server.reply = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";
        LlmException e = assertThrows(LlmException.class, () -> client.complete(REQUEST));
        assertTrue(e.getMessage().contains("SAFETY"));
    }

    @Test
    void garbageReplyIsAnError() {
        server.reply = "<html>oops</html>";
        assertThrows(LlmException.class, () -> client.complete(REQUEST));
    }

    @Test
    void unreachableServerIsAnError() {
        server.close();
        assertThrows(LlmException.class, () -> client.complete(REQUEST));
    }

    @Test
    void rejectsMissingKeyAndOddModelNames() {
        assertThrows(IllegalArgumentException.class, () -> new GeminiClient(" ", "gemini-3.5-flash-lite"));
        assertThrows(IllegalArgumentException.class, () -> new GeminiClient(KEY, "../../evil"));
    }

    private static String reply(String text) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + text + "\"}]}}]}";
    }
}
