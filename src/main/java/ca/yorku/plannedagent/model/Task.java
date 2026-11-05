package ca.yorku.plannedagent.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** State pattern context: status changes are delegated to the current TaskState. */
public class Task {

    public static final int MIN_MINUTES = 5;
    public static final int MAX_MINUTES = 600;

    private String id;
    private String title;
    private int minutes;
    private LocalDateTime deadline;
    private Priority priority;
    private boolean outdoor;
    private TaskState state;
    private LocalDateTime doneAt;
    private LocalDateTime missedAt;

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

    /** What is wrong with this task, if anything. Empty means it can be saved. */
    public List<String> problems(LocalDateTime now) {
        List<String> problems = new ArrayList<>();
        if (title.isBlank()) {
            problems.add("The title is empty");
        }
        if (minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
            problems.add("The length must be " + MIN_MINUTES + " to " + MAX_MINUTES + " minutes");
        }
        if (deadline != null && deadline.isBefore(now)) {
            problems.add("The deadline is in the past");
        }
        return problems;
    }

    public Task copy() {
        Task copy = new Task(title, minutes, deadline, priority, outdoor);
        copy.id = id;
        copy.state = state;
        copy.doneAt = doneAt;
        copy.missedAt = missedAt;
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

    /** When the task was completed, for the weekly report. */
    public LocalDateTime getDoneAt() {
        return doneAt;
    }

    public void setDoneAt(LocalDateTime doneAt) {
        this.doneAt = doneAt;
    }

    /** When the task was last marked as missed, for the weekly report. */
    public LocalDateTime getMissedAt() {
        return missedAt;
    }

    public void setMissedAt(LocalDateTime missedAt) {
        this.missedAt = missedAt;
    }

    @Override
    public String toString() {
        return (id == null ? "?" : id) + " " + title + " (" + getStatus() + ")";
    }
}
