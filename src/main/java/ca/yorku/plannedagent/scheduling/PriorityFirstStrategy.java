package ca.yorku.plannedagent.scheduling;

import ca.yorku.plannedagent.model.Task;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Highest priority first. Ties go to the earlier deadline. */
public class PriorityFirstStrategy implements SchedulingStrategy {

    @Override
    public String name() {
        return "priority";
    }

    @Override
    public List<Task> sort(List<Task> tasks) {
        List<Task> sorted = new ArrayList<>(tasks);
        sorted.sort(Comparator.comparing(Task::getPriority, Comparator.reverseOrder())
                .thenComparing(Task::getDeadline, Comparator.nullsLast(Comparator.naturalOrder())));
        return sorted;
    }
}
