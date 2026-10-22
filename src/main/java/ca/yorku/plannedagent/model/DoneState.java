package ca.yorku.plannedagent.model;

/** A finished task: every change is an error. */
public class DoneState implements TaskState {

    @Override
    public void schedule(Task task) {
        throw alreadyDone(task);
    }

    @Override
    public void unschedule(Task task) {
        throw alreadyDone(task);
    }

    @Override
    public void complete(Task task) {
        throw alreadyDone(task);
    }

    @Override
    public void markMissed(Task task) {
        throw alreadyDone(task);
    }

    @Override
    public boolean needsScheduling() {
        return false;
    }

    @Override
    public String name() {
        return "DONE";
    }

    private static IllegalStateException alreadyDone(Task task) {
        return new IllegalStateException("'" + task.getTitle() + "' is already done");
    }
}
