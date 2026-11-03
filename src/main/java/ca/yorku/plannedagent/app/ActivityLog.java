package ca.yorku.plannedagent.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Observer that writes one line per event to a file: a record of what the student and the agent did. */
public class ActivityLog implements PlannerObserver {

    private final Path file;
    private final Clock clock;
    private boolean warned;

    public ActivityLog(Path file) {
        this(file, Clock.systemDefaultZone());
    }

    public ActivityLog(Path file, Clock clock) {
        this.file = Objects.requireNonNull(file, "file");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized void update(String type, String message) {
        String clean = String.valueOf(message).replaceAll("[\\r\\n]+", " / ");
        String line = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS) + " " + type + " " + clean
                + System.lineSeparator();
        try {
            Path dir = file.toAbsolutePath().getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            if (!warned) {
                System.err.println("Could not write the activity log " + file + ": " + e.getMessage());
                warned = true;
            }
        }
    }
}
