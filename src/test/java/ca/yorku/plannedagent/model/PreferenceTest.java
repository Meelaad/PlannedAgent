package ca.yorku.plannedagent.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PreferenceTest {

    private static final LocalDateTime SUNDAY_21 = LocalDateTime.of(2026, 11, 1, 21, 0);

    @Test
    void normalizesRuleAndValue() {
        Preference p = new Preference(" no_work_after ", "9:30", "no work after 9:30pm");
        assertEquals(Preference.NO_WORK_AFTER, p.getRule());
        assertEquals("09:30", p.getValue());
        assertEquals("SUNDAY", new Preference("DAY_OFF", "sunday", "").getValue());
    }

    @Test
    void rejectsBadValues() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new Preference(Preference.NO_WORK_AFTER, "25:00", ""));
        assertTrue(e.getMessage().contains("not a time"));
        assertThrows(IllegalArgumentException.class, () -> new Preference(Preference.DAY_OFF, "Funday", ""));
        assertThrows(IllegalArgumentException.class, () -> new Preference("NO_MEETINGS", "10:00", ""));
        assertThrows(IllegalArgumentException.class, () -> new Preference(Preference.NO_WORK_BEFORE, " ", ""));
    }

    @Test
    void noWorkAfter() {
        Preference p = new Preference(Preference.NO_WORK_AFTER, "22:00", "");
        assertTrue(p.allows(SUNDAY_21, SUNDAY_21.plusHours(1)));
        assertFalse(p.allows(SUNDAY_21, SUNDAY_21.plusMinutes(90)));
        assertFalse(p.allows(SUNDAY_21.plusHours(2), SUNDAY_21.plusHours(4)));
    }

    @Test
    void noWorkBefore() {
        Preference p = new Preference(Preference.NO_WORK_BEFORE, "09:00", "");
        assertFalse(p.allows(SUNDAY_21.withHour(8), SUNDAY_21.withHour(10)));
        assertTrue(p.allows(SUNDAY_21.withHour(9), SUNDAY_21.withHour(10)));
    }

    @Test
    void dayOff() {
        Preference p = new Preference(Preference.DAY_OFF, "SUNDAY", "Sundays are off");
        assertFalse(p.allows(SUNDAY_21, SUNDAY_21.plusHours(1)));
        assertTrue(p.allows(SUNDAY_21.plusDays(1), SUNDAY_21.plusDays(1).plusHours(1)));
        assertTrue(p.allows(SUNDAY_21.minusDays(1), SUNDAY_21.minusDays(1).plusHours(3)));
        assertEquals("Sunday off", p.describe());
    }
}
