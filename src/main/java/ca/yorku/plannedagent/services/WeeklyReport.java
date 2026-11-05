package ca.yorku.plannedagent.services;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The numbers of one week (counted in Java) and a short summary (written by the LLM, or a template). */
public class WeeklyReport {

    public static final String PLANNED_MINUTES = "planned_minutes";
    public static final String DONE_MINUTES = "done_minutes";
    public static final String TASKS_DONE = "tasks_done";
    public static final String TASKS_MISSED = "tasks_missed";
    public static final String BUSIEST_DAY = "busiest_day";

    public final LocalDate weekStart;
    public final Map<String, Integer> stats;
    public final String summary;
    public final boolean writtenByAi;

    public WeeklyReport(LocalDate weekStart, Map<String, Integer> stats, String summary, boolean writtenByAi) {
        this.weekStart = weekStart;
        this.stats = Collections.unmodifiableMap(new LinkedHashMap<>(stats));
        this.summary = summary;
        this.writtenByAi = writtenByAi;
    }
}
