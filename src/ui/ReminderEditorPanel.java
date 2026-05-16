package ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import model.Reminder;

public class ReminderEditorPanel extends JPanel {
    private static final int MAX_REMINDERS = 10;
    private static final DateTimeFormatter DATE_BTN_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JDialog owner;
    private final LocalDateTime baseDateTime;
    private final JPanel rowsPanel = new JPanel();
    private final List<ReminderRow> rows = new ArrayList<>();
    private final Runnable onChanged;

    public ReminderEditorPanel(JDialog owner, List<Reminder> initialReminders,
                               LocalDateTime baseDateTime, Runnable onChanged) {
        this.owner = owner;
        this.baseDateTime = baseDateTime != null ? baseDateTime : LocalDateTime.now();
        this.onChanged = onChanged != null ? onChanged : () -> {};

        setLayout(new BorderLayout(0, 8));
        setOpaque(false);

        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        rowsPanel.setOpaque(false);
        rowsPanel.setBorder(null);

        if (initialReminders != null) {
            for (Reminder reminder : initialReminders) {
                if (rows.size() >= MAX_REMINDERS) break;
                addReminderRow(reminder);
            }
        }
        rebuildRows();

        JButton addBtn = smallButton("+ 新增提醒", AppColors.ACCENT_LIGHT, AppColors.ACCENT);
        addBtn.addActionListener(e -> {
            if (rows.size() >= MAX_REMINDERS) {
                showMaxReminderHint();
                return;
            }
            addReminderRow(Reminder.beforeDeadline(10));
            rebuildRows();
            notifyChanged();
        });

        add(rowsPanel, BorderLayout.CENTER);
        add(addBtn, BorderLayout.SOUTH);
    }

    public List<Reminder> getReminders() {
        return getReminders(baseDateTime);
    }

    public List<Reminder> getReminders(LocalDateTime deadline) {
        List<Reminder> reminders = new ArrayList<>();
        for (ReminderRow row : rows) {
            Reminder reminder = normalizeReminder(row.toReminder(), deadline);
            if (reminder != null) reminders.add(reminder);
        }
        return reminders;
    }

    private Reminder normalizeReminder(Reminder reminder, LocalDateTime deadline) {
        if (reminder == null || deadline == null || !deadline.isAfter(LocalDateTime.now())) {
            return null;
        }
        LocalDateTime target = reminder.resolve(deadline);
        if (target != null && !target.isBefore(LocalDateTime.now()) && !target.isAfter(deadline)) {
            return reminder;
        }
        Reminder fallback = defaultReminderFor(deadline);
        LocalDateTime fallbackTarget = fallback.resolve(deadline);
        if (fallbackTarget != null && !fallbackTarget.isBefore(LocalDateTime.now())
                && !fallbackTarget.isAfter(deadline)) {
            return fallback;
        }
        return null;
    }

    private Reminder defaultReminderFor(LocalDateTime deadline) {
        long minutesUntilDeadline = java.time.Duration.between(LocalDateTime.now(), deadline).toMinutes();
        return Reminder.beforeDeadline(minutesUntilDeadline >= 10 ? 10 : 0);
    }

    private void addReminderRow(Reminder reminder) {
        ReminderRow row = new ReminderRow(reminder);
        rows.add(row);
    }

    private void removeReminderRow(ReminderRow row) {
        rows.remove(row);
        rebuildRows();
        notifyChanged();
    }

    private void rebuildRows() {
        rowsPanel.removeAll();
        if (rows.isEmpty()) {
            JLabel empty = new JLabel("尚未新增提醒");
            empty.setFont(AppFonts.BODY_SMALL);
            empty.setForeground(AppColors.TEXT_TERTIARY);
            empty.setBorder(new EmptyBorder(4, 0, 4, 0));
            rowsPanel.add(empty);
        } else {
            for (int i = 0; i < rows.size(); i++) {
                rowsPanel.add(rows.get(i).panel);
                if (i < rows.size() - 1) {
                    rowsPanel.add(Box.createRigidArea(new Dimension(0, 6)));
                }
            }
        }
        rowsPanel.revalidate();
        rowsPanel.repaint();
    }

