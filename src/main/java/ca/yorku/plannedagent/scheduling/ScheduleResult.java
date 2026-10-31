package ca.yorku.plannedagent.scheduling;

import ca.yorku.plannedagent.commands.AddBlockCommand;
import ca.yorku.plannedagent.commands.PlanCommand;
import ca.yorku.plannedagent.commands.Proposal;
import ca.yorku.plannedagent.model.Conflict;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeBlock;
import ca.yorku.plannedagent.model.Workspace;
import ca.yorku.plannedagent.services.Forecast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScheduleResult {

    private final List<TimeBlock> placed;
    private final Map<String, String> notPlaced;

    public ScheduleResult(List<TimeBlock> placed, Map<String, String> notPlaced) {
        this.placed = List.copyOf(placed);
        this.notPlaced = Collections.unmodifiableMap(new LinkedHashMap<>(notPlaced));
    }

    public List<TimeBlock> getPlaced() {
        return placed;
    }

    /** Task id to the reason it could not be placed. */
    public Map<String, String> getNotPlaced() {
        return notPlaced;
    }

    /**
     * Turns the placed blocks into AddBlockCommands, tries them on a copy for new conflicts,
     * and writes a summary. The workspace itself is not changed.
     */
    public Proposal toProposal(String heading, Workspace workspace, ConflictChecker checker, Forecast forecast,
                               List<String> warnings) {
        List<PlanCommand> commands = new ArrayList<>();
        for (TimeBlock block : placed) {
            Task task = workspace.getTask(block.getTaskId());
            commands.add(new AddBlockCommand(block, task == null ? block.getTaskId() : task.getTitle()));
        }
        List<Conflict> conflicts = checker.checkChanges(workspace, commands, forecast);

        StringBuilder summary = new StringBuilder(heading).append(": ")
                .append(placed.size()).append(placed.size() == 1 ? " new block." : " new blocks.");
        for (PlanCommand command : commands) {
            summary.append("\n- ").append(command.describe());
        }
        if (!notPlaced.isEmpty()) {
            summary.append("\nCould not place:");
            notPlaced.forEach((taskId, reason) -> {
                Task task = workspace.getTask(taskId);
                summary.append("\n- '").append(task == null ? taskId : task.getTitle()).append("': ").append(reason);
            });
        }
        for (String warning : warnings) {
            summary.append("\nWarning: ").append(warning);
        }
        if (!conflicts.isEmpty()) {
            summary.append("\nNew conflicts:");
            conflicts.forEach(c -> summary.append("\n- ").append(c.getMessage()));
        }
        return new Proposal(summary.toString(), commands, conflicts);
    }
}
