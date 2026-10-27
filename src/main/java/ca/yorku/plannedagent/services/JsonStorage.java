package ca.yorku.plannedagent.services;

import ca.yorku.plannedagent.model.DoneState;
import ca.yorku.plannedagent.model.MissedState;
import ca.yorku.plannedagent.model.ScheduledState;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TaskState;
import ca.yorku.plannedagent.model.TodoState;
import ca.yorku.plannedagent.model.Workspace;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/** Saves the workspace in one JSON file. Writes go to a temp file first, so a crash never leaves half a file. */
public class JsonStorage {

    private final Path file;
    private final Gson gson;

    public JsonStorage(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.gson = createGson();
    }

    /** Returns an empty workspace if the file does not exist yet. A damaged file is reported, never overwritten. */
    public Workspace load() {
        if (!Files.exists(file)) {
            return new Workspace();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Workspace workspace = gson.fromJson(json, Workspace.class);
            if (workspace == null) {
                return new Workspace();
            }
            repair(workspace);
            return workspace;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        } catch (JsonParseException | IllegalArgumentException | DateTimeParseException e) {
            throw new IllegalStateException("The data file " + file + " is damaged: " + e.getMessage(), e);
        }
    }

    public void save(Workspace workspace) {
        Objects.requireNonNull(workspace, "workspace");
        try {
            Path dir = file.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, "workspace", ".tmp");
            try {
                Files.writeString(temp, gson.toJson(workspace), StandardCharsets.UTF_8);
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + file, e);
        }
    }

    public Path getFile() {
        return file;
    }

    private static void repair(Workspace workspace) {
        for (Task task : workspace.getTasks()) {
            if (task.getState() == null) {
                task.setState(new TodoState());
            }
            task.setPriority(task.getPriority());
        }
    }

    static Gson createGson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter().nullSafe())
                .registerTypeHierarchyAdapter(TaskState.class, new TaskStateAdapter().nullSafe())
                .create();
    }

    private static final class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            out.value(value.toString());
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            return LocalDateTime.parse(in.nextString());
        }
    }

    private static final class TaskStateAdapter extends TypeAdapter<TaskState> {
        @Override
        public void write(JsonWriter out, TaskState value) throws IOException {
            out.value(value.name());
        }

        @Override
        public TaskState read(JsonReader in) throws IOException {
            if (in.peek() != JsonToken.STRING) {
                throw new JsonParseException("A task state must be a string");
            }
            String name = in.nextString();
            return switch (name) {
                case "TODO" -> new TodoState();
                case "SCHEDULED" -> new ScheduledState();
                case "MISSED" -> new MissedState();
                case "DONE" -> new DoneState();
                default -> throw new JsonParseException("Unknown task state " + name);
            };
        }
    }
}
