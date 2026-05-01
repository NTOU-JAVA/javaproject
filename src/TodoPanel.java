import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * TodoPanel：代辦事項面板。
 * 左側圓圈可點擊切換完成狀態；每列右側有編輯、刪除按鈕。
 */
public class TodoPanel extends JPanel {

    private static final DateTimeFormatter REMINDER_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final List<TodoItem>         todos;
    private final Runnable               saveCallback;
    private final java.util.Set<Integer> remindedIds = new java.util.HashSet<>();
    private final Timer                  reminderTimer;

    // 清單容器（BoxLayout 垂直排列）
    private final JPanel listContainer = new JPanel();
    { listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS)); }

    public TodoPanel(List<TodoItem> todos, Runnable saveCallback) {
        this.todos        = todos;
        this.saveCallback = saveCallback;

        setLayout(new BorderLayout(0, 0));
        setBackground(AppColors.BG_SECONDARY);

        add(buildTopNav(),   BorderLayout.NORTH);
        add(buildListArea(), BorderLayout.CENTER);
        add(buildHintBar(),  BorderLayout.SOUTH);

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
    private JScrollPane buildListArea() {
        listContainer.setBackground(AppColors.BG_PRIMARY);

        // 外層 wrapper：讓 listContainer 靠頂，不被 viewport 拉伸
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(AppColors.BG_PRIMARY);
        wrapper.add(listContainer, BorderLayout.NORTH);

        JScrollPane sp = new JScrollPane(wrapper);
        sp.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getViewport().setBackground(AppColors.BG_PRIMARY);
        return sp;
    }

    // ── 底部提示列 ────────────────────────────────────────────────────────────
    private JPanel buildHintBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 6));
        bar.setBackground(AppColors.BG_SECONDARY);
        bar.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));
        JLabel hint = new JLabel("點擊左側圓圈切換完成狀態　|　雙擊項目快速編輯");
        hint.setFont(AppFonts.CAPTION);
        hint.setForeground(AppColors.TEXT_TERTIARY);
        bar.add(hint);
        return bar;
    }

    // ── 建立每一列 TodoItem 的 UI ─────────────────────────────────────────────
    private JPanel buildItemRow(TodoItem item, int rowIndex) {
        JPanel row = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // 底部分隔線
                g.setColor(AppColors.BORDER_DEFAULT);
                g.drawLine(0, getHeight()-1, getWidth(), getHeight()-1);
            }
        };
        row.setOpaque(true);
        row.setBackground(rowIndex % 2 == 0 ? AppColors.BG_PRIMARY : new Color(0xFBFBF9));
        // 最小高度 52px，內容多時自動撐高；寬度填滿
        row.setMinimumSize(new Dimension(0, 52));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        // ── 左側：可點擊的圓圈 ──
        JLabel circle = new JLabel(item.isCompleted() ? "✔" : "○", SwingConstants.CENTER);
        circle.setFont(new Font("Serif", Font.PLAIN, 18));
        circle.setForeground(item.isCompleted() ? AppColors.SUCCESS : AppColors.TEXT_TERTIARY);
        circle.setPreferredSize(new Dimension(44, 44));
        circle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        circle.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                item.setCompleted(!item.isCompleted());
                saveCallback.run();
                refreshList();
            }
            @Override public void mouseEntered(MouseEvent e) {
                circle.setForeground(item.isCompleted()
                        ? AppColors.SUCCESS.darker()
                        : AppColors.ACCENT);
            }
            @Override public void mouseExited(MouseEvent e) {
                circle.setForeground(item.isCompleted()
                        ? AppColors.SUCCESS : AppColors.TEXT_TERTIARY);
            }
        });

        // ── 中間：標題 + 說明 + 期限（各自一行）──
        JPanel center = new JPanel() {
            @Override public Dimension getMaximumSize() {
                // 讓 BoxLayout 不把 center 往下無限撐高
                Dimension ps = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, ps.height);
            }
        };
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);
        center.setBorder(new EmptyBorder(8, 0, 8, 0));

        // 標題 — 使用 JTextArea 實現可靠換行
        JTextArea titleLbl = new JTextArea(item.getTitle());
        titleLbl.setFont(item.isCompleted()
                ? AppFonts.BODY_SMALL
                : AppFonts.BODY_SMALL);
        titleLbl.setForeground(item.isCompleted()
                ? AppColors.TEXT_TERTIARY
                : AppColors.TEXT_PRIMARY);
        if (item.isCompleted()) {
            // 刪除線效果：用 HTML label 疊加，但維持 JTextArea 換行
            titleLbl.setForeground(new Color(0xA8A7A4));
        }
        titleLbl.setEditable(false);
        titleLbl.setFocusable(false);
        titleLbl.setLineWrap(true);
        titleLbl.setWrapStyleWord(true);
        titleLbl.setOpaque(false);
        titleLbl.setBorder(null);
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        // 將滑鼠事件轉發給 row，確保雙擊編輯正常運作
        titleLbl.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e)  { row.dispatchEvent(SwingUtilities.convertMouseEvent(titleLbl, e, row)); }
            @Override public void mouseEntered(MouseEvent e)  { row.dispatchEvent(SwingUtilities.convertMouseEvent(titleLbl, e, row)); }
            @Override public void mouseExited(MouseEvent e)   { row.dispatchEvent(SwingUtilities.convertMouseEvent(titleLbl, e, row)); }
            @Override public void mousePressed(MouseEvent e)  { row.dispatchEvent(SwingUtilities.convertMouseEvent(titleLbl, e, row)); }
            @Override public void mouseReleased(MouseEvent e) { row.dispatchEvent(SwingUtilities.convertMouseEvent(titleLbl, e, row)); }
        });

        // 說明行（支援換行）
        String rt = item.getReminderTime();
        String desc = item.getDescription();
        center.add(titleLbl);

        if (!item.isCompleted()) {
            if (!desc.isEmpty()) {
                // 把換行轉成 <br>，用 HTML 顯示
                String descHtml = "<html><font color='#A8A7A4'>"
                        + escHtml(desc).replace("\n", "<br>") + "</font></html>";
                JLabel descLbl = new JLabel(descHtml);
                descLbl.setFont(AppFonts.CAPTION);
                descLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
                center.add(Box.createRigidArea(new Dimension(0, 1)));
                center.add(descLbl);
            }
            if (rt != null) {
                Color timeColor = AppColors.TEXT_TERTIARY;
                try {
                    LocalDateTime target = LocalDateTime.parse(rt, REMINDER_FMT);
                    long mins = java.time.Duration.between(LocalDateTime.now(), target).toMinutes();
                    if (mins >= 0 && mins <= 1440) timeColor = AppColors.DANGER;
                } catch (Exception ignored) {}
                JLabel timeLbl = new JLabel("[期限] " + rt.substring(5));
                timeLbl.setFont(AppFonts.CAPTION);
                timeLbl.setForeground(timeColor);
                timeLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
                center.add(Box.createRigidArea(new Dimension(0, 1)));
                center.add(timeLbl);
            }
        }

        // ── 右側：編輯 + 刪除 按鈕（頂端對齊，不撐高）──
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
        actions.setOpaque(false);
        actions.setAlignmentY(Component.TOP_ALIGNMENT);

        JButton editBtn = actionBtn("編輯", AppColors.BG_TERTIARY, AppColors.TEXT_PRIMARY);
        JButton delBtn  = actionBtn("刪除", AppColors.DANGER_LIGHT, AppColors.DANGER);

        editBtn.addActionListener(e -> showTodoDialog(item));
        delBtn.addActionListener(e  -> deleteItem(item));

        actions.add(editBtn);
        actions.add(delBtn);

        // 雙擊整列也進入編輯
        MouseAdapter dblClick = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) showTodoDialog(item);
            }
        };
        center.addMouseListener(dblClick);
        row.addMouseListener(dblClick);

        row.add(circle,  BorderLayout.WEST);
        row.add(center,  BorderLayout.CENTER);
        row.add(actions, BorderLayout.EAST);
        return row;
    }

    private static JButton actionBtn(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setFont(AppFonts.CAPTION);
        b.setBackground(bg);
        b.setForeground(fg);
        b.setBorder(new EmptyBorder(4, 10, 4, 10));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static String escHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    // ── 新增/編輯 Dialog ─────────────────────────────────────────────────────
    private void showTodoDialog(TodoItem editItem) {
        boolean isEdit = (editItem != null);

        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(owner,
                isEdit ? "編輯代辦" : "新增代辦",
                Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setLayout(new BorderLayout());
        dlg.setResizable(false);

        JTextField titleField = new JTextField(isEdit ? editItem.getTitle() : "", 22);
        titleField.setFont(AppFonts.BODY_MEDIUM);

        JTextArea descArea = new JTextArea(isEdit ? editItem.getDescription() : "", 3, 22);
        descArea.setFont(AppFonts.BODY_SMALL);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        JScrollPane descScroll = new JScrollPane(descArea);
        descScroll.setPreferredSize(new Dimension(280, 62));

        LocalDateTime base = LocalDateTime.now();
        if (isEdit && editItem.getReminderTime() != null) {
            try { base = LocalDateTime.parse(editItem.getReminderTime(), REMINDER_FMT); }
            catch (DateTimeParseException ignored) {}
        }

        boolean initHasDeadline = isEdit && editItem.getReminderTime() != null;
        JCheckBox deadlineCheck = new JCheckBox("設定截止提醒時間", initHasDeadline);
        deadlineCheck.setFont(AppFonts.BODY_SMALL);

        JSpinner yearSp  = new JSpinner(new SpinnerNumberModel(base.getYear(),       2020, 2099, 1));
        JSpinner monthSp = new JSpinner(new SpinnerNumberModel(base.getMonthValue(), 1,    12,   1));
        JSpinner daySp   = new JSpinner(new SpinnerNumberModel(base.getDayOfMonth(), 1,    31,   1));
        JSpinner hourSp  = new JSpinner(new SpinnerNumberModel(base.getHour(),       0,    23,   1));
        JSpinner minSp   = new JSpinner(new SpinnerNumberModel(base.getMinute(),     0,    59,   1));

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

        deadlineCheck.addActionListener(e -> {
            dtPanel.setVisible(deadlineCheck.isSelected());
            dlg.pack();
        });

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

        cancelBtn.addActionListener(e -> dlg.dispose());
        dlg.getRootPane().setDefaultButton(okBtn);

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
                try { java.time.LocalDate.of(y, mo, d); }
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

    private void deleteItem(TodoItem item) {
        int confirm = JOptionPane.showConfirmDialog(this,
                "確定要刪除「" + item.getTitle() + "」？", "確認刪除",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        remindedIds.remove(item.getId());
        todos.remove(item);
        refreshList();
        saveCallback.run();
    }

    // ── 更新清單顯示 ──────────────────────────────────────────────────────────
    public void refreshList() {
        todos.sort((a, b) -> {
            if (a.isCompleted() != b.isCompleted())
                return a.isCompleted() ? 1 : -1;
            String ta = a.getReminderTime(), tb = b.getReminderTime();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return ta.compareTo(tb);
        });

        listContainer.removeAll();
        for (int i = 0; i < todos.size(); i++) {
            listContainer.add(buildItemRow(todos.get(i), i));
        }
        // 空狀態提示
        if (todos.isEmpty()) {
            JLabel empty = new JLabel("目前沒有代辦事項，點擊右上角新增吧！", SwingConstants.CENTER);
            empty.setFont(AppFonts.BODY_SMALL);
            empty.setForeground(AppColors.TEXT_TERTIARY);
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            empty.setBorder(new EmptyBorder(40, 0, 0, 0));
            listContainer.add(empty);
        }
        listContainer.revalidate();
        listContainer.repaint();
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
}