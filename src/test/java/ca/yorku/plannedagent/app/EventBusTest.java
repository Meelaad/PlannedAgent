package ca.yorku.plannedagent.app;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    @Test
    void everyObserverIsToldOnce() {
        EventBus bus = new EventBus();
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        PlannerObserver first = (type, message) -> a.add(type + ":" + message);
        bus.attach(first);
        bus.attach(first);
        bus.attach((type, message) -> b.add(message));

        bus.notifyObservers(EventBus.PLAN, "3 blocks added");

        assertEquals(List.of("PLAN:3 blocks added"), a);
        assertEquals(List.of("3 blocks added"), b);
    }

    @Test
    void detachedObserverHearsNothing() {
        EventBus bus = new EventBus();
        List<String> heard = new ArrayList<>();
        PlannerObserver observer = (type, message) -> heard.add(message);
        bus.attach(observer);
        bus.detach(observer);
        bus.notifyObservers(EventBus.TASKS, "x");
        assertTrue(heard.isEmpty());
    }

    @Test
    void brokenObserverDoesNotStopTheOthers() {
        EventBus bus = new EventBus();
        List<String> heard = new ArrayList<>();
        bus.attach((type, message) -> {
            throw new IllegalStateException("boom");
        });
        bus.attach((type, message) -> heard.add(message));
        bus.notifyObservers(EventBus.AGENT, "using get_tasks");
        assertEquals(List.of("using get_tasks"), heard);
    }
}
