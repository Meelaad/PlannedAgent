package ca.yorku.plannedagent.model;

/**
 * State pattern: one class per task status. Each state decides which calls are allowed
 * and moves the task to its next state. A call that is not allowed throws IllegalStateException.
 */
public interface TaskState {

    void schedule(Task task);

    void unschedule(Task task);

    void complete(Task task);

    void markMissed(Task task);

    boolean needsScheduling();

    String name();
}
