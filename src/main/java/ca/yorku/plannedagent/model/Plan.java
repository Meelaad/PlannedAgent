package ca.yorku.plannedagent.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** The planned time blocks. Owned by the Workspace. */
public class Plan {

    private final List<TimeBlock> blocks = new ArrayList<>();

    public void add(TimeBlock block) {
        Objects.requireNonNull(block, "block");
        if (find(block.getId()) != null) {
            throw new IllegalArgumentException("The plan already has a block " + block.getId());
        }
        blocks.add(block);
    }

    public TimeBlock remove(String blockId) {
        TimeBlock block = find(blockId);
        if (block != null) {
            blocks.remove(block);
        }
        return block;
    }

    public TimeBlock find(String blockId) {
        for (TimeBlock block : blocks) {
            if (block.getId().equals(blockId)) {
                return block;
            }
        }
        return null;
    }

    public List<TimeBlock> blocksOn(LocalDate day) {
        return blocks.stream()
                .filter(b -> b.getStart().toLocalDate().equals(day))
                .sorted(Comparator.comparing(TimeBlock::getStart))
                .toList();
    }

    public List<TimeBlock> blocksFor(String taskId) {
        return blocks.stream()
                .filter(b -> Objects.equals(b.getTaskId(), taskId))
                .sorted(Comparator.comparing(TimeBlock::getStart))
                .toList();
    }

    public List<TimeBlock> getBlocks() {
        return blocks.stream().sorted(Comparator.comparing(TimeBlock::getStart)).toList();
    }

    public Plan copy() {
        Plan copy = new Plan();
        for (TimeBlock block : blocks) {
            copy.blocks.add(block.copy());
        }
        return copy;
    }
}
