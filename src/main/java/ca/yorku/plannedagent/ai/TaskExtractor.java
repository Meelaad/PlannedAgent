package ca.yorku.plannedagent.ai;

import ca.yorku.plannedagent.model.Priority;
import ca.yorku.plannedagent.model.Task;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Turns one sentence ("EECS report Friday, about 3 hours, gym 3 times") into tasks with a single LLM call.
 * The LLM only reads the text; the tasks are built and checked in Java. Nothing is saved here.
 */
public class TaskExtractor {

    static final int MAX_TEXT_LENGTH = 2000;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd", Locale.ENGLISH);

    private final LlmClient llm;

    public TaskExtractor(LlmClient llm) {
        this.llm = Objects.requireNonNull(llm, "llm");
    }

    public List<Task> extract(String text, LocalDate today) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Type some tasks first");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("That text is too long (max " + MAX_TEXT_LENGTH + " characters)");
        }
        String reply = llm.complete(new LlmRequest(systemPrompt(today), List.of("user: " + text.trim()), true));
        return readTasks(reply);
    }

    private List<Task> readTasks(String json) {
        JsonArray items;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonArray()) {
                items = root.getAsJsonArray();
            } else if (root.isJsonObject() && root.getAsJsonObject().has("tasks")
                    && root.getAsJsonObject().get("tasks").isJsonArray()) {
                items = root.getAsJsonObject().getAsJsonArray("tasks");
            } else {
                throw new LlmException("The AI reply did not contain a task list");
            }
        } catch (JsonParseException | IllegalStateException e) {
            throw new LlmException("The AI reply was not valid JSON", e);
        }

        List<Task> tasks = new ArrayList<>();
        for (JsonElement item : items) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject o = item.getAsJsonObject();
            String title = string(o, "title");
            if (title == null || title.isBlank()) {
                continue;
            }
            tasks.add(new Task(title, minutes(o), deadline(string(o, "deadline")),
                    Priority.parseOrMedium(string(o, "priority")), bool(o, "outdoor")));
        }
        return tasks;
    }

    private static String systemPrompt(LocalDate today) {
        StringBuilder days = new StringBuilder();
        for (int i = 1; i <= 7; i++) {
            days.append(i == 1 ? "" : ", ").append(DAY.format(today.plusDays(i)));
        }
        return """
                You turn a student's quick note into a list of tasks.
                Today is %s. The next days are: %s.
                Reply with JSON only, in exactly this form:
                {"tasks": [{"title": "EECS report", "minutes": 180, "deadline": "2026-10-30T23:59", "priority": "HIGH", "outdoor": false}]}
                Rules:
                - One entry per separate task. "gym 3 times" means three tasks: "Gym (1/3)", "Gym (2/3)", "Gym (3/3)".
                - minutes: how long the task takes, from 5 to 600. "about 3 hours" is 180. Guess sensibly if not given.
                - deadline: local date and time as YYYY-MM-DDTHH:MM, or null if the note gives none.
                  A day name means the next such day from the list above. With no time, use 23:59.
                  A day belongs only to the task it is written next to. A task with no day of its own
                  gets null; never copy another task's deadline. Example: "report Friday, gym twice"
                  gives the report a Friday deadline and both gym tasks null.
                - priority: HIGH for exams, assignments and anything due within 2 days, LOW for chores with
                  no deadline, otherwise MEDIUM.
                - outdoor: true only for things done outside, like a run, a bike ride or a walk.
                - Keep titles short. Do not invent tasks that are not in the note.
                """.formatted(DAY.format(today), days);
    }

    private static int minutes(JsonObject o) {
        JsonElement value = o.get("minutes");
        if (value == null || !value.isJsonPrimitive()) {
            return 60;
        }
        try {
            return (int) Math.round(Double.parseDouble(value.getAsString().trim()));
        } catch (NumberFormatException e) {
            return 60;
        }
    }

    private static LocalDateTime deadline(String text) {
        if (text == null || text.isBlank() || text.equalsIgnoreCase("null")) {
            return null;
        }
        String value = text.trim();
        try {
            if (value.length() == 10) {
                return LocalDate.parse(value).atTime(LocalTime.of(23, 59));
            }
            return LocalDateTime.parse(value.length() > 16 ? value.substring(0, 16) : value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String string(JsonObject o, String name) {
        JsonElement value = o.get(name);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? null : value.getAsString();
    }

    private static boolean bool(JsonObject o, String name) {
        JsonElement value = o.get(name);
        return value != null && value.isJsonPrimitive() && Boolean.parseBoolean(value.getAsString());
    }
}
