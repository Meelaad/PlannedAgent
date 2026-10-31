package ca.yorku.plannedagent.app;

/** Observer pattern: told when the plan changes or the agent does something. */
public interface PlannerObserver {

    void update(String type, String message);
}
