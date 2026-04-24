import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * TodoPanel：代辦事項面板，風格與 CalendarPanel 統一。
 * 支援：標題、說明、可選 deadline、完成勾選、提醒。
 */
public class TodoPanel extends JPanel {

    private static final DateTimeFormatter REMINDER_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final List<TodoItem>        todos;
    private final Runnable              saveCallback;
    private final java.util.Set<Integer> remindedIds = new java.util.HashSet<>();
    private final Timer                 reminderTimer;

    // 清單區
    private final DefaultListModel<TodoItem> listModel = new DefaultListModel<>();
    private final JList<TodoItem>            todoList  = new JList<>(listModel);

    // 右側操作按鈕
    private final JButton editButton   = makeBtn("編輯");
    private final JButton deleteButton = makeBtn("刪除");
    private final JButton doneButton   = makeBtn("標記完成");

    private boolean updatingList = false;

    public TodoPanel(List<TodoItem> todos, Runnable saveCallback) {
        this.todos        = todos;
        this.saveCallback = saveCallback;

        setLayout(new BorderLayout(0, 0));
        setBackground(AppColors.BG_SECONDARY);

        add(buildTopNav(),    BorderLayout.NORTH);
        add(buildListArea(),  BorderLayout.CENTER);
        add(buildSidePanel(), BorderLayout.EAST);

        reminderTimer = new Timer(60_000, e -> scanReminders());
        reminderTimer.setInitialDelay(0);
        reminderTimer.start();

        refreshList();
    }

    // ── 頂部列 ──────────────────────────────────────────────────────────────
    private JPanel buildTopNav() {
        JPanel nav = new JPanel(new BorderLayout());
        nav.setBackground(AppColors.BG_SECONDARY);
        nav.setBorder(new EmptyBorder(12, 16, 8, 16));

        JLabel title = new JLabel("代辦事項");
        title.setFont(AppFonts.TITLE_MEDIUM);
        title.setForeground(AppColors.TEXT_PRIMARY);

        JButton addBtn = new JButton("＋  新增代辦");
        addBtn.setFont(AppFonts.BODY_SMALL);
        addBtn.setBackground(AppColors.ACCENT);
        addBtn.setForeground(Color.WHITE);
        addBtn.setBorder(new EmptyBorder(7, 16, 7, 16));
        addBtn.setFocusPainted(false);
        addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addBtn.addActionListener(e -> showTodoDialog(null));

        nav.add(title,  BorderLayout.WEST);
        nav.add(addBtn, BorderLayout.EAST);
        return nav;
    }

