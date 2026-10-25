package ca.yorku.plannedagent.commands;

import ca.yorku.plannedagent.model.TimeBlock;
import ca.yorku.plannedagent.model.TimeFormat;
import ca.yorku.plannedagent.model.Workspace;

import java.time.LocalDateTime;
import java.util.Objects;

/** Moves an existing block to a new start time, keeping its length. */
public class MoveBlockCommand implements PlanCommand {

    private final String blockId;
    private final LocalDateTime newStart;
    private final String title;
    private LocalDateTime oldStart;

    public MoveBlockCommand(String blockId, LocalDateTime newStart, String title) {
        this.blockId = Objects.requireNonNull(blockId, "blockId");
        this.newStart = Objects.requireNonNull(newStart, "newStart");
        this.title = title;
    }

    @Override
    public void execute(Workspace workspace) {
        TimeBlock block = find(workspace);
        oldStart = block.getStart();
        block.moveTo(newStart);
    }

    @Override
    public void undo(Workspace workspace) {
        find(workspace).moveTo(oldStart);
    }

    @Override
    public String describe() {
        return "Move '" + title + "' to " + TimeFormat.dayTime(newStart);
    }

    public String getBlockId() {
        return blockId;
    }

    public LocalDateTime getNewStart() {
        return newStart;
    }

    private TimeBlock find(Workspace workspace) {
        TimeBlock block = workspace.getPlan().find(blockId);
        if (block == null) {
            throw new IllegalStateException("Block " + blockId + " no longer exists");
        }
        return block;
    }
}
