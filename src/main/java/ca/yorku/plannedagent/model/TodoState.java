package ca.yorku.plannedagent.model;

public class TodoState implements TaskState {

    @Override
    public void schedule(Task task) {
        task.setState(new ScheduledState());
    }

    @Override
    public void unschedule(Task task) {
        throw new IllegalStateException("'" + task.getTitle() + "' is not scheduled");
    }

    @Override
    public void complete(Task task) {
        task.setState(new DoneState());
    }

    @Override
    public void markMissed(Task task) {
        throw new IllegalStateException("'" + task.getTitle() + "' was never scheduled, so it cannot be missed");
    }

    @Override
    public boolean needsScheduling() {
        return true;
    }

    @Override
    public String name() {
        return "TODO";
    }
}
