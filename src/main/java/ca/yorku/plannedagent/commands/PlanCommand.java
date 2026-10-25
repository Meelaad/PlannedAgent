package ca.yorku.plannedagent.commands;

import ca.yorku.plannedagent.model.Workspace;

/** Command pattern: one reversible change to the plan. The Workspace is the receiver. */
public interface PlanCommand {

    void execute(Workspace workspace);

    void undo(Workspace workspace);

    String describe();
}
