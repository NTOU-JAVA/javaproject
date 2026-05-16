package ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import model.Reminder;

final class ReminderChips {
    private static final DateTimeFormatter ABS_FMT =
            DateTimeFormatter.ofPattern("MM/dd HH:mm");

    private ReminderChips() {}

    static JPanel build(List<Reminder> reminders, LocalDateTime deadline, int maxVisible) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        List<ReminderInfo> infos = upcoming(reminders, deadline);
        int visible = Math.min(Math.max(0, maxVisible), infos.size());
        for (int i = 0; i < visible; i++) {
            panel.add(new ChipLabel(infos.get(i).text, true));
        }
        if (infos.size() > visible) {
            panel.add(new ChipLabel("+" + (infos.size() - visible), true));
        }
        if (panel.getComponentCount() == 0) {
            panel.add(Box.createRigidArea(new Dimension(0, 0)));
        }
        return panel;
    }

    static boolean hasUpcoming(List<Reminder> reminders, LocalDateTime deadline) {
        return !upcoming(reminders, deadline).isEmpty();
    }

    private static List<ReminderInfo> upcoming(List<Reminder> reminders, LocalDateTime deadline) {
        List<ReminderInfo> infos = new ArrayList<>();
        if (reminders == null || reminders.isEmpty()) return infos;
        LocalDateTime now = LocalDateTime.now();
        for (Reminder reminder : reminders) {
            if (reminder == null) continue;
            LocalDateTime target = reminder.resolve(deadline);
            if (target == null || target.isBefore(now)) continue;
            infos.add(new ReminderInfo(target, displayText(reminder, target)));
        }
        infos.sort(Comparator.comparing(info -> info.target));
        return infos;
    }

    private static String displayText(Reminder reminder, LocalDateTime target) {
        if (reminder.getType() == Reminder.Type.ABSOLUTE) {
            return target.format(ABS_FMT);
        }
        int minutes = reminder.getMinutesBefore();
        if (minutes == 0) return "due time";
        if (minutes % 1440 == 0) return "before " + (minutes / 1440) + "d";
        if (minutes % 60 == 0) return "before " + (minutes / 60) + "h";
        return "before " + minutes + "m";
    }

    private static class ReminderInfo {
        final LocalDateTime target;
        final String text;

        ReminderInfo(LocalDateTime target, String text) {
            this.target = target;
            this.text = text;
        }
    }

    private static class ChipLabel extends JLabel {
        private final boolean drawClock;

        ChipLabel(String text, boolean drawClock) {
            super(text);
            this.drawClock = drawClock;
            setFont(AppFonts.CAPTION);
            setForeground(AppColors.ACCENT);
            setOpaque(false);
            setBorder(new EmptyBorder(2, drawClock ? 20 : 7, 2, 7));
        }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(getText()) + (drawClock ? 28 : 16), fm.getHeight() + 4);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(AppColors.ACCENT_LIGHT);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.setColor(new Color(AppColors.ACCENT.getRed(),
                    AppColors.ACCENT.getGreen(), AppColors.ACCENT.getBlue(), 50));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            if (drawClock) {
                int cy = getHeight() / 2;
                int cx = 10;
                int r = 5;
                g2.setColor(AppColors.ACCENT);
                g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawOval(cx - r, cy - r, r * 2, r * 2);
                g2.drawLine(cx, cy, cx, cy - 3);
                g2.drawLine(cx, cy, cx + 3, cy + 2);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
