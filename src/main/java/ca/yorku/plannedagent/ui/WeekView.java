package ca.yorku.plannedagent.ui;

import ca.yorku.plannedagent.model.FixedEvent;
import ca.yorku.plannedagent.model.Plan;
import ca.yorku.plannedagent.model.Priority;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeBlock;
import ca.yorku.plannedagent.model.TimeFormat;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.ToolTipManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Draws the next seven days with fixed events and planned blocks. Only draws; it holds no planning logic. */
class WeekView extends JPanel implements Scrollable {

    private static final int FIRST_HOUR = 7;
    private static final int LAST_HOUR = 23;
    private static final int HOUR_HEIGHT = 40;
    private static final int HEADER = 28;
    private static final int GUTTER = 44;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE MMM d", Locale.ENGLISH);

    private static final Color EVENT = new Color(0x9AA3AD);
    private static final Color HIGH = new Color(0xE07A5F);
    private static final Color MEDIUM = new Color(0x4A7FC1);
    private static final Color LOW = new Color(0x5B9E6F);
    private static final Color DONE = new Color(0xC9CED3);
    private static final Color CONFLICT = new Color(0xD62828);

    private final Clock clock;
    private final List<Item> items = new ArrayList<>();
    private Set<String> highlighted = new HashSet<>();

    WeekView(Clock clock) {
        this.clock = clock;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(7 * 70 + GUTTER, HEADER + (LAST_HOUR - FIRST_HOUR) * HOUR_HEIGHT + 1));
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    void show(Plan plan, List<FixedEvent> events, List<Task> tasks) {
        Map<String, Task> byId = new HashMap<>();
        tasks.forEach(t -> byId.put(t.getId(), t));
        items.clear();
        for (FixedEvent event : events) {
            items.add(new Item(event.getId(), event.getTitle(), event.getStart(), event.getEnd(), EVENT, false));
        }
        for (TimeBlock block : plan.getBlocks()) {
            Task task = byId.get(block.getTaskId());
            String title = task == null ? block.getId() : task.getTitle();
            boolean done = task != null && "DONE".equals(task.getStatus());
            Color color = task == null ? MEDIUM : colorFor(task.getPriority());
            items.add(new Item(block.getId(), title, block.getStart(), block.getEnd(), done ? DONE : color, done));
        }
        repaint();
    }

    void highlight(Set<String> ids) {
        highlighted = new HashSet<>(ids);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        LocalDate today = LocalDate.now(clock);
        int dayWidth = dayWidth();

        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        for (int hour = FIRST_HOUR; hour <= LAST_HOUR; hour++) {
            int y = HEADER + (hour - FIRST_HOUR) * HOUR_HEIGHT;
            g.setColor(new Color(0xEEF0F2));
            g.drawLine(GUTTER, y, getWidth(), y);
            g.setColor(Color.GRAY);
            g.drawString(String.format("%02d:00", hour), 6, y + 4);
        }
        for (int d = 0; d < 7; d++) {
            int x = GUTTER + d * dayWidth;
            g.setColor(new Color(0xDDE1E5));
            g.drawLine(x, 0, x, getHeight());
            g.setColor(d == 0 ? new Color(0x1D3557) : Color.DARK_GRAY);
            g.setFont(getFont().deriveFont(d == 0 ? Font.BOLD : Font.PLAIN, 12f));
            g.drawString(DAY.format(today.plusDays(d)), x + 6, 18);
        }

        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        for (Item item : items) {
            Rectangle r = bounds(item, today, dayWidth);
            if (r == null) {
                continue;
            }
            g.setColor(item.color);
            g.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);
            if (highlighted.contains(item.id)) {
                g.setColor(CONFLICT);
                g.setStroke(new BasicStroke(2.5f));
                g.drawRoundRect(r.x, r.y, r.width, r.height, 8, 8);
                g.setStroke(new BasicStroke(1f));
            }
            g.setColor(item.done ? Color.DARK_GRAY : Color.WHITE);
            Graphics2D clip = (Graphics2D) g.create(r.x + 4, r.y + 2, r.width - 8, r.height - 4);
            FontMetrics metrics = clip.getFontMetrics();
            clip.drawString(item.title, 0, metrics.getAscent());
            if (r.height > 2 * metrics.getHeight()) {
                clip.drawString(TimeFormat.time(item.start) + "-" + TimeFormat.time(item.end), 0,
                        metrics.getAscent() + metrics.getHeight());
            }
            clip.dispose();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int nowY = yFor(now.getHour() * 60 + now.getMinute());
        if (nowY >= HEADER) {
            g.setColor(CONFLICT);
            g.fillRect(GUTTER, nowY, dayWidth, 2);
        }
        g.dispose();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        LocalDate today = LocalDate.now(clock);
        for (Item item : items) {
            Rectangle r = bounds(item, today, dayWidth());
            if (r != null && r.contains(event.getPoint())) {
                return item.title + "  " + TimeFormat.range(item.start, item.end) + "  (" + item.id + ")";
            }
        }
        return null;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return HOUR_HEIGHT / 2;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return HOUR_HEIGHT * 4;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    private Rectangle bounds(Item item, LocalDate today, int dayWidth) {
        int day = (int) (item.start.toLocalDate().toEpochDay() - today.toEpochDay());
        if (day < 0 || day > 6) {
            return null;
        }
        int startMinutes = item.start.getHour() * 60 + item.start.getMinute();
        int endMinutes = item.end.toLocalDate().equals(item.start.toLocalDate())
                ? item.end.getHour() * 60 + item.end.getMinute() : 24 * 60;
        int top = Math.max(yFor(startMinutes), HEADER);
        int bottom = Math.min(yFor(endMinutes), HEADER + (LAST_HOUR - FIRST_HOUR) * HOUR_HEIGHT);
        if (bottom <= top) {
            return null;
        }
        return new Rectangle(GUTTER + day * dayWidth + 3, top + 1, dayWidth - 6, Math.max(bottom - top - 2, 6));
    }

    private int yFor(int minutesOfDay) {
        return HEADER + (minutesOfDay - FIRST_HOUR * 60) * HOUR_HEIGHT / 60;
    }

    private int dayWidth() {
        return Math.max((getWidth() - GUTTER) / 7, 40);
    }

    private static Color colorFor(Priority priority) {
        return switch (priority) {
            case HIGH -> HIGH;
            case MEDIUM -> MEDIUM;
            case LOW -> LOW;
        };
    }

    private record Item(String id, String title, LocalDateTime start, LocalDateTime end, Color color, boolean done) {
    }
}
