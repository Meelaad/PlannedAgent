package ca.yorku.plannedagent.agent.tools;

import ca.yorku.plannedagent.agent.Tool;
import ca.yorku.plannedagent.agent.ToolResult;
import ca.yorku.plannedagent.model.Preference;
import ca.yorku.plannedagent.model.Workspace;

import java.util.Map;

/** remember_preference: checks a rule from the student and saves it as long-term memory. */
public class RememberPreferenceTool implements Tool {

    @Override
    public String name() {
        return "remember_preference";
    }

    @Override
    public String description() {
        return "saves a lasting rule the student states. Args: rule (NO_WORK_AFTER, NO_WORK_BEFORE or DAY_OFF), "
                + "value (HH:MM for the first two, a day like SUNDAY for DAY_OFF), text (the student's words).";
    }

    @Override
    public ToolResult run(Map<String, String> args, Workspace workspace) {
        Preference preference;
        try {
            preference = new Preference(args.get("rule"), args.get("value"), args.get("text"));
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        }
        workspace.addPreference(preference);
        return ToolResult.ok("Saved the rule: " + preference.describe()
                + ". Auto-schedule and the conflict check will follow it from now on.");
    }
}
