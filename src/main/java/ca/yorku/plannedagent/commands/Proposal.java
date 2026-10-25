package ca.yorku.plannedagent.commands;

import ca.yorku.plannedagent.model.Conflict;

import java.util.List;

/** Plan changes waiting for the student to click Apply or Discard. */
public class Proposal {

    private final String summary;
    private final List<PlanCommand> commands;
    private final List<Conflict> conflicts;

    public Proposal(String summary, List<PlanCommand> commands, List<Conflict> conflicts) {
        this.summary = summary == null ? "" : summary;
        this.commands = List.copyOf(commands);
        this.conflicts = List.copyOf(conflicts);
    }

    public String getSummary() {
        return summary;
    }

    public List<PlanCommand> getCommands() {
        return commands;
    }

    public List<Conflict> getConflicts() {
        return conflicts;
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }
}
