package ca.yorku.plannedagent.app;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Settings from a local .env file and the environment. Environment variables win over the file.
 * Values are never printed, because the file holds the API key.
 */
public final class Config {

    private final Map<String, String> values;

    private Config(Map<String, String> values) {
        this.values = values;
    }

    public static Config load(Path envFile) {
        return load(envFile, System.getenv());
    }

    static Config load(Path envFile, Map<String, String> environment) {
        Map<String, String> values = new HashMap<>();
        if (envFile != null && Files.isRegularFile(envFile)) {
            try {
                for (String raw : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                    String line = raw.strip();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    if (line.startsWith("export ")) {
                        line = line.substring(7).strip();
                    }
                    int eq = line.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    values.put(line.substring(0, eq).strip(), unquote(line.substring(eq + 1).strip()));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + envFile, e);
            }
        }
        environment.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                values.put(key, value);
            }
        });
        return new Config(values);
    }

    public String get(String key, String fallback) {
        String value = values.get(key);
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    public double getDouble(String key, double fallback) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a number");
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        int comment = value.indexOf(" #");
        return comment >= 0 ? value.substring(0, comment).strip() : value;
    }
}
