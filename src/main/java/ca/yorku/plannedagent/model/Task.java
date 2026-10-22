package ca.yorku.plannedagent.model;

import java.time.LocalDateTime;
import java.util.Objects;

/** State pattern context: status changes are delegated to the current TaskState. */
public class Task {

    private String id;
    private String title;
    private int minutes;
    private LocalDateTime deadline;
    private Priority priority;
    private boolean outdoor;
    private TaskState state;

    public Task(String title, int minutes, LocalDateTime deadline, Priority priority, boolean outdoor) {
        this.title = title == null ? "" : title.trim();
        this.minutes = minutes;
        this.deadline = deadline;
        this.priority = priority == null ? Priority.MEDIUM : priority;
        this.outdoor = outdoor;
        this.state = new TodoState();
    }

    public void schedule() {
        state.schedule(this);
    }

    public void unschedule() {
        state.unschedule(this);
    }

    public void complete() {
        state.complete(this);
    }

    public void markMissed() {
        state.markMissed(this);
    }

    public boolean needsScheduling() {
        return state.needsScheduling();
    }

    public String getStatus() {
        return state.name();
    }

    public void setState(TaskState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public TaskState getState() {
        return state;
    }

    public Task copy() {
        Task copy = new Task(title, minutes, deadline, priority, outdoor);
        copy.id = id;
        copy.state = state;
        return copy;
    }

    public String getId() {
        return id;
    }

    void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title.trim();
    }

    public int getMinutes() {
        return minutes;
    }

    public void setMinutes(int minutes) {
        this.minutes = minutes;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDateTime deadline) {
        this.deadline = deadline;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority == null ? Priority.MEDIUM : priority;
    }

    public boolean isOutdoor() {
        return outdoor;
    }

    public void setOutdoor(boolean outdoor) {
        this.outdoor = outdoor;
    }

    @Override
    public String toString() {
        return (id == null ? "?" : id) + " " + title + " (" + getStatus() + ")";
    }
}
