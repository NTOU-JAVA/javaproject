import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;

public class CalendarPanel extends JPanel {

    private static final String[] WEEK_DAY_NAMES = {"日","一","二","三","四","五","六"};
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd");
    private static final DateTimeFormatter ISO_FMT  = DateTimeFormatter.ISO_LOCAL_DATE;

    private final JPanel[]           dayPanels    = new JPanel[7];
    private final JLabel[]           dayLabels    = new JLabel[7];
    private final JLabel             weekLabel    = new JLabel("", SwingConstants.CENTER);
    private LocalDate                weekStart;
    private final List<Task>         tasks;
    private final List<JList<Task>>  dayTaskLists = new ArrayList<>();

    private Task selectedTask     = null;
    private int  selectedDayIndex = -1;

    private final javax.swing.Timer reminderTimer;
    private final Set<Integer>      remindedIds = new HashSet<>();

    private final JButton editButton   = sideButton("✎  編輯任務");
    private final JButton deleteButton = sideButton("✕  刪除任務");

    public CalendarPanel(List<Task> tasks) {
        this.tasks = tasks;

        LocalDate today = LocalDate.now();
        int dow = today.getDayOfWeek().getValue() % 7;
        this.weekStart = today.minusDays(dow);

        setLayout(new BorderLayout(0, 0));
        setBackground(AppColors.BG_SECONDARY);

        add(buildTopNav(),    BorderLayout.NORTH);
        add(buildGrid(),      BorderLayout.CENTER);
        add(buildSidePanel(), BorderLayout.EAST);

        updateCalendar();

        reminderTimer = new javax.swing.Timer(60_000, e -> scanReminders());
        reminderTimer.setInitialDelay(0);
        reminderTimer.start();
    }

    // ── 頂部週切換列 ──────────────────────────────────────────────────────────
    private JPanel buildTopNav() {
        JPanel nav = new JPanel(new BorderLayout());
        nav.setBackground(AppColors.BG_SECONDARY);
        nav.setBorder(new EmptyBorder(12, 16, 8, 16));

        weekLabel.setFont(AppFonts.TITLE_MEDIUM);
        weekLabel.setForeground(AppColors.TEXT_PRIMARY);

        JButton prevBtn  = navBtn("\u2039");
        JButton nextBtn  = navBtn("\u203A");
        JButton todayBtn = new JButton("今天");
        todayBtn.setFont(AppFonts.BODY_SMALL);
        todayBtn.setForeground(AppColors.ACCENT);
        todayBtn.setBackground(AppColors.ACCENT_LIGHT);
        todayBtn.setBorder(new EmptyBorder(4, 12, 4, 12));
        todayBtn.setFocusPainted(false);
        todayBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        prevBtn.addActionListener(e -> { weekStart = weekStart.minusWeeks(1); updateCalendar(); });
        nextBtn.addActionListener(e -> { weekStart = weekStart.plusWeeks(1);  updateCalendar(); });
        todayBtn.addActionListener(e -> {
            LocalDate t = LocalDate.now();
            weekStart = t.minusDays(t.getDayOfWeek().getValue() % 7);
            updateCalendar();
        });

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(prevBtn); left.add(nextBtn); left.add(todayBtn); left.add(weekLabel);

        JButton addBtn = new JButton("+ 新增任務");
        addBtn.setFont(AppFonts.BODY_SMALL);
        addBtn.setBackground(AppColors.ACCENT);
        addBtn.setForeground(Color.WHITE);
        addBtn.setBorder(new EmptyBorder(7, 16, 7, 16));
        addBtn.setFocusPainted(false);
        addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addBtn.addActionListener(e -> openTaskDialog(null, LocalDate.now()));

        nav.add(left,   BorderLayout.WEST);
        nav.add(addBtn, BorderLayout.EAST);
        return nav;
    }

