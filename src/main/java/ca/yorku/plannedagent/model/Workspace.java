package ca.yorku.plannedagent.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Everything the student has: tasks, fixed events, the plan and the preferences. */
public class Workspace {

    private final List<Task> tasks = new ArrayList<>();
    private final List<FixedEvent> events = new ArrayList<>();
    private Plan plan = new Plan();
    private final List<Preference> preferences = new ArrayList<>();
    private int lastId;

    public Task getTask(String id) {
        for (Task task : tasks) {
            if (task.getId().equals(id)) {
                return task;
            }
        }
        return null;
    }

    public void addTask(Task task) {
        Objects.requireNonNull(task, "task");
        if (task.getId() == null) {
            task.setId(newId("t"));
        } else if (getTask(task.getId()) != null) {
            throw new IllegalArgumentException("There is already a task " + task.getId());
        }
        tasks.add(task);
    }

    public void removeTask(String id) {
        Task task = getTask(id);
        if (task == null) {
            throw new IllegalArgumentException("No task with id " + id);
        }
        tasks.remove(task);
        for (TimeBlock block : plan.blocksFor(id)) {
            plan.remove(block.getId());
        }
    }

    public List<Task> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    public void addEvent(FixedEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.getId() == null) {
            event.setId(newId("e"));
        }
        events.add(event);
    }

    public List<FixedEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public List<Task> tasksToSchedule() {
        return tasks.stream().filter(Task::needsScheduling).toList();
    }

    /** Planned blocks and fixed events (as blocks without a task) that overlap the given time. */
    public List<TimeBlock> busyBetween(LocalDateTime from, LocalDateTime to) {
        List<TimeBlock> busy = new ArrayList<>();
        for (TimeBlock block : plan.getBlocks()) {
            if (block.overlaps(from, to)) {
                busy.add(block);
            }
        }
        for (FixedEvent event : events) {
            TimeBlock block = event.toBlock();
            if (block.overlaps(from, to)) {
                busy.add(block);
            }
        }
        busy.sort(Comparator.comparing(TimeBlock::getStart));
        return busy;
    }

    public Plan getPlan() {
        return plan;
    }

    /** Stating a rule again replaces the old value. Days off are kept one per day. */
    public void addPreference(Preference preference) {
        Objects.requireNonNull(preference, "preference");
        preferences.removeIf(p -> p.getRule().equals(preference.getRule())
                && (!Preference.DAY_OFF.equals(p.getRule()) || p.getValue().equals(preference.getValue())));
        preferences.add(preference);
    }

    public List<Preference> getPreferences() {
        return Collections.unmodifiableList(preferences);
    }

    public String newId(String prefix) {
        lastId++;
        return prefix + lastId;
    }

    public Workspace copy() {
        Workspace copy = new Workspace();
        for (Task task : tasks) {
            copy.tasks.add(task.copy());
        }
        copy.events.addAll(events);
        copy.plan = plan.copy();
        copy.preferences.addAll(preferences);
        copy.lastId = lastId;
        return copy;
    }
}
