package ca.yorku.plannedagent.app;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Observer pattern subject. The facade and the agent publish here without knowing who listens. */
public class EventBus {

    public static final String TASKS = "TASKS";
    public static final String EVENTS = "EVENTS";
    public static final String PLAN = "PLAN";
    public static final String PROPOSAL = "PROPOSAL";
    public static final String AGENT = "AGENT";
    public static final String PREFERENCES = "PREFERENCES";
    public static final String WARNING = "WARNING";

    private final List<PlannerObserver> observers = new CopyOnWriteArrayList<>();

    public void attach(PlannerObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void detach(PlannerObserver observer) {
        observers.remove(observer);
    }

    /** One broken observer must not stop the others from being told. */
    public void notifyObservers(String type, String message) {
        for (PlannerObserver observer : observers) {
            try {
                observer.update(type, message);
            } catch (RuntimeException e) {
                System.err.println("An observer failed on " + type + ": " + e);
            }
        }
    }
}
