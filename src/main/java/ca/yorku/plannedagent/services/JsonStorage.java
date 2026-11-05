package ca.yorku.plannedagent.services;

import ca.yorku.plannedagent.commands.AddBlockCommand;
import ca.yorku.plannedagent.commands.CommandHistory;
import ca.yorku.plannedagent.commands.MoveBlockCommand;
import ca.yorku.plannedagent.commands.PlanCommand;
import ca.yorku.plannedagent.model.DoneState;
import ca.yorku.plannedagent.model.MissedState;
import ca.yorku.plannedagent.model.ScheduledState;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TaskState;
import ca.yorku.plannedagent.model.TodoState;
import ca.yorku.plannedagent.model.Workspace;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
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
    private final Path historyFile;
    private final Gson gson;
    private final Gson historyGson;

    public JsonStorage(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.historyFile = file.resolveSibling("history.json");
        this.gson = createGson();
        this.historyGson = gson.newBuilder()
                .registerTypeHierarchyAdapter(PlanCommand.class, new PlanCommandAdapter(gson))
                .create();
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
        writeAtomically(file, gson.toJson(workspace));
    }

    /**
     * The undo history is kept next to the workspace so undo also works between two CLI commands.
     * Unlike the workspace it is not precious: if it cannot be read, undo simply starts empty.
     */
    public CommandHistory loadHistory() {
        if (!Files.exists(historyFile)) {
            return new CommandHistory();
        }
        try {
            CommandHistory history = historyGson.fromJson(Files.readString(historyFile, StandardCharsets.UTF_8),
                    CommandHistory.class);
            return history == null ? new CommandHistory() : history;
        } catch (IOException | RuntimeException e) {
            System.err.println("The undo history could not be read and was reset: " + e.getMessage());
            return new CommandHistory();
        }
    }

    public void saveHistory(CommandHistory history) {
        Objects.requireNonNull(history, "history");
        writeAtomically(historyFile, historyGson.toJson(history));
    }

    private static void writeAtomically(Path target, String json) {
        try {
            Path dir = target.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, "plannedagent", ".tmp");
            try {
                Files.writeString(temp, json, StandardCharsets.UTF_8);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + target, e);
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

    /** Stores each command with a "type" so the right class comes back. */
    private static final class PlanCommandAdapter implements JsonSerializer<PlanCommand>, JsonDeserializer<PlanCommand> {
        private final Gson base;

        PlanCommandAdapter(Gson base) {
            this.base = base;
        }

        @Override
        public JsonElement serialize(PlanCommand command, Type type, JsonSerializationContext context) {
            JsonObject json = base.toJsonTree(command).getAsJsonObject();
            if (command instanceof AddBlockCommand) {
                json.addProperty("type", "add");
            } else if (command instanceof MoveBlockCommand) {
                json.addProperty("type", "move");
            } else {
                throw new JsonParseException("Cannot save command " + command.getClass().getSimpleName());
            }
            return json;
        }

        @Override
        public PlanCommand deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            JsonElement kind = json.getAsJsonObject().get("type");
            String name = kind == null ? "" : kind.getAsString();
            return switch (name) {
                case "add" -> base.fromJson(json, AddBlockCommand.class);
                case "move" -> base.fromJson(json, MoveBlockCommand.class);
                default -> throw new JsonParseException("Unknown command type '" + name + "'");
            };
        }
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
