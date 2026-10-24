package ca.yorku.plannedagent.scheduling;

import ca.yorku.plannedagent.model.Task;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shortest task first, to finish as many tasks as possible. Ties go to the earlier deadline. */
public class ShortestFirstStrategy implements SchedulingStrategy {

    @Override
    public String name() {
        return "shortest";
    }

    @Override
    public List<Task> sort(List<Task> tasks) {
        List<Task> sorted = new ArrayList<>(tasks);
        sorted.sort(Comparator.comparingInt(Task::getMinutes)
                .thenComparing(Task::getDeadline, Comparator.nullsLast(Comparator.naturalOrder())));
        return sorted;
    }
}
