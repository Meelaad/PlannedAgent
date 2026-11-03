package ca.yorku.plannedagent.app;

import ca.yorku.plannedagent.ai.GeminiClient;
import ca.yorku.plannedagent.ai.LlmClient;
import ca.yorku.plannedagent.ai.LlmException;
import ca.yorku.plannedagent.ai.LlmRequest;
import ca.yorku.plannedagent.ai.OllamaClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @TempDir
    Path dir;

    @Test
    void readsTheEnvFile() throws Exception {
        Path env = dir.resolve(".env");
        Files.writeString(env, """
                # a comment
                PLANNER_LLM=ollama
                export OLLAMA_MODEL="qwen3.5"
                GEMINI_API_KEY='abc=def'
                PLANNER_LAT=43.5 # downtown
                broken line
                """);
        Config config = Config.load(env, Map.of());

        assertEquals("ollama", config.get("PLANNER_LLM", "gemini"));
        assertEquals("qwen3.5", config.get("OLLAMA_MODEL", "x"));
        assertEquals("abc=def", config.get("GEMINI_API_KEY", ""));
        assertEquals(43.5, config.getDouble("PLANNER_LAT", 0));
        assertEquals("fallback", config.get("MISSING", "fallback"));
    }

    @Test
    void environmentWinsOverTheFile() throws Exception {
        Path env = dir.resolve(".env");
        Files.writeString(env, "PLANNER_LLM=ollama\n");
        Config config = Config.load(env, Map.of("PLANNER_LLM", "gemini"));
        assertEquals("gemini", config.get("PLANNER_LLM", ""));
    }

    @Test
    void missingFileIsFine() {
        Config config = Config.load(dir.resolve("nope.env"), Map.of());
        assertEquals("gemini", config.get("PLANNER_LLM", "gemini"));
    }

    @Test
    void badNumberIsReported() throws Exception {
        Path env = dir.resolve(".env");
        Files.writeString(env, "PLANNER_LAT=north\n");
        assertThrows(IllegalArgumentException.class, () -> Config.load(env, Map.of()).getDouble("PLANNER_LAT", 0));
    }

    @Test
    void picksTheLlmAdapter() throws Exception {
        assertInstanceOf(OllamaClient.class, App.createLlm(config("PLANNER_LLM=ollama")));
        assertInstanceOf(GeminiClient.class, App.createLlm(config("GEMINI_API_KEY=real-looking-key")));
    }

    @Test
    void missingKeyStillStartsButAiCallsExplain() throws Exception {
        for (String setup : List.of("", "GEMINI_API_KEY=" + App.KEY_PLACEHOLDER, "PLANNER_LLM=chatgpt")) {
            LlmClient llm = App.createLlm(config(setup));
            LlmException e = assertThrows(LlmException.class,
                    () -> llm.complete(new LlmRequest("s", List.of(), false)));
            assertFalse(e.getMessage().isBlank());
        }
    }

    private Config config(String content) throws Exception {
        Path env = Files.createTempFile(dir, "env", ".env");
        Files.writeString(env, content);
        return Config.load(env, Map.of());
    }
}
