package ca.yorku.plannedagent.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TimeBlockTest {

    private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 21, 9, 0);

    @Test
    void endMustBeAfterStart() {
        assertThrows(IllegalArgumentException.class, () -> new TimeBlock("b1", "t1", NINE, NINE));
        assertThrows(IllegalArgumentException.class, () -> new TimeBlock("b1", "t1", NINE, NINE.minusMinutes(5)));
    }

    @Test
    void moveKeepsTheLength() {
        TimeBlock block = new TimeBlock("b1", "t1", NINE, NINE.plusMinutes(90));
        block.moveTo(NINE.plusDays(1).withHour(14));
        assertEquals(LocalDateTime.of(2026, 10, 22, 14, 0), block.getStart());
        assertEquals(LocalDateTime.of(2026, 10, 22, 15, 30), block.getEnd());
        assertEquals(90, block.getMinutes());
    }

    @Test
    void overlapIsExclusiveAtTheEdges() {
        TimeBlock block = new TimeBlock("b1", "t1", NINE, NINE.plusHours(1));
        assertTrue(block.overlaps(NINE.plusMinutes(30), NINE.plusHours(2)));
        assertTrue(block.overlaps(NINE.minusHours(1), NINE.plusMinutes(1)));
        assertTrue(block.overlaps(NINE.plusMinutes(10), NINE.plusMinutes(20)));
        assertFalse(block.overlaps(NINE.plusHours(1), NINE.plusHours(2)));
        assertFalse(block.overlaps(NINE.minusHours(1), NINE));
    }

    @Test
    void copyIsIndependent() {
        TimeBlock block = new TimeBlock("b1", "t1", NINE, NINE.plusHours(1));
        TimeBlock copy = block.copy();
        copy.moveTo(NINE.plusHours(3));
        assertEquals(NINE, block.getStart());
        assertEquals("b1", copy.getId());
        assertEquals("t1", copy.getTaskId());
    }

    @Test
    void eventBecomesABlockWithoutTask() {
        FixedEvent lecture = new FixedEvent("EECS 3311 lecture", NINE, NINE.plusMinutes(80));
        TimeBlock block = lecture.toBlock();
        assertNull(block.getTaskId());
        assertEquals(80, block.getMinutes());
    }

    @Test
    void eventNeedsTitleAndValidTimes() {
        assertThrows(IllegalArgumentException.class, () -> new FixedEvent(" ", NINE, NINE.plusHours(1)));
        assertThrows(IllegalArgumentException.class, () -> new FixedEvent("Shift", NINE, NINE.minusHours(1)));
    }
}
