package ca.yorku.plannedagent.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Adapter for a local model (e.g. Qwen) served by Ollama's /api/chat. No key, works offline. */
public class OllamaClient implements LlmClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(180);

    private final String baseUrl;
    private final String model;
    private final HttpClient http;

    public OllamaClient(String baseUrl, String model) {
        this(baseUrl, model, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    OllamaClient(String baseUrl, String model, HttpClient http) {
        if (baseUrl == null || !(baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))) {
            throw new IllegalArgumentException("The Ollama URL must start with http:// or https://");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("An Ollama model name is needed");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model.trim();
        this.http = http;
    }

    @Override
    public String complete(LlmRequest request) {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toOllamaJson(request), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (ConnectException e) {
            throw new LlmException("Ollama is not running at " + baseUrl + ". Start Ollama and try again.", e);
        } catch (IOException e) {
            throw new LlmException("Could not reach Ollama: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("The Ollama call was interrupted", e);
        }
        if (response.statusCode() == 404) {
            throw new LlmException("Ollama does not have the model '" + model + "'. Run: ollama pull " + model);
        }
        if (response.statusCode() != 200) {
            throw new LlmException("Ollama returned HTTP " + response.statusCode() + errorDetail(response.body()));
        }
        return readText(response.body());
    }

    private String toOllamaJson(LlmRequest request) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", false);
        body.addProperty("think", false);
        if (request.wantJson) {
            body.addProperty("format", "json");
        }
        JsonArray messages = new JsonArray();
        messages.add(message("system", request.system));
        messages.add(message("user", request.conversation()));
        body.add("messages", messages);
        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.2);
        body.add("options", options);
        return body.toString();
    }

    private String readText(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject message = root.getAsJsonObject("message");
            JsonElement content = message == null ? null : message.get("content");
            if (content == null || content.isJsonNull() || content.getAsString().isBlank()) {
                throw new LlmException("Ollama gave an empty answer");
            }
            return content.getAsString();
        } catch (JsonParseException | IllegalStateException | ClassCastException e) {
            throw new LlmException("Could not read the Ollama reply", e);
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private static String errorDetail(String body) {
        try {
            JsonElement error = JsonParser.parseString(body).getAsJsonObject().get("error");
            if (error == null || error.isJsonNull()) {
                return "";
            }
            String text = error.getAsString();
            return ": " + (text.length() > 200 ? text.substring(0, 200) + "..." : text);
        } catch (RuntimeException e) {
            return "";
        }
    }
}
