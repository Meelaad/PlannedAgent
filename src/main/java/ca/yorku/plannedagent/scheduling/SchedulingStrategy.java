package ca.yorku.plannedagent.scheduling;

import ca.yorku.plannedagent.model.Task;

import java.util.List;

/** Strategy pattern: decides which task gets a free slot first. */
public interface SchedulingStrategy {

    String name();

    /** Returns a new list in the order the tasks should be placed. */
    List<Task> sort(List<Task> tasks);
}
