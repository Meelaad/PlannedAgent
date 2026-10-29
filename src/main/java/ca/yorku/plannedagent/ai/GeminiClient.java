package ca.yorku.plannedagent.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Adapter for the Gemini REST API (generateContent). The API key goes in a header,
 * never in the URL, so it does not end up in logs or error messages.
 */
public class GeminiClient implements LlmClient {

    static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final Pattern MODEL_NAME = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final HttpClient http;

    public GeminiClient(String apiKey, String model) {
        this(apiKey, model, BASE_URL, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    GeminiClient(String apiKey, String model, String baseUrl, HttpClient http) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("A Gemini API key is needed");
        }
        if (model == null || !MODEL_NAME.matcher(model).matches()) {
            throw new IllegalArgumentException("Not a valid Gemini model name: " + model);
        }
        this.apiKey = apiKey.trim();
        this.model = model;
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.http = Objects.requireNonNull(http, "http");
    }

    @Override
    public String complete(LlmRequest request) {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/models/" + model + ":generateContent"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(toGeminiJson(request), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new LlmException("Could not reach Gemini: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("The Gemini call was interrupted", e);
        }
        if (response.statusCode() == 429) {
            throw new LlmException("The Gemini free limit is used up for now (HTTP 429). Try again later or use Ollama.");
        }
        if (response.statusCode() != 200) {
            throw new LlmException("Gemini returned HTTP " + response.statusCode() + errorDetail(response.body()));
        }
        return readText(response.body());
    }

    private String toGeminiJson(LlmRequest request) {
        JsonObject body = new JsonObject();
        body.add("systemInstruction", textContent(null, request.system));
        JsonArray contents = new JsonArray();
        contents.add(textContent("user", request.conversation()));
        body.add("contents", contents);
        if (request.wantJson) {
            JsonObject config = new JsonObject();
            config.addProperty("responseMimeType", "application/json");
            body.add("generationConfig", config);
        }
        return body.toString();
    }

    private String readText(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new LlmException("Gemini gave no answer" + blockReason(root));
            }
            JsonObject candidate = candidates.get(0).getAsJsonObject();
            JsonObject content = candidate.getAsJsonObject("content");
            if (content == null || !content.has("parts")) {
                throw new LlmException("Gemini gave an empty answer (" + stringOrNull(candidate, "finishReason") + ")");
            }
            StringBuilder text = new StringBuilder();
            for (JsonElement part : content.getAsJsonArray("parts")) {
                JsonObject p = part.getAsJsonObject();
                boolean thought = p.has("thought") && p.get("thought").getAsBoolean();
                if (!thought && p.has("text")) {
                    text.append(p.get("text").getAsString());
                }
            }
            if (text.isEmpty()) {
                throw new LlmException("Gemini gave an empty answer");
            }
            return text.toString();
        } catch (JsonParseException | IllegalStateException | ClassCastException e) {
            throw new LlmException("Could not read the Gemini reply", e);
        }
    }

    private static JsonObject textContent(String role, String text) {
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        JsonArray parts = new JsonArray();
        parts.add(part);
        JsonObject content = new JsonObject();
        if (role != null) {
            content.addProperty("role", role);
        }
        content.add("parts", parts);
        return content;
    }

    private static String blockReason(JsonObject root) {
        JsonObject feedback = root.getAsJsonObject("promptFeedback");
        String reason = feedback == null ? null : stringOrNull(feedback, "blockReason");
        return reason == null ? "" : " (blocked: " + reason + ")";
    }

    private static String errorDetail(String body) {
        try {
            JsonObject error = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("error");
            String message = error == null ? null : stringOrNull(error, "message");
            if (message == null) {
                return "";
            }
            return ": " + (message.length() > 200 ? message.substring(0, 200) + "..." : message);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String stringOrNull(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }
}
