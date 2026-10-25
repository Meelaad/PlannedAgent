package ca.yorku.plannedagent.commands;

import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TaskState;
import ca.yorku.plannedagent.model.TimeBlock;
import ca.yorku.plannedagent.model.TimeFormat;
import ca.yorku.plannedagent.model.Workspace;

import java.util.Objects;

/** Adds a block to the plan and moves its task to Scheduled. */
public class AddBlockCommand implements PlanCommand {

    private final TimeBlock block;
    private final String title;
    private TaskState previousState;

    public AddBlockCommand(TimeBlock block, String title) {
        this.block = Objects.requireNonNull(block, "block");
        this.title = title;
    }

    @Override
    public void execute(Workspace workspace) {
        Task task = workspace.getTask(block.getTaskId());
        if (task == null) {
            throw new IllegalStateException("Task " + block.getTaskId() + " no longer exists");
        }
        previousState = task.getState();
        task.schedule();
        workspace.getPlan().add(block.copy());
    }

    @Override
    public void undo(Workspace workspace) {
        Task task = workspace.getTask(block.getTaskId());
        if (task != null) {
            task.unschedule();
            task.setState(previousState);
        }
        workspace.getPlan().remove(block.getId());
    }

    @Override
    public String describe() {
        return "Add '" + title + "' " + TimeFormat.range(block.getStart(), block.getEnd());
    }

    public TimeBlock getBlock() {
        return block;
    }
}
