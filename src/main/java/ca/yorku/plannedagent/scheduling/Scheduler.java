package ca.yorku.plannedagent.scheduling;

import ca.yorku.plannedagent.model.Preference;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeBlock;
import ca.yorku.plannedagent.model.Workspace;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Strategy pattern context. Places every task that needs a slot into free time,
 * in the order given by the current strategy. Start times are tried every 30 minutes.
 */
public class Scheduler {

    static final int STEP_MINUTES = 30;
    static final LocalTime DAY_START = LocalTime.of(8, 0);
    static final LocalTime DAY_END = LocalTime.of(22, 0);

    private SchedulingStrategy strategy = new DeadlineFirstStrategy();

    public void setStrategy(SchedulingStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
    }

    public SchedulingStrategy getStrategy() {
        return strategy;
    }

    /** Does not change the workspace plan: the caller decides what to do with the result. */
    public ScheduleResult schedule(Workspace workspace, LocalDateTime from, LocalDateTime to) {
        Workspace work = workspace.copy();
        LocalDateTime first = roundUp(from);
        List<TimeBlock> placed = new ArrayList<>();
        Map<String, String> notPlaced = new LinkedHashMap<>();

        for (Task task : strategy.sort(work.tasksToSchedule())) {
            if (task.getMinutes() <= 0) {
                notPlaced.put(task.getId(), "it has no length");
                continue;
            }
            if (task.getDeadline() != null && !task.getDeadline().isAfter(first)) {
                notPlaced.put(task.getId(), "its deadline has already passed");
                continue;
            }
            LocalDateTime start = findStart(task, first, to, work);
            if (start == null) {
                boolean deadlineInWindow = task.getDeadline() != null && task.getDeadline().isBefore(to);
                notPlaced.put(task.getId(), deadlineInWindow
                        ? "no free time before its deadline"
                        : "no free time in the planning window");
                continue;
            }
            TimeBlock block = new TimeBlock(workspace.newId("b"), task.getId(), start,
                    start.plusMinutes(task.getMinutes()));
            work.getPlan().add(block.copy());
            placed.add(block);
        }
        return new ScheduleResult(placed, notPlaced);
    }

    private LocalDateTime findStart(Task task, LocalDateTime first, LocalDateTime to, Workspace work) {
        for (LocalDateTime start = first;
             !start.plusMinutes(task.getMinutes()).isAfter(to);
             start = start.plusMinutes(STEP_MINUTES)) {
            if (fits(task, start, work)) {
                return start;
            }
        }
        return null;
    }

    private boolean fits(Task task, LocalDateTime start, Workspace workspace) {
        LocalDateTime end = start.plusMinutes(task.getMinutes());
        if (start.toLocalTime().isBefore(DAY_START)
                || end.toLocalDate().isAfter(start.toLocalDate())
                || end.toLocalTime().isAfter(DAY_END)) {
            return false;
        }
        if (task.getDeadline() != null && end.isAfter(task.getDeadline())) {
            return false;
        }
        for (Preference preference : workspace.getPreferences()) {
            if (!preference.allows(start, end)) {
                return false;
            }
        }
        return workspace.busyBetween(start, end).isEmpty();
    }

    static LocalDateTime roundUp(LocalDateTime time) {
        LocalDateTime minute = time.truncatedTo(ChronoUnit.MINUTES);
        if (minute.isBefore(time)) {
            minute = minute.plusMinutes(1);
        }
        int extra = minute.getMinute() % STEP_MINUTES;
        return extra == 0 ? minute : minute.plusMinutes(STEP_MINUTES - extra);
    }
}
