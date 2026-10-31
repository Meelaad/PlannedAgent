package ca.yorku.plannedagent.agent.tools;

import ca.yorku.plannedagent.agent.Tool;
import ca.yorku.plannedagent.agent.ToolResult;
import ca.yorku.plannedagent.model.FixedEvent;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeBlock;
import ca.yorku.plannedagent.model.TimeFormat;
import ca.yorku.plannedagent.model.Workspace;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** get_schedule: lists fixed events and planned blocks for some days. Read only. */
public class GetScheduleTool implements Tool {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd", Locale.ENGLISH);

    private final Clock clock;

    public GetScheduleTool(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String name() {
        return "get_schedule";
    }

    @Override
    public String description() {
        return "lists fixed events and planned blocks (with block ids) day by day. "
                + "Args: from (YYYY-MM-DD, default today), days (1-14, default 7).";
    }

    @Override
    public ToolResult run(Map<String, String> args, Workspace workspace) {
        LocalDate from;
        int days;
        try {
            String fromText = args.get("from");
            from = fromText == null || fromText.isBlank() ? LocalDate.now(clock) : LocalDate.parse(fromText.trim());
        } catch (DateTimeParseException e) {
            return ToolResult.error("'from' must be a date like 2026-11-02");
        }
        try {
            String daysText = args.get("days");
            days = daysText == null || daysText.isBlank() ? 7 : (int) Double.parseDouble(daysText.trim());
        } catch (NumberFormatException e) {
            return ToolResult.error("'days' must be a number from 1 to 14");
        }
        if (days < 1 || days > 14) {
            return ToolResult.error("'days' must be a number from 1 to 14");
        }

        StringBuilder text = new StringBuilder();
        for (LocalDate day = from; day.isBefore(from.plusDays(days)); day = day.plusDays(1)) {
            List<String> lines = new ArrayList<>();
            List<TimeBlock> items = new ArrayList<>(workspace.getPlan().blocksOn(day));
            for (FixedEvent event : workspace.getEvents()) {
                if (event.getStart().toLocalDate().equals(day)) {
                    items.add(event.toBlock());
                }
            }
            items.sort(Comparator.comparing(TimeBlock::getStart));
            for (TimeBlock item : items) {
                String time = TimeFormat.time(item.getStart()) + "-" + TimeFormat.time(item.getEnd());
                if (item.getTaskId() == null) {
                    lines.add(item.getId() + " " + time + " " + eventTitle(workspace, item.getId()) + " (fixed event)");
                } else {
                    Task task = workspace.getTask(item.getTaskId());
                    lines.add(item.getId() + " " + time + " " + (task == null ? "?" : task.getTitle())
                            + " (task " + item.getTaskId() + ")");
                }
            }
            text.append(DAY.format(day)).append(": ").append(lines.isEmpty() ? "free" : "").append('\n');
            lines.forEach(line -> text.append("  ").append(line).append('\n'));
        }
        return ToolResult.ok(text.toString().trim());
    }

    private static String eventTitle(Workspace workspace, String eventId) {
        return workspace.getEvents().stream()
                .filter(e -> eventId.equals(e.getId()))
                .map(FixedEvent::getTitle)
                .findFirst().orElse("event");
    }
}
