package ca.yorku.plannedagent.services;

import ca.yorku.plannedagent.ai.LlmClient;
import ca.yorku.plannedagent.ai.LlmException;
import ca.yorku.plannedagent.ai.LlmRequest;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeBlock;
import ca.yorku.plannedagent.model.Workspace;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Weekly report: the counting is plain Java; the LLM only turns the numbers into a few sentences. */
public class ReportService {

    private final LlmClient llm;

    public ReportService(LlmClient llm) {
        this.llm = Objects.requireNonNull(llm, "llm");
    }

    public WeeklyReport weeklyReport(Workspace workspace, LocalDate weekStart) {
        Map<String, Integer> stats = countStats(workspace, weekStart);
        boolean empty = stats.get(WeeklyReport.PLANNED_MINUTES) == 0 && stats.get(WeeklyReport.TASKS_DONE) == 0
                && stats.get(WeeklyReport.TASKS_MISSED) == 0;
        if (empty) {
            return new WeeklyReport(weekStart, stats, "No tasks were planned this week.", false);
        }
        try {
            String text = llm.complete(new LlmRequest(SYSTEM, List.of("user: " + numbers(weekStart, stats)), false));
            if (text != null && !text.isBlank()) {
                return new WeeklyReport(weekStart, stats, text.strip(), true);
            }
        } catch (LlmException e) {
            // the template below is the agreed fallback
        }
        return new WeeklyReport(weekStart, stats, fallbackSummary(stats), false);
    }

    private Map<String, Integer> countStats(Workspace workspace, LocalDate weekStart) {
        LocalDateTime from = weekStart.atStartOfDay();
        LocalDateTime to = from.plusDays(7);
        int planned = 0;
        int[] perDay = new int[8];
        for (TimeBlock block : workspace.getPlan().getBlocks()) {
            if (!block.getStart().isBefore(from) && block.getStart().isBefore(to)) {
                planned += block.getMinutes();
                perDay[block.getStart().getDayOfWeek().getValue()] += block.getMinutes();
            }
        }
        int doneMinutes = 0;
        int done = 0;
        int missed = 0;
        for (Task task : workspace.getTasks()) {
            if (within(task.getDoneAt(), from, to)) {
                done++;
                doneMinutes += task.getMinutes();
            }
            if (within(task.getMissedAt(), from, to)) {
                missed++;
            }
        }
        int busiest = 0;
        for (int day = 1; day <= 7; day++) {
            if (perDay[day] > 0 && (busiest == 0 || perDay[day] > perDay[busiest])) {
                busiest = day;
            }
        }
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put(WeeklyReport.PLANNED_MINUTES, planned);
        stats.put(WeeklyReport.DONE_MINUTES, doneMinutes);
        stats.put(WeeklyReport.TASKS_DONE, done);
        stats.put(WeeklyReport.TASKS_MISSED, missed);
        stats.put(WeeklyReport.BUSIEST_DAY, busiest);
        return stats;
    }

    private String fallbackSummary(Map<String, Integer> stats) {
        int done = stats.get(WeeklyReport.TASKS_DONE);
        int missed = stats.get(WeeklyReport.TASKS_MISSED);
        StringBuilder text = new StringBuilder()
                .append("You planned ").append(hours(stats.get(WeeklyReport.PLANNED_MINUTES)))
                .append(" and finished ").append(done).append(done == 1 ? " task" : " tasks")
                .append(" (").append(hours(stats.get(WeeklyReport.DONE_MINUTES))).append(").");
        if (stats.get(WeeklyReport.BUSIEST_DAY) > 0) {
            text.append(" Your busiest day was ").append(dayName(stats.get(WeeklyReport.BUSIEST_DAY))).append('.');
        }
        if (missed > 0) {
            text.append(" ").append(missed).append(missed == 1 ? " task was" : " tasks were")
                    .append(" missed; try shorter blocks or an earlier start for those.");
        } else {
            text.append(" Nothing was missed. Nice work!");
        }
        return text.toString();
    }

    public static String dayName(int day) {
        return day < 1 || day > 7 ? "none" : DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private static String numbers(LocalDate weekStart, Map<String, Integer> stats) {
        return "Week starting " + weekStart + ". Minutes planned: " + stats.get(WeeklyReport.PLANNED_MINUTES)
                + ". Minutes of finished tasks: " + stats.get(WeeklyReport.DONE_MINUTES)
                + ". Tasks done: " + stats.get(WeeklyReport.TASKS_DONE)
                + ". Tasks missed: " + stats.get(WeeklyReport.TASKS_MISSED)
                + ". Busiest day: " + dayName(stats.get(WeeklyReport.BUSIEST_DAY)) + ".";
    }

    private static boolean within(LocalDateTime time, LocalDateTime from, LocalDateTime to) {
        return time != null && !time.isBefore(from) && time.isBefore(to);
    }

    private static String hours(int minutes) {
        return minutes % 60 == 0 ? minutes / 60 + " h" : String.format(Locale.ROOT, "%.1f h", minutes / 60.0);
    }

    private static final String SYSTEM = """
            You write a short weekly summary for a university student who uses a planning app.
            Use ONLY the numbers you are given; do not invent tasks, dates or numbers.
            Write 3 or 4 plain sentences: what went well, what slipped, and one or two practical tips
            for next week. No lists, no headings, no emojis.""";
}