    private JButton navBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(AppFonts.TITLE_MEDIUM);
        b.setForeground(AppColors.TEXT_SECONDARY);
        b.setBorder(new EmptyBorder(2, 8, 2, 8));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setContentAreaFilled(false);
        return b;
    }

    // ── 七天格子 ──────────────────────────────────────────────────────────────
    private JPanel buildGrid() {
        JPanel grid = new JPanel(new GridLayout(1, 7, 1, 0));
        grid.setBackground(AppColors.BORDER_DEFAULT);
        grid.setBorder(new MatteBorder(1, 0, 1, 0, AppColors.BORDER_DEFAULT));

        for (int i = 0; i < 7; i++) {
            final int idx = i;
            JPanel dayPanel = new JPanel(new BorderLayout());
            dayPanel.setBackground(AppColors.BG_PRIMARY);

            dayLabels[i] = new JLabel("", SwingConstants.CENTER);
            dayLabels[i].setFont(AppFonts.BODY_SMALL);
            dayLabels[i].setOpaque(true);
            dayLabels[i].setBackground(AppColors.BG_PRIMARY);
            dayLabels[i].setBorder(new MatteBorder(0, 0, 1, 0, AppColors.BORDER_DEFAULT));
            dayLabels[i].setPreferredSize(new Dimension(0, 44));
            dayPanel.add(dayLabels[i], BorderLayout.NORTH);

            JList<Task> list = new JList<>(new DefaultListModel<>());
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setBackground(AppColors.BG_PRIMARY);
            list.setFixedCellHeight(30);
            list.setCellRenderer(new TaskCellRenderer());

            list.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    Task sel = list.getSelectedValue();
                    if (sel != null) {
                        selectedTask = sel;
                        selectedDayIndex = idx;
                        for (int j = 0; j < dayTaskLists.size(); j++)
                            if (j != idx) dayTaskLists.get(j).clearSelection();
                    } else if (selectedDayIndex == idx) {
                        selectedTask = null;
                        selectedDayIndex = -1;
                    }
                    refreshSideButtons();
                }
            });

            list.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    int ci = list.locationToIndex(e.getPoint());
                    if (e.getClickCount() == 2 && ci >= 0) {
                        openTaskDialog(selectedTask, null);
                    } else if (e.getClickCount() == 1 && ci < 0) {
                        openTaskDialog(null, weekStart.plusDays(idx));
                    }
                }
            });

            dayTaskLists.add(list);
            JScrollPane sp = new JScrollPane(list);
            sp.setBorder(null);
            sp.getVerticalScrollBar().setPreferredSize(new Dimension(5, 0));
            dayPanel.add(sp, BorderLayout.CENTER);
            dayPanels[i] = dayPanel;
            grid.add(dayPanel);
        }
        return grid;
    }

    // ── 右側操作面板 ──────────────────────────────────────────────────────────
    private JPanel buildSidePanel() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(AppColors.SIDEBAR_BG);
        side.setPreferredSize(new Dimension(148, 0));
        side.setBorder(new MatteBorder(0, 1, 0, 0, AppColors.BORDER_DEFAULT));

        side.add(Box.createRigidArea(new Dimension(0, 56)));
        side.add(sideSectionLabel("任務操作"));
        side.add(Box.createRigidArea(new Dimension(0, 8)));

        styleSideButton(editButton);
        styleSideButton(deleteButton);

        editButton.addActionListener(e -> {
            if (selectedTask != null) openTaskDialog(selectedTask, null);
        });
        deleteButton.addActionListener(e -> deleteSelectedTask());

        side.add(wrapSideBtn(editButton));
        side.add(Box.createRigidArea(new Dimension(0, 6)));
        side.add(wrapSideBtn(deleteButton));

        side.add(Box.createRigidArea(new Dimension(0, 20)));
        side.add(sideSectionLabel("提示"));
        side.add(Box.createRigidArea(new Dimension(0, 4)));

        JLabel hint = new JLabel("<html><div style='width:110px;color:#A8A7A4;"
                + "font-size:10px;line-height:1.6'>"
                + "點空白處新增<br>雙擊任務編輯</div></html>");
        hint.setBorder(new EmptyBorder(0, 12, 0, 4));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(hint);
        side.add(Box.createVerticalGlue());

        refreshSideButtons();
        return side;
    }

    private void refreshSideButtons() {
        boolean has = selectedTask != null;
        editButton.setEnabled(has);
        deleteButton.setEnabled(has);
    }

    private JPanel wrapSideBtn(JButton btn) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(0, 8, 0, 8));
        p.add(btn);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JLabel sideSectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(AppFonts.LABEL);
        l.setForeground(AppColors.TEXT_TERTIARY);
        l.setBorder(new EmptyBorder(0, 12, 0, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        return l;
    }

    // ── 新增 / 編輯 Dialog（用 JDialog 確保版面穩定） ────────────────────────
    private void openTaskDialog(Task editTask, LocalDate defaultDate) {
        boolean isEdit = (editTask != null);
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(owner, isEdit ? "編輯任務" : "新增任務",
                Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setLayout(new BorderLayout());
        dlg.setResizable(false);

        // ── 欄位 ──
        LocalDate initDate = isEdit
                ? (editTask.hasDeadline() && !editTask.getDate().isEmpty()
                   ? LocalDate.parse(editTask.getDate()) : LocalDate.now())
                : (defaultDate != null ? defaultDate : LocalDate.now());

        JTextField titleField = new JTextField(isEdit ? editTask.getTitle() : "", 24);
        titleField.setFont(AppFonts.BODY_MEDIUM);

        JTextArea descArea = new JTextArea(isEdit ? editTask.getDescription() : "", 3, 24);
        descArea.setFont(AppFonts.BODY_SMALL);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        JScrollPane descScroll = new JScrollPane(descArea);
        descScroll.setPreferredSize(new Dimension(300, 62));

        JCheckBox importantCheck = new JCheckBox("★  標記為重要任務",
                isEdit && editTask.isImportant());
        importantCheck.setFont(AppFonts.BODY_SMALL);

        // ── Deadline ──
        boolean initHasDeadline = isEdit ? editTask.hasDeadline() : true;
        int initH = 9, initM = 0;
        if (isEdit && editTask.hasDeadline() && !editTask.getTime().isEmpty()) {
            String[] tp = editTask.getTime().split(":");
            try { initH = Integer.parseInt(tp[0]); initM = Integer.parseInt(tp[1]); }
            catch (NumberFormatException ignored) {}
        }

        JCheckBox deadlineCheck = new JCheckBox("設定截止日期與時間", initHasDeadline);
        deadlineCheck.setFont(AppFonts.BODY_SMALL);

        JSpinner yearSp  = makeSpinner(initDate.getYear(),       2020, 2099, 1);
        JSpinner monthSp = makeSpinner(initDate.getMonthValue(), 1,    12,   1);
        JSpinner daySp   = makeSpinner(initDate.getDayOfMonth(), 1,    31,   1);
        JSpinner hourSp  = makeSpinner(initH,                    0,    23,   1);
        JSpinner minSp   = makeSpinner(initM,                    0,    59,   1);
        for (JSpinner s : new JSpinner[]{monthSp, daySp, hourSp, minSp})
            s.setEditor(new JSpinner.NumberEditor(s, "00"));
        yearSp .setPreferredSize(new Dimension(68, 26));
        monthSp.setPreferredSize(new Dimension(50, 26));
        daySp  .setPreferredSize(new Dimension(50, 26));
        hourSp .setPreferredSize(new Dimension(50, 26));
        minSp  .setPreferredSize(new Dimension(50, 26));

        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        dateRow.setOpaque(false);
        dateRow.add(new JLabel("日期："));
        dateRow.add(yearSp); dateRow.add(new JLabel("/")); dateRow.add(monthSp);
        dateRow.add(new JLabel("/")); dateRow.add(daySp);

        JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        timeRow.setOpaque(false);
        timeRow.add(new JLabel("時間："));
        timeRow.add(hourSp); timeRow.add(new JLabel(":")); timeRow.add(minSp);

        JPanel dtPanel = new JPanel();
        dtPanel.setLayout(new BoxLayout(dtPanel, BoxLayout.Y_AXIS));
        dtPanel.setOpaque(false);
        dtPanel.add(dateRow);
        dtPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        dtPanel.add(timeRow);
        dtPanel.setVisible(initHasDeadline);
        deadlineCheck.addActionListener(e -> {
            dtPanel.setVisible(deadlineCheck.isSelected());
            dlg.pack();
        });

        // ── 組合內容面板 ──
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(16, 20, 8, 20));

        content.add(fieldRow("標題", titleField));
        content.add(Box.createRigidArea(new Dimension(0, 10)));
        content.add(fieldRow("說明", descScroll));
        content.add(Box.createRigidArea(new Dimension(0, 12)));
        content.add(leftAlign(deadlineCheck));
        content.add(Box.createRigidArea(new Dimension(0, 4)));
        content.add(leftAlign(dtPanel));
        content.add(Box.createRigidArea(new Dimension(0, 10)));
        content.add(leftAlign(importantCheck));

        // ── 底部按鈕列 ──
        JButton okBtn     = new JButton(isEdit ? "儲存" : "新增");
        JButton cancelBtn = new JButton("取消");
        okBtn.setFont(AppFonts.BODY_SMALL);
        cancelBtn.setFont(AppFonts.BODY_SMALL);
        okBtn.setBackground(AppColors.ACCENT);
        okBtn.setForeground(Color.WHITE);
        okBtn.setFocusPainted(false);
        okBtn.setBorder(new EmptyBorder(6, 20, 6, 20));
        cancelBtn.setBorder(new EmptyBorder(6, 16, 6, 16));
        cancelBtn.setFocusPainted(false);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        btnRow.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));
        btnRow.add(cancelBtn);
        btnRow.add(okBtn);

        dlg.add(content, BorderLayout.CENTER);
        dlg.add(btnRow,  BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(this);

        // 取消
        cancelBtn.addActionListener(e -> dlg.dispose());
        dlg.getRootPane().setDefaultButton(okBtn);

        // 確認儲存
        okBtn.addActionListener(e -> {
            String titleVal = titleField.getText().trim();
            if (titleVal.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "標題不可為空。", "輸入錯誤", JOptionPane.WARNING_MESSAGE);
                return;
            }
            boolean hasDeadline = deadlineCheck.isSelected();
            String dateVal = "", timeVal = "";
            if (hasDeadline) {
                int y=(int)yearSp.getValue(), mo=(int)monthSp.getValue(),
                    d=(int)daySp.getValue(),  h=(int)hourSp.getValue(),
                    mi=(int)minSp.getValue();
                try { LocalDate.of(y, mo, d); }
                catch (Exception ex) {
                    JOptionPane.showMessageDialog(dlg,"日期不合法。","日期錯誤",JOptionPane.WARNING_MESSAGE);
                    return;
                }
                dateVal = String.format("%04d-%02d-%02d", y, mo, d);
                timeVal = String.format("%02d:%02d", h, mi);
            }
            if (isEdit) {
                editTask.setTitle(titleVal);
                editTask.setDescription(descArea.getText().trim());
                editTask.setHasDeadline(hasDeadline);
                editTask.setDate(dateVal);
                editTask.setTime(timeVal);
                editTask.setImportant(importantCheck.isSelected());
                remindedIds.remove(editTask.getId());
                selectedTask = null;
            } else {
                int nextId = tasks.isEmpty() ? 1
                        : tasks.stream().mapToInt(Task::getId).max().orElse(0) + 1;
                Task t = new Task(nextId, titleVal, descArea.getText().trim(),
                                  dateVal, timeVal, hasDeadline);
                t.setImportant(importantCheck.isSelected());
                tasks.add(t);
            }
            dlg.dispose();
            updateCalendar();
        });

        dlg.setVisible(true);
    }

    /** 標籤 + 元件水平排列的一列 */
    private JPanel fieldRow(String labelText, JComponent comp) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(AppFonts.BODY_SMALL);
        lbl.setForeground(AppColors.TEXT_SECONDARY);
        lbl.setPreferredSize(new Dimension(36, 0));
        row.add(lbl,  BorderLayout.WEST);
        row.add(comp, BorderLayout.CENTER);
        return row;
    }

    private JPanel leftAlign(JComponent comp) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        p.add(comp);
        return p;
    }

    private JSpinner makeSpinner(int val, int min, int max, int step) {
        return new JSpinner(new SpinnerNumberModel(val, min, max, step));
    }

    private void deleteSelectedTask() {
        if (selectedTask == null) return;
        int confirm = JOptionPane.showConfirmDialog(this,
                "確定要刪除「" + selectedTask.getTitle() + "」？",
                "確認刪除", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            remindedIds.remove(selectedTask.getId());
            tasks.remove(selectedTask);
            selectedTask = null;
            refreshSideButtons();
            updateCalendar();
        }
    }

    // ── 更新顯示 ──────────────────────────────────────────────────────────────
    public void updateCalendar() {
        LocalDate today   = LocalDate.now();
        LocalDate weekEnd = weekStart.plusDays(6);
        weekLabel.setText(weekStart.format(DATE_FMT) + " - " + weekEnd.format(DATE_FMT));

        for (int i = 0; i < 7; i++) {
            LocalDate date    = weekStart.plusDays(i);
            String    dateStr = date.format(ISO_FMT);
            boolean   isToday   = date.equals(today);
            boolean   isWeekend = (i == 0 || i == 6);

            // 日期標頭文字
            String dayNum  = String.valueOf(date.getDayOfMonth());
            String dayName = WEEK_DAY_NAMES[i];
            Color  bgColor = isToday ? AppColors.TODAY_BG : AppColors.BG_PRIMARY;
            Color  fgColor = isWeekend ? AppColors.DANGER : AppColors.TEXT_SECONDARY;

            if (isToday) {
                // 今天：數字用淺藍背景圓框，不用深藍
                dayLabels[i].setText(
                    "<html><center>"
                    + "<span style='background:#EEF2FF;color:#3B5BDB;"
                    + "padding:1px 5px;border-radius:3px'><b>" + dayNum + "</b></span>"
                    + "<br><small style='color:#3B5BDB'>（" + dayName + "）</small>"
                    + "</center></html>");
            } else {
                dayLabels[i].setText(
                    "<html><center><b style='color:"
                    + toHex(fgColor) + "'>" + dayNum + "</b>"
                    + "<br><small style='color:" + toHex(AppColors.TEXT_TERTIARY)
                    + "'>（" + dayName + "）</small></center></html>");
            }
            dayLabels[i].setBackground(bgColor);
            dayPanels[i].setBackground(bgColor);

            // 填入任務
            JList<Task> list = dayTaskLists.get(i);
            DefaultListModel<Task> model = (DefaultListModel<Task>) list.getModel();
            model.clear();

            if (date.equals(today)) {
                tasks.stream()
                     .filter(t -> !t.hasDeadline())
                     .sorted(Comparator.comparing(Task::getTitle))
                     .forEach(model::addElement);
            }
            tasks.stream()
                 .filter(t -> t.hasDeadline() && dateStr.equals(t.getDate()))
                 .sorted(Comparator.comparing(Task::getTime))
                 .forEach(model::addElement);

            if (selectedTask != null) {
                boolean inCol =
                    (selectedTask.hasDeadline() && selectedTask.getDate().equals(dateStr)) ||
                    (!selectedTask.hasDeadline() && date.equals(today));
                if (inCol) list.setSelectedValue(selectedTask, true);
                else       list.clearSelection();
            } else {
                list.clearSelection();
            }
        }
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    // ── Reminder ──────────────────────────────────────────────────────────────
    private void scanReminders() {
        for (Task t : tasks) maybeRemind(t);
    }

    private void maybeRemind(Task task) {
        if (!task.isImportant() || !task.hasDeadline()) return;
        try {
            LocalDateTime target = LocalDateTime.parse(
                    task.getDate() + " " + task.getTime(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            long diff = java.time.Duration.between(LocalDateTime.now(), target).toMinutes();
            if (diff >= 0 && diff <= 240 && !remindedIds.contains(task.getId())) {
                remindedIds.add(task.getId());
                JOptionPane.showMessageDialog(this,
                        "提醒：重要任務「" + task.getTitle() + "」將在四小時內到期。",
                        "任務提醒", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (DateTimeParseException ignored) {}
    }

    // ── 側欄按鈕工廠 ──────────────────────────────────────────────────────────
    private static JButton sideButton(String text) {
        JButton b = new JButton(text);
        b.setFont(AppFonts.BODY_SMALL);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static void styleSideButton(JButton b) {
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorder(new EmptyBorder(5, 10, 5, 10));
        b.setBackground(AppColors.BG_TERTIARY);
        b.setForeground(AppColors.TEXT_PRIMARY);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
    }

    // ── Cell Renderer ─────────────────────────────────────────────────────────
    private static class TaskCellRenderer extends JPanel implements ListCellRenderer<Task> {
        private final JLabel dotLabel   = new JLabel();
        private final JLabel titleLabel = new JLabel();
        private final JLabel timeLabel  = new JLabel();

        TaskCellRenderer() {
            setLayout(new BorderLayout(4, 0));
            setBorder(new EmptyBorder(3, 6, 3, 6));
            dotLabel.setPreferredSize(new Dimension(14, 14));
            dotLabel.setFont(new Font("Dialog", Font.PLAIN, 10));
            JPanel left = new JPanel(new BorderLayout(3, 0));
            left.setOpaque(false);
            left.add(dotLabel,   BorderLayout.WEST);
            left.add(titleLabel, BorderLayout.CENTER);
            add(left,      BorderLayout.CENTER);
            add(timeLabel, BorderLayout.EAST);
            timeLabel.setFont(AppFonts.CAPTION);
            timeLabel.setForeground(AppColors.TEXT_TERTIARY);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Task> list,
                Task t, int idx, boolean selected, boolean focused) {
            setOpaque(true);
            if (t.isImportant()) {
                dotLabel.setText("*");
                dotLabel.setForeground(AppColors.DANGER);
                titleLabel.setText(t.getTitle());
                titleLabel.setForeground(AppColors.DANGER);
                titleLabel.setFont(AppFonts.BODY_SMALL.deriveFont(Font.BOLD));
            } else {
                dotLabel.setText("-");
                dotLabel.setForeground(AppColors.TEXT_TERTIARY);
                titleLabel.setText(t.getTitle());
                titleLabel.setForeground(AppColors.TEXT_PRIMARY);
                titleLabel.setFont(AppFonts.BODY_SMALL);
            }
            timeLabel.setText(t.hasDeadline() ? t.getTime() : "");
            setBackground(selected ? AppColors.ACCENT_LIGHT : list.getBackground());
            return this;
        }
    }
}