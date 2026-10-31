package ca.yorku.plannedagent.agent;

import ca.yorku.plannedagent.model.Workspace;

import java.util.Map;

/**
 * An action the agent can take. Every tool checks its own arguments before doing anything,
 * and tools that would change the plan return a Proposal instead of changing it.
 */
public interface Tool {

    String name();

    /** Shown to the LLM: what the tool does and which arguments it takes. */
    String description();

    ToolResult run(Map<String, String> args, Workspace workspace);
}
