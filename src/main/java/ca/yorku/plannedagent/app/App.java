package ca.yorku.plannedagent.app;

import ca.yorku.plannedagent.ai.GeminiClient;
import ca.yorku.plannedagent.ai.LlmClient;
import ca.yorku.plannedagent.ai.LlmException;
import ca.yorku.plannedagent.ai.OllamaClient;
import ca.yorku.plannedagent.model.Workspace;
import ca.yorku.plannedagent.services.JsonStorage;
import ca.yorku.plannedagent.services.OpenMeteoWeather;
import ca.yorku.plannedagent.ui.MainController;
import ca.yorku.plannedagent.ui.MainWindow;
import ca.yorku.plannedagent.ui.PlannerCli;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;

/**
 * Start-up (SD1): reads the settings, picks the LLM, loads the workspace and wires the facade.
 * With no arguments it opens the window; otherwise it runs the CLI command.
 */
public final class App {

    static final String KEY_PLACEHOLDER = "YOUR_LOCAL_API_KEY_HERE";

    private App() {
    }

    public static void main(String[] args) {
        Config config = Config.load(Path.of(".env"));
        Path dataDir = Path.of(config.get("PLANNER_DATA", Path.of(System.getProperty("user.home"), ".plannedagent").toString()));
        JsonStorage storage = new JsonStorage(dataDir.resolve("workspace.json"));
        Workspace workspace;
        try {
            workspace = storage.load();
        } catch (RuntimeException e) {
            System.err.println("Could not load your data: " + e.getMessage());
            System.exit(1);
            return;
        }

        OpenMeteoWeather weather = new OpenMeteoWeather(
                config.getDouble("PLANNER_LAT", PlannerFacade.YORK_LATITUDE),
                config.getDouble("PLANNER_LON", PlannerFacade.YORK_LONGITUDE));
        PlannerFacade facade = new PlannerFacade(workspace, createLlm(config), storage, weather,
                Clock.systemDefaultZone());
        facade.subscribe(new ActivityLog(dataDir.resolve("activity.log")));

        if (args.length > 0 || GraphicsEnvironment.isHeadless()) {
            new PlannerCli(facade).run(args);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
                // the default look and feel is fine too
            }
            MainWindow window = new MainWindow();
            facade.subscribe(new MainController(facade, window));
            window.showWindow();
        });
    }

    /** PLANNER_LLM picks the adapter. Without a usable setup the app still runs; only AI calls fail, with a hint. */
    static LlmClient createLlm(Config config) {
        String provider = config.get("PLANNER_LLM", "gemini").toLowerCase(Locale.ROOT);
        try {
            return switch (provider) {
                case "ollama" -> new OllamaClient(config.get("OLLAMA_URL", "http://localhost:11434"),
                        config.get("OLLAMA_MODEL", "qwen3.5"));
                case "gemini" -> {
                    String key = config.get("GEMINI_API_KEY", "");
                    if (key.isEmpty() || key.equals(KEY_PLACEHOLDER)) {
                        yield unavailable("No Gemini API key. Copy .env.example to .env and set GEMINI_API_KEY, "
                                + "or set PLANNER_LLM=ollama.");
                    }
                    yield new GeminiClient(key, config.get("GEMINI_MODEL", "gemini-3.5-flash-lite"));
                }
                default -> unavailable("PLANNER_LLM must be gemini or ollama, not '" + provider + "'.");
            };
        } catch (IllegalArgumentException e) {
            return unavailable(e.getMessage());
        }
    }

    private static LlmClient unavailable(String reason) {
        return request -> {
            throw new LlmException(reason);
        };
    }
}
