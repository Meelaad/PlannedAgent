package ca.yorku.plannedagent.scheduling;

import ca.yorku.plannedagent.model.Task;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Earliest deadline first; tasks without a deadline go last. Ties go to the higher priority. */
public class DeadlineFirstStrategy implements SchedulingStrategy {

    @Override
    public String name() {
        return "deadline";
    }

    @Override
    public List<Task> sort(List<Task> tasks) {
        List<Task> sorted = new ArrayList<>(tasks);
        sorted.sort(Comparator.comparing(Task::getDeadline, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Task::getPriority, Comparator.reverseOrder()));
        return sorted;
    }
}