    // ── 清單區 ──────────────────────────────────────────────────────────────
    private JPanel buildListArea() {
        JPanel area = new JPanel(new BorderLayout());
        area.setBackground(AppColors.BG_PRIMARY);
        area.setBorder(new MatteBorder(1, 0, 1, 0, AppColors.BORDER_DEFAULT));

        todoList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        todoList.setBackground(AppColors.BG_PRIMARY);
        todoList.setFixedCellHeight(52);
        todoList.setCellRenderer(new TodoCellRenderer());

        todoList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updatingList) refreshActionButtons();
        });

        todoList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int idx = todoList.locationToIndex(e.getPoint());
                if (e.getClickCount() == 2 && idx >= 0)
                    showTodoDialog(todos.get(idx));
            }
        });

        JScrollPane sp = new JScrollPane(todoList);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        area.add(sp, BorderLayout.CENTER);
        return area;
    }

    // ── 右側操作面板 ────────────────────────────────────────────────────────
    private JPanel buildSidePanel() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(AppColors.SIDEBAR_BG);
        side.setPreferredSize(new Dimension(148, 0));
        side.setBorder(new MatteBorder(0, 1, 0, 0, AppColors.BORDER_DEFAULT));

        side.add(Box.createRigidArea(new Dimension(0, 56)));
        side.add(sideLabel("代辦操作"));
        side.add(Box.createRigidArea(new Dimension(0, 8)));

        for (JButton b : new JButton[]{editButton, doneButton, deleteButton})
            styleActionButton(b);

        editButton.addActionListener(e -> {
            TodoItem sel = todoList.getSelectedValue();
            if (sel != null) showTodoDialog(sel);
        });
        doneButton.addActionListener(e -> toggleDone());
        deleteButton.addActionListener(e -> deleteSelected());

        side.add(sideBtn(editButton));
        side.add(Box.createRigidArea(new Dimension(0, 6)));
        side.add(sideBtn(doneButton));
        side.add(Box.createRigidArea(new Dimension(0, 6)));
        side.add(sideBtn(deleteButton));

        side.add(Box.createRigidArea(new Dimension(0, 20)));
        side.add(sideLabel("操作提示"));
        side.add(Box.createRigidArea(new Dimension(0, 4)));

        JLabel hint = new JLabel(
            "<html><div style='width:110px;color:#A8A7A4;font-size:10px;line-height:1.5'>" +
            "雙擊代辦項目<br>可快速編輯</div></html>");
        hint.setBorder(new EmptyBorder(0, 12, 0, 4));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(hint);
        side.add(Box.createVerticalGlue());

        refreshActionButtons();
        return side;
    }

    private JPanel sideBtn(JButton btn) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(0, 8, 0, 8));
        p.add(btn);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JLabel sideLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(AppFonts.LABEL);
        l.setForeground(AppColors.TEXT_TERTIARY);
        l.setBorder(new EmptyBorder(0, 12, 0, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        return l;
    }

    private void refreshActionButtons() {
        boolean has = todoList.getSelectedValue() != null;
        editButton.setEnabled(has);
        deleteButton.setEnabled(has);
        doneButton.setEnabled(has);
        if (has && todoList.getSelectedValue() != null) {
            doneButton.setText(todoList.getSelectedValue().isCompleted()
                    ? "取消完成" : "標記完成");
        } else {
            doneButton.setText("標記完成");
        }
    }

    // ── 新增/編輯 Dialog（改用 JDialog，修正內容被截掉的問題） ──────────────
    private void showTodoDialog(TodoItem editItem) {
        boolean isEdit = (editItem != null);

        // ── 取得父視窗 ──
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(owner,
                isEdit ? "編輯代辦" : "新增代辦",
                Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setLayout(new BorderLayout());
        dlg.setResizable(false);

        // ── 欄位 ──
        JTextField titleField = new JTextField(isEdit ? editItem.getTitle() : "", 22);
        titleField.setFont(AppFonts.BODY_MEDIUM);

        JTextArea descArea = new JTextArea(isEdit ? editItem.getDescription() : "", 3, 22);
        descArea.setFont(AppFonts.BODY_SMALL);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        JScrollPane descScroll = new JScrollPane(descArea);
        descScroll.setPreferredSize(new Dimension(280, 62));

        // ── Deadline（提醒時間）可選 ──
        LocalDateTime base = LocalDateTime.now();
        if (isEdit && editItem.getReminderTime() != null) {
            try { base = LocalDateTime.parse(editItem.getReminderTime(), REMINDER_FMT); }
            catch (DateTimeParseException ignored) {}
        }

        boolean initHasDeadline = isEdit && editItem.getReminderTime() != null;
        JCheckBox deadlineCheck = new JCheckBox("設定截止提醒時間", initHasDeadline);
        deadlineCheck.setFont(AppFonts.BODY_SMALL);

        JSpinner yearSp  = new JSpinner(new SpinnerNumberModel(base.getYear(),         2020, 2099, 1));
        JSpinner monthSp = new JSpinner(new SpinnerNumberModel(base.getMonthValue(),   1,    12,   1));
        JSpinner daySp   = new JSpinner(new SpinnerNumberModel(base.getDayOfMonth(),   1,    31,   1));
        JSpinner hourSp  = new JSpinner(new SpinnerNumberModel(base.getHour(),         0,    23,   1));
        JSpinner minSp   = new JSpinner(new SpinnerNumberModel(base.getMinute(),       0,    59,   1));

        for (JSpinner s : new JSpinner[]{monthSp, daySp, hourSp, minSp})
            s.setEditor(new JSpinner.NumberEditor(s, "00"));
        yearSp .setPreferredSize(new Dimension(68, 26));
        monthSp.setPreferredSize(new Dimension(48, 26));
        daySp  .setPreferredSize(new Dimension(48, 26));
        hourSp .setPreferredSize(new Dimension(48, 26));
        minSp  .setPreferredSize(new Dimension(48, 26));

        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        dateRow.setOpaque(false);
        dateRow.add(new JLabel("日期 "));
        dateRow.add(yearSp);  dateRow.add(new JLabel("/"));
        dateRow.add(monthSp); dateRow.add(new JLabel("/")); dateRow.add(daySp);

        JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        timeRow.setOpaque(false);
        timeRow.add(new JLabel("時間 "));
        timeRow.add(hourSp); timeRow.add(new JLabel(":")); timeRow.add(minSp);

        JPanel dtPanel = new JPanel();
        dtPanel.setLayout(new BoxLayout(dtPanel, BoxLayout.Y_AXIS));
        dtPanel.setOpaque(false);
        dtPanel.add(dateRow);
        dtPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        dtPanel.add(timeRow);
        dtPanel.setVisible(initHasDeadline);

        // 勾選截止時間時重新 pack，確保視窗大小正確展開
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

        // ── 取消 ──
        cancelBtn.addActionListener(e -> dlg.dispose());
        dlg.getRootPane().setDefaultButton(okBtn);

        // ── 確認儲存 ──
        okBtn.addActionListener(e -> {
            String titleVal = titleField.getText().trim();
            if (titleVal.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "標題不可為空。",
                        "輸入錯誤", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String reminder = null;
            if (deadlineCheck.isSelected()) {
                int y  = (int) yearSp.getValue(),  mo = (int) monthSp.getValue(),
                    d  = (int) daySp.getValue(),   h  = (int) hourSp.getValue(),
                    mi = (int) minSp.getValue();
                try { LocalDateTime.of(y, mo, d, h, mi); }
                catch (Exception ex) {
                    JOptionPane.showMessageDialog(dlg, "日期不合法。",
                            "日期錯誤", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                reminder = String.format("%04d-%02d-%02d %02d:%02d", y, mo, d, h, mi);
            }

            if (isEdit) {
                editItem.setTitle(titleVal);
                editItem.setDescription(descArea.getText().trim());
                editItem.setReminderTime(reminder);
                remindedIds.remove(editItem.getId());
            } else {
                int nextId = todos.isEmpty() ? 1
                        : todos.stream().mapToInt(TodoItem::getId).max().orElse(0) + 1;
                TodoItem item = new TodoItem(nextId, titleVal,
                        descArea.getText().trim(), reminder);
                todos.add(item);
            }

            dlg.dispose();
            refreshList();
            saveCallback.run();
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

    private void toggleDone() {
        TodoItem sel = todoList.getSelectedValue();
        if (sel == null) return;
        sel.setCompleted(!sel.isCompleted());
        todoList.repaint();
        refreshActionButtons();
        saveCallback.run();
    }

    private void deleteSelected() {
        TodoItem sel = todoList.getSelectedValue();
        if (sel == null) return;
        int confirm = JOptionPane.showConfirmDialog(this,
                "確定要刪除「" + sel.getTitle() + "」？", "確認刪除",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        remindedIds.remove(sel.getId());
        todos.remove(sel);
        refreshList();
        saveCallback.run();
    }

    // ── 更新清單顯示 ─────────────────────────────────────────────────────────
    public void refreshList() {
        updatingList = true;
        try {
            todos.sort((a, b) -> {
                if (a.isCompleted() != b.isCompleted())
                    return a.isCompleted() ? 1 : -1;
                String ta = a.getReminderTime(), tb = b.getReminderTime();
                if (ta == null && tb == null) return 0;
                if (ta == null) return 1;
                if (tb == null) return -1;
                return ta.compareTo(tb);
            });
            listModel.clear();
            for (TodoItem t : todos) listModel.addElement(t);
        } finally {
            updatingList = false;
        }
    }

    // ── Reminder ──────────────────────────────────────────────────────────────
    private void scanReminders() {
        for (TodoItem t : todos) maybeShowReminder(t);
    }

    private void maybeShowReminder(TodoItem todo) {
        if (todo.getReminderTime() == null || todo.isCompleted()) return;
        try {
            LocalDateTime target = LocalDateTime.parse(todo.getReminderTime(), REMINDER_FMT);
            long diff = java.time.Duration.between(LocalDateTime.now(), target).toMinutes();
            if (diff >= 0 && diff <= 240 && !remindedIds.contains(todo.getId())) {
                remindedIds.add(todo.getId());
                JOptionPane.showMessageDialog(this,
                        "提醒：「" + todo.getTitle() + "」將在四小時內到期。",
                        "代辦提醒", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (DateTimeParseException ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static JButton makeBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(AppFonts.BODY_SMALL);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static void styleActionButton(JButton b) {
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
    private static class TodoCellRenderer extends JPanel implements ListCellRenderer<TodoItem> {
        private final JLabel iconLabel  = new JLabel();
        private final JLabel titleLabel = new JLabel();
        private final JLabel descLabel  = new JLabel();
        private final JLabel timeLabel  = new JLabel();

        TodoCellRenderer() {
            setLayout(new BorderLayout(6, 0));
            setBorder(new EmptyBorder(6, 10, 6, 10));

            iconLabel.setPreferredSize(new Dimension(22, 22));
            iconLabel.setFont(new Font("Serif", Font.PLAIN, 16));
            iconLabel.setVerticalAlignment(SwingConstants.TOP);

            JPanel center = new JPanel(new GridLayout(2, 1, 0, 1));
            center.setOpaque(false);
            center.add(titleLabel);
            center.add(descLabel);
            descLabel.setFont(AppFonts.CAPTION);
            descLabel.setForeground(AppColors.TEXT_TERTIARY);

            add(iconLabel, BorderLayout.WEST);
            add(center,    BorderLayout.CENTER);
            add(timeLabel, BorderLayout.EAST);
            timeLabel.setFont(AppFonts.CAPTION);
            timeLabel.setVerticalAlignment(SwingConstants.TOP);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends TodoItem> list,
                TodoItem t, int idx, boolean selected, boolean focused) {
            setOpaque(true);

            if (t.isCompleted()) {
                iconLabel.setText("✔");
                iconLabel.setForeground(new Color(0x2F9E44));
                titleLabel.setText("<html><strike><font color='#A8A7A4'>"
                        + t.getTitle() + "</font></strike></html>");
                descLabel.setText("");
            } else {
                iconLabel.setText("○");
                iconLabel.setForeground(AppColors.TEXT_TERTIARY);
                titleLabel.setText(t.getTitle());
                titleLabel.setForeground(AppColors.TEXT_PRIMARY);
                String desc = t.getDescription();
                descLabel.setText(desc != null && !desc.isEmpty() ? desc : "");
            }
            titleLabel.setFont(AppFonts.BODY_SMALL);

            String rt = t.getReminderTime();
            if (rt != null && !t.isCompleted()) {
                try {
                    LocalDateTime target = LocalDateTime.parse(rt, REMINDER_FMT);
                    long mins = java.time.Duration.between(LocalDateTime.now(), target).toMinutes();
                    timeLabel.setForeground(
                            (mins >= 0 && mins <= 1440) ? AppColors.DANGER
                                                        : AppColors.TEXT_TERTIARY);
                } catch (Exception e) {
                    timeLabel.setForeground(AppColors.TEXT_TERTIARY);
                }
                timeLabel.setText(rt.substring(5));
            } else {
                timeLabel.setText("");
            }

            setBackground(selected ? AppColors.ACCENT_LIGHT
                    : (idx % 2 == 0 ? list.getBackground() : new Color(0xFBFBF9)));
            return this;
        }
    }
}