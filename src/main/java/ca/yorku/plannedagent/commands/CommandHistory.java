package ca.yorku.plannedagent.commands;

import ca.yorku.plannedagent.model.Workspace;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Command pattern invoker. Runs a group of commands as one step and keeps undo and redo stacks.
 * A group is all or nothing: if one command fails, the ones before it are rolled back.
 */
public class CommandHistory {

    static final int LIMIT = 20;

    private final Deque<List<PlanCommand>> undoStack = new ArrayDeque<>();
    private final Deque<List<PlanCommand>> redoStack = new ArrayDeque<>();

    public void run(List<PlanCommand> commands, Workspace workspace) {
        List<PlanCommand> group = List.copyOf(commands);
        executeAll(group, workspace);
        undoStack.push(group);
        while (undoStack.size() > LIMIT) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    /**
     * If the plan changed since (e.g. the task was completed), the step cannot be undone any more.
     * It is then left as it is, dropped from the history, and reported, so older steps can still be undone.
     */
    public boolean undo(Workspace workspace) {
        if (undoStack.isEmpty()) {
            return false;
        }
        List<PlanCommand> group = undoStack.pop();
        try {
            undoAll(group, workspace);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("That change can no longer be undone (" + e.getMessage()
                    + "), so it was removed from the history", e);
        }
        redoStack.push(group);
        return true;
    }

    public boolean redo(Workspace workspace) {
        if (redoStack.isEmpty()) {
            return false;
        }
        List<PlanCommand> group = redoStack.pop();
        try {
            executeAll(group, workspace);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("That change can no longer be redone (" + e.getMessage()
                    + "), so it was removed from the history", e);
        }
        undoStack.push(group);
        return true;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    private static void executeAll(List<PlanCommand> group, Workspace workspace) {
        int done = 0;
        try {
            for (PlanCommand command : group) {
                command.execute(workspace);
                done++;
            }
        } catch (RuntimeException e) {
            for (int i = done - 1; i >= 0; i--) {
                try {
                    group.get(i).undo(workspace);
                } catch (RuntimeException rollback) {
                    e.addSuppressed(rollback);
                }
            }
            throw e;
        }
    }

    private static void undoAll(List<PlanCommand> group, Workspace workspace) {
        int undone = 0;
        try {
            for (int i = group.size() - 1; i >= 0; i--) {
                group.get(i).undo(workspace);
                undone++;
            }
        } catch (RuntimeException e) {
            for (int i = group.size() - undone; i < group.size(); i++) {
                try {
                    group.get(i).execute(workspace);
                } catch (RuntimeException rollback) {
                    e.addSuppressed(rollback);
                }
            }
            throw e;
        }
    }
}