    private void notifyChanged() {
        onChanged.run();
    }

    private JButton smallButton(String text, Color bg, Color fg) {
        JButton button = new JButton(text);
        button.setFont(AppFonts.CAPTION);
        button.setBackground(bg);
        button.setForeground(fg);
        button.setBorder(new EmptyBorder(5, 10, 5, 10));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void showMaxReminderHint() {
        Window dialogOwner = owner != null ? owner : SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(dialogOwner, "提醒數量已滿", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColors.BORDER_HOVER, 1, true),
                new EmptyBorder(14, 16, 14, 16)));

        JLabel title = new JLabel("提醒數量已滿");
        title.setFont(AppFonts.TITLE_SMALL);
        title.setForeground(AppColors.ACCENT);

        JLabel message = new JLabel("每個項目最多 10 個提醒。");
        message.setFont(AppFonts.BODY_SMALL);
        message.setForeground(AppColors.TEXT_SECONDARY);

        JButton okBtn = smallButton("確認", AppColors.ACCENT, Color.WHITE);
        okBtn.addActionListener(e -> dialog.dispose());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnRow.setOpaque(false);
        btnRow.add(okBtn);

        root.add(title, BorderLayout.NORTH);
        root.add(message, BorderLayout.CENTER);
        root.add(btnRow, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setSize(Math.max(260, dialog.getPreferredSize().width), dialog.getPreferredSize().height);
        AppUIManager.applyRoundedWindowShape(dialog, 12);
        dialog.setLocationRelativeTo(dialogOwner);
        dialog.setVisible(true);
    }

