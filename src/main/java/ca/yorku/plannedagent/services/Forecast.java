package ca.yorku.plannedagent.services;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;

/** Hourly chance of rain. Hours we know nothing about are treated as dry. */
public class Forecast {

    public static final int RAIN_LIMIT = 40;

    private final Map<LocalDateTime, Integer> rainChance;

    public Forecast(Map<LocalDateTime, Integer> rainChance) {
        this.rainChance = new TreeMap<>(rainChance);
    }

    public static Forecast none() {
        return new Forecast(Map.of());
    }

    public boolean isRainy(LocalDateTime start, LocalDateTime end) {
        return maxChance(start, end) >= RAIN_LIMIT;
    }

    /** The highest chance of rain in the hours from start to end, or -1 if unknown. */
    public int maxChance(LocalDateTime start, LocalDateTime end) {
        int max = -1;
        for (LocalDateTime hour = start.truncatedTo(ChronoUnit.HOURS); hour.isBefore(end); hour = hour.plusHours(1)) {
            Integer chance = rainChance.get(hour);
            if (chance != null && chance > max) {
                max = chance;
            }
        }
        return max;
    }

    public boolean isEmpty() {
        return rainChance.isEmpty();
    }
}
