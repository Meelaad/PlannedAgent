package ca.yorku.plannedagent.agent.tools;

import ca.yorku.plannedagent.agent.Tool;
import ca.yorku.plannedagent.agent.ToolResult;
import ca.yorku.plannedagent.commands.MoveBlockCommand;
import ca.yorku.plannedagent.commands.PlanCommand;
import ca.yorku.plannedagent.commands.Proposal;
import ca.yorku.plannedagent.model.Conflict;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeBlock;
import ca.yorku.plannedagent.model.Workspace;
import ca.yorku.plannedagent.scheduling.ConflictChecker;
import ca.yorku.plannedagent.services.Forecast;
import ca.yorku.plannedagent.services.WeatherService;

import java.io.IOException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** move_task: proposes moving one planned block to a new start time, after checking it on a copy. */
public class MoveTaskTool implements Tool {

    private final ConflictChecker checker;
    private final WeatherService weather;
    private final Clock clock;

    public MoveTaskTool(ConflictChecker checker, WeatherService weather, Clock clock) {
        this.checker = Objects.requireNonNull(checker, "checker");
        this.weather = Objects.requireNonNull(weather, "weather");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String name() {
        return "move_task";
    }

    @Override
    public String description() {
        return "proposes moving a planned block to a new start time (same length). "
                + "Args: blockId (a b-id from get_schedule), day (a weekday like WEDNESDAY, TODAY, TOMORROW "
                + "or a date YYYY-MM-DD), time (HH:MM, 24-hour).";
    }

    @Override
    public ToolResult run(Map<String, String> args, Workspace workspace) {
        String id = args.get("blockId");
        if (id == null || id.isBlank()) {
            return ToolResult.error("Give blockId, the id of a block from get_schedule (like b3)");
        }
        id = id.trim();
        TimeBlock block = findBlock(id, workspace);
        if (block == null) {
            String wanted = id;
            if (workspace.getEvents().stream().anyMatch(e -> e.getId().equals(wanted))) {
                return ToolResult.error(id + " is a fixed event. Fixed events cannot be moved.");
            }
            return ToolResult.error("There is no planned block '" + id + "'. Call get_schedule to see block ids.");
        }

        LocalDateTime newStart;
        try {
            newStart = newStart(args);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return ToolResult.error("Give day (WEDNESDAY, TOMORROW or 2026-11-04) and time (07:00)");
        }
        if (newStart.isBefore(LocalDateTime.now(clock))) {
            return ToolResult.error("newStart " + newStart + " is in the past");
        }
        if (newStart.equals(block.getStart())) {
            return ToolResult.error("That block already starts at " + newStart);
        }

        Task task = workspace.getTask(block.getTaskId());
        String title = task == null ? block.getId() : task.getTitle();
        List<PlanCommand> commands = List.of(new MoveBlockCommand(block.getId(), newStart, title));

        String weatherNote = "";
        Forecast forecast;
        try {
            forecast = weather.forecast(newStart.toLocalDate(), newStart.toLocalDate());
        } catch (IOException e) {
            forecast = Forecast.none();
            weatherNote = "\nWarning: the weather service is not available, so rain was not checked.";
        }
        List<Conflict> conflicts = checker.checkChanges(workspace, commands, forecast);

        StringBuilder summary = new StringBuilder(commands.get(0).describe()).append('.').append(weatherNote);
        if (!conflicts.isEmpty()) {
            summary.append("\nThis move would cause conflicts, so it cannot be applied:");
            conflicts.forEach(c -> summary.append("\n- ").append(c.getMessage()));
            summary.append("\nTry another time.");
        } else {
            summary.append("\nNo conflicts. This is a proposal; the student must apply it.");
        }
        return ToolResult.ok(summary.toString(), new Proposal(summary.toString(), commands, conflicts));
    }

    /**
     * The date is worked out here, not by the LLM: small models often get the date of "Wednesday" wrong
     * even when they know it. A full newStart is still accepted.
     */
    private LocalDateTime newStart(Map<String, String> args) {
        String full = args.get("newStart");
        if (full != null && !full.isBlank() && args.get("day") == null) {
            String text = full.trim();
            return LocalDateTime.parse(text.length() > 16 ? text.substring(0, 16) : text);
        }
        String dayText = args.getOrDefault("day", "").trim().toUpperCase(Locale.ROOT);
        String timeText = args.getOrDefault("time", "").trim();
        LocalTime time = LocalTime.parse(timeText.length() == 4 ? "0" + timeText : timeText);
        LocalDate today = LocalDate.now(clock);
        LocalDate day = switch (dayText) {
            case "TODAY" -> today;
            case "TOMORROW" -> today.plusDays(1);
            default -> dayText.length() == 10 ? LocalDate.parse(dayText) : nextDay(today, time, DayOfWeek.valueOf(dayText));
        };
        return day.atTime(time);
    }

    /** The next such weekday; today counts if the time has not passed yet. */
    private LocalDate nextDay(LocalDate today, LocalTime time, DayOfWeek wanted) {
        LocalDate day = today.with(TemporalAdjusters.nextOrSame(wanted));
        if (day.equals(today) && !today.atTime(time).isAfter(LocalDateTime.now(clock))) {
            day = day.plusWeeks(1);
        }
        return day;
    }

    /** Accepts a block id, or a task id when that task has exactly one block. */
    private static TimeBlock findBlock(String id, Workspace workspace) {
        TimeBlock block = workspace.getPlan().find(id);
        if (block != null) {
            return block;
        }
        List<TimeBlock> forTask = workspace.getPlan().blocksFor(id);
        return forTask.size() == 1 ? forTask.get(0) : null;
    }
}
