package ca.yorku.plannedagent.model;

public class ScheduledState implements TaskState {

    @Override
    public void schedule(Task task) {
        throw new IllegalStateException("'" + task.getTitle() + "' is already scheduled");
    }

    @Override
    public void unschedule(Task task) {
        task.setState(new TodoState());
    }

    @Override
    public void complete(Task task) {
        task.setState(new DoneState());
    }

    @Override
    public void markMissed(Task task) {
        task.setState(new MissedState());
    }

    @Override
    public boolean needsScheduling() {
        return false;
    }

    @Override
    public String name() {
        return "SCHEDULED";
    }
}
