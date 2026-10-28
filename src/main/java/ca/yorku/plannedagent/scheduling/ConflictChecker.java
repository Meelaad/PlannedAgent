package ca.yorku.plannedagent.scheduling;

import ca.yorku.plannedagent.commands.PlanCommand;
import ca.yorku.plannedagent.model.Conflict;
import ca.yorku.plannedagent.model.FixedEvent;
import ca.yorku.plannedagent.model.Preference;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeBlock;
import ca.yorku.plannedagent.model.TimeFormat;
import ca.yorku.plannedagent.model.Workspace;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Finds overlaps, blocks that end after their deadline, and blocks that break a preference. */
public class ConflictChecker {

    public List<Conflict> check(Workspace workspace) {
        List<Conflict> conflicts = new ArrayList<>();
        findOverlaps(workspace, conflicts);
        for (TimeBlock block : workspace.getPlan().getBlocks()) {
            Task task = workspace.getTask(block.getTaskId());
            String title = task == null ? block.getId() : task.getTitle();
            if (task != null && task.getDeadline() != null && block.getEnd().isAfter(task.getDeadline())) {
                conflicts.add(new Conflict(Conflict.LATE,
                        "'" + title + "' ends " + TimeFormat.dayTime(block.getEnd())
                                + ", after its deadline " + TimeFormat.dayTime(task.getDeadline()),
                        List.of(block.getId())));
            }
            for (Preference preference : workspace.getPreferences()) {
                if (!preference.allows(block.getStart(), block.getEnd())) {
                    conflicts.add(new Conflict(Conflict.PREFERENCE,
                            "'" + title + "' at " + TimeFormat.range(block.getStart(), block.getEnd())
                                    + " breaks your rule: " + preference.describe(),
                            List.of(block.getId())));
                }
            }
        }
        return conflicts;
    }

    /** The conflicts the commands would add, found by trying them on a copy. Existing conflicts are left out. */
    public List<Conflict> checkChanges(Workspace workspace, List<PlanCommand> commands) {
        Workspace copy = workspace.copy();
        List<Conflict> before = check(copy);
        for (PlanCommand command : commands) {
            command.execute(copy);
        }
        List<Conflict> after = check(copy);
        after.removeAll(before);
        return after;
    }

    private void findOverlaps(Workspace workspace, List<Conflict> conflicts) {
        List<Item> items = new ArrayList<>();
        for (TimeBlock block : workspace.getPlan().getBlocks()) {
            Task task = workspace.getTask(block.getTaskId());
            items.add(new Item(block.getId(), task == null ? block.getId() : task.getTitle(),
                    block.getStart(), block.getEnd()));
        }
        for (FixedEvent event : workspace.getEvents()) {
            items.add(new Item(event.getId(), event.getTitle(), event.getStart(), event.getEnd()));
        }
        items.sort(Comparator.comparing(Item::start).thenComparing(Item::id));

        for (int i = 0; i < items.size(); i++) {
            Item a = items.get(i);
            for (int j = i + 1; j < items.size() && items.get(j).start().isBefore(a.end()); j++) {
                Item b = items.get(j);
                conflicts.add(new Conflict(Conflict.OVERLAP,
                        "'" + a.title() + "' (" + TimeFormat.range(a.start(), a.end()) + ") overlaps '"
                                + b.title() + "' (" + TimeFormat.range(b.start(), b.end()) + ")",
                        List.of(a.id(), b.id())));
            }
        }
    }

    private record Item(String id, String title, LocalDateTime start, LocalDateTime end) {
    }
}