    private JLabel smallLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppFonts.BODY_SMALL);
        label.setForeground(AppColors.TEXT_SECONDARY);
        return label;
    }

    private JButton pickerButton(String text) {
        JButton button = new JButton(text);
        button.setFont(AppFonts.BODY_SMALL);
        button.setForeground(AppColors.TEXT_PRIMARY);
        button.setBackground(Color.WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
                new EmptyBorder(5, 10, 5, 10)));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private class ReminderRow {
        private final JPanel panel = new JPanel(new BorderLayout(4, 0));
        private final JComboBox<String> modeCombo = new JComboBox<>(new String[] {"截止前", "指定時間"});
        private final CardLayout settingLayout = new CardLayout();
        private final JPanel settingPanel = new JPanel(settingLayout);
        private final JSpinner daysSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 365, 1));
        private final JSpinner hoursSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 23, 1));
        private final JSpinner minsSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 59, 5));
        private final LocalDate[] absoluteDate = { baseDateTime.toLocalDate() };
        private final int[] absoluteTime = { baseDateTime.getHour(), baseDateTime.getMinute() };
        private final JButton dateButton = pickerButton(absoluteDate[0].format(DATE_BTN_FMT));
        private final JButton timeButton = pickerButton(String.format("%02d:%02d", absoluteTime[0], absoluteTime[1]));

        ReminderRow(Reminder reminder) {
            panel.setOpaque(false);
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

            SchedulePanel.applyComboStyle(modeCombo);
            modeCombo.setPreferredSize(new Dimension(94, 30));

            JPanel beforePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            beforePanel.setOpaque(false);
            beforePanel.add(spinnerWrap(daysSpinner, 56));
            beforePanel.add(smallLabel("天"));
            beforePanel.add(spinnerWrap(hoursSpinner, 56));
            beforePanel.add(smallLabel("小時"));
            beforePanel.add(spinnerWrap(minsSpinner, 56));
            beforePanel.add(smallLabel("分鐘"));

            JPanel absolutePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            absolutePanel.setOpaque(false);
            absolutePanel.add(dateButton);
            absolutePanel.add(timeButton);

            settingPanel.setOpaque(false);
            settingPanel.add(beforePanel, "before");
            settingPanel.add(absolutePanel, "absolute");

            JButton removeButton = smallButton("-", AppColors.DANGER_LIGHT, AppColors.DANGER);
            removeButton.addActionListener(e -> removeReminderRow(this));

            dateButton.addActionListener(e ->
                AppUIManager.showDatePicker(dateButton, absoluteDate[0], date -> {
                    absoluteDate[0] = date;
                    dateButton.setText(date.format(DATE_BTN_FMT));
                    notifyChanged();
                })
            );
            timeButton.addActionListener(e ->
                AppUIManager.showTimePicker(timeButton, absoluteTime[0], absoluteTime[1], (h, m) -> {
                    absoluteTime[0] = h;
                    absoluteTime[1] = m;
                    timeButton.setText(String.format("%02d:%02d", h, m));
                    notifyChanged();
                })
            );

            modeCombo.addActionListener(e -> {
                settingLayout.show(settingPanel, modeCombo.getSelectedIndex() == 1 ? "absolute" : "before");
                notifyChanged();
            });

            JComponent editor = ((JSpinner.DefaultEditor) daysSpinner.getEditor()).getTextField();
            editor.setFont(AppFonts.BODY_SMALL);
            editor = ((JSpinner.DefaultEditor) hoursSpinner.getEditor()).getTextField();
            editor.setFont(AppFonts.BODY_SMALL);
            editor = ((JSpinner.DefaultEditor) minsSpinner.getEditor()).getTextField();
            editor.setFont(AppFonts.BODY_SMALL);

            if (reminder != null && reminder.getType() == Reminder.Type.ABSOLUTE) {
                modeCombo.setSelectedIndex(1);
                try {
                    LocalDateTime dateTime = LocalDateTime.parse(reminder.getDateTime(), DATE_TIME_FMT);
                    absoluteDate[0] = dateTime.toLocalDate();
                    absoluteTime[0] = dateTime.getHour();
                    absoluteTime[1] = dateTime.getMinute();
                    dateButton.setText(absoluteDate[0].format(DATE_BTN_FMT));
                    timeButton.setText(String.format("%02d:%02d", absoluteTime[0], absoluteTime[1]));
                } catch (Exception ignored) {
                }
            } else {
                int total = reminder != null ? reminder.getMinutesBefore() : 10;
                daysSpinner.setValue(total / 1440);
                total %= 1440;
                hoursSpinner.setValue(total / 60);
                minsSpinner.setValue(total % 60);
                modeCombo.setSelectedIndex(0);
            }
            settingLayout.show(settingPanel, modeCombo.getSelectedIndex() == 1 ? "absolute" : "before");

            JPanel rowFields = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            rowFields.setOpaque(false);
            rowFields.add(modeCombo);
            rowFields.add(settingPanel);

            panel.add(rowFields, BorderLayout.CENTER);
            panel.add(removeButton, BorderLayout.EAST);
        }

        Reminder toReminder() {
            if (modeCombo.getSelectedIndex() == 1) {
                String dateTime = String.format("%04d-%02d-%02d %02d:%02d",
                        absoluteDate[0].getYear(), absoluteDate[0].getMonthValue(),
                        absoluteDate[0].getDayOfMonth(), absoluteTime[0], absoluteTime[1]);
                return Reminder.absolute(dateTime);
            }
            int total = ((Integer) daysSpinner.getValue()) * 1440
                    + ((Integer) hoursSpinner.getValue()) * 60
                    + (Integer) minsSpinner.getValue();
            return Reminder.beforeDeadline(total);
        }

        private JSpinner spinnerWrap(JSpinner spinner, int width) {
            spinner.setFont(AppFonts.BODY_SMALL);
            spinner.setPreferredSize(new Dimension(width, 30));
            spinner.addChangeListener(e -> notifyChanged());
            return spinner;
        }
    }
}
