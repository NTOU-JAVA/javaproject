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
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getViewport().setBackground(AppColors.BG_PRIMARY);
        AppUIManager.applySlimScrollBar(sp);
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

        // ── 右側按鈕區：CardLayout 切換「正常」與「確認刪除」兩種狀態 ──
        CardLayout actionsCard = new CardLayout();
        JPanel actions = new JPanel(actionsCard);
        actions.setOpaque(false);

        // 正常狀態：刪除 | 編輯
        JButton delBtn  = actionBtn("刪除", AppColors.DANGER_LIGHT, AppColors.DANGER);
        JButton editBtn = actionBtn("編輯", AppColors.BG_TERTIARY,  AppColors.TEXT_PRIMARY);
        JPanel normalPane = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 8));
        normalPane.setOpaque(false);
        normalPane.add(delBtn);
        normalPane.add(editBtn);

        // 確認刪除狀態：取消 | 確認刪除
        JButton cancelDelBtn  = actionBtn("取消",    AppColors.BG_TERTIARY, AppColors.TEXT_SECONDARY);
        JButton confirmDelBtn = actionBtn("確認刪除", AppColors.DANGER,      Color.WHITE);
        JPanel confirmPane = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 8));
        confirmPane.setOpaque(false);
        confirmPane.add(cancelDelBtn);
        confirmPane.add(confirmDelBtn);

        actions.add(normalPane,  "normal");
        actions.add(confirmPane, "confirm");
        actionsCard.show(actions, "normal");

        editBtn.addActionListener(e -> showTodoDialog(item));
        delBtn.addActionListener(e -> actionsCard.show(actions, "confirm"));
        cancelDelBtn.addActionListener(e -> actionsCard.show(actions, "normal"));
        confirmDelBtn.addActionListener(e -> {
            remindedIds.remove(item.getId());
            todos.remove(item);
            refreshList();
            saveCallback.run();
        });

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
        JDialog dlg = new JDialog(owner, "", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setUndecorated(true);
        dlg.setLayout(new BorderLayout());
        dlg.setBackground(new Color(0, 0, 0, 0));

        // ── 主容器（懸浮視窗風格：白底 + 明顯邊框 + 陰影） ──
        Color headerBg = AppColors.ACCENT_LIGHT;
        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int W = getWidth()-7, H = getHeight()-7, R = 14;
                // 陰影
                g2.setColor(new Color(0, 0, 0, 28));
                g2.fillRoundRect(5, 7, getWidth()-5, getHeight()-5, R, R);
                // 白底（全部）
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, W, H, R, R);
                // Header 色帶（上圓角）
                JPanel hdr = (JPanel) getComponent(0);
                int hh = hdr.getHeight();
                g2.setColor(headerBg);
                g2.fillRoundRect(0, 0, W, R + hh, R, R);
                g2.fillRect(0, R, W, hh - R);
                // 邊框
                g2.setColor(AppColors.BORDER_HOVER);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, W-1, H-1, R, R);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(0, 0, 7, 7));
        dlg.add(root);

        // ── Header ──
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppColors.ACCENT_LIGHT);
        header.setBorder(new EmptyBorder(12, 16, 12, 10));
        header.setOpaque(false);

        JLabel headerTitle = new JLabel(isEdit ? "編輯代辦事項" : "新增代辦事項");
        headerTitle.setFont(AppFonts.TITLE_SMALL);
        headerTitle.setForeground(AppColors.ACCENT);

        JButton closeBtn = new JButton("×");
        closeBtn.setFont(new Font(AppFonts.BODY_MEDIUM.getFamily(), Font.PLAIN, 16));
        closeBtn.setForeground(AppColors.TEXT_TERTIARY);
        closeBtn.setBorder(new EmptyBorder(0, 8, 0, 4));
        closeBtn.setFocusPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dlg.dispose());
        header.add(headerTitle, BorderLayout.CENTER);
        header.add(closeBtn,    BorderLayout.EAST);

        // ── 欄位 ──
        JTextField titleField = new JTextField(isEdit ? editItem.getTitle() : "");
        titleField.setFont(AppFonts.BODY_MEDIUM);
        titleField.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
            new EmptyBorder(5, 8, 5, 8)));

        JTextArea descArea = new JTextArea(isEdit ? editItem.getDescription() : "", 4, 0);
        descArea.setFont(AppFonts.BODY_SMALL);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane descScroll = new JScrollPane(descArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        descScroll.setBorder(new LineBorder(AppColors.BORDER_DEFAULT, 1, true));
        descScroll.setPreferredSize(new Dimension(0, 86));
        descScroll.setMinimumSize(new Dimension(0, 86));
        AppUIManager.applySlimScrollBar(descScroll);

        // 截止時間
        LocalDateTime base = LocalDateTime.now();
        if (isEdit && editItem.getReminderTime() != null) {
            try { base = LocalDateTime.parse(editItem.getReminderTime(), REMINDER_FMT); }
            catch (DateTimeParseException ignored) {}
        }
        boolean initHasDeadline = isEdit && editItem.getReminderTime() != null;

        JCheckBox deadlineCheck = new JCheckBox("設定截止提醒時間", initHasDeadline);
        deadlineCheck.setFont(AppFonts.BODY_SMALL);
        deadlineCheck.setForeground(AppColors.TEXT_SECONDARY);
        deadlineCheck.setOpaque(false);

        // ── 日期/時間 picker 按鈕 ──
        final java.time.LocalDate[] selDate = { base.toLocalDate() };
        final int[] selTime = { base.getHour(), base.getMinute() };

        java.time.format.DateTimeFormatter btnDateFmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd");

        JButton dateBtn = pickerBtn(selDate[0].format(btnDateFmt));
        JButton timePickBtn = pickerBtn(String.format("%02d:%02d", selTime[0], selTime[1]));

        dateBtn.addActionListener(e ->
            AppUIManager.showDatePicker(dateBtn, selDate[0], date -> {
                selDate[0] = date;
                dateBtn.setText(date.format(btnDateFmt));
            })
        );
        timePickBtn.addActionListener(e ->
            AppUIManager.showTimePicker(timePickBtn, selTime[0], selTime[1], (h, m) -> {
                selTime[0] = h; selTime[1] = m;
                timePickBtn.setText(String.format("%02d:%02d", h, m));
            })
        );

        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        dateRow.setOpaque(false);
        dateRow.add(styledLabel("日期")); dateRow.add(dateBtn);

        JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        timeRow.setOpaque(false);
        timeRow.add(styledLabel("時間")); timeRow.add(timePickBtn);

        // dtPanel：不放在固定高度容器裡，直接控制 GridBag row 的可見性
        JPanel dtPanel = new JPanel();
        dtPanel.setLayout(new BoxLayout(dtPanel, BoxLayout.Y_AXIS));
        dtPanel.setOpaque(false);
        dtPanel.add(dateRow);
        dtPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        dtPanel.add(timeRow);
        dtPanel.setVisible(initHasDeadline);

        // ── GridBagLayout 內容面板 ──
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(14, 16, 10, 16));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;

        gc.gridy = 0; gc.insets = new Insets(0, 0, 4, 0);
        content.add(fieldLabel("標題"), gc);
        gc.gridy = 1; gc.insets = new Insets(0, 0, 12, 0);
        content.add(titleField, gc);
        gc.gridy = 2; gc.insets = new Insets(0, 0, 4, 0);
        content.add(fieldLabel("說明"), gc);
        gc.gridy = 3; gc.insets = new Insets(0, 0, 12, 0);
        content.add(descScroll, gc);
        gc.gridy = 4; gc.insets = new Insets(0, 0, initHasDeadline ? 6 : 0, 0);
        content.add(deadlineCheck, gc);
        gc.gridy = 5; gc.insets = new Insets(0, 0, 0, 0);
        content.add(dtPanel, gc);

        // checkbox 切換：只改 dtPanel 顯示，並縮排 dlg 高度
        deadlineCheck.addActionListener(e -> {
            boolean on = deadlineCheck.isSelected();
            dtPanel.setVisible(on);
            GridBagConstraints updGc = new GridBagConstraints();
            updGc.gridx = 0; updGc.gridy = 4; updGc.weightx = 1.0;
            updGc.fill = GridBagConstraints.HORIZONTAL;
            updGc.anchor = GridBagConstraints.WEST;
            updGc.insets = new Insets(0, 0, on ? 6 : 0, 0);
            ((GridBagLayout) content.getLayout()).setConstraints(deadlineCheck, updGc);
            content.revalidate();
            content.repaint();
            dlg.pack();
            dlg.setSize(400, dlg.getPreferredSize().height);
        });

        // ── 底部按鈕列 ──
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        btnRow.setBackground(new Color(0xFAF9F7));
        btnRow.setOpaque(true);
        btnRow.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));

        JButton cancelBtn = new JButton("取消");
        cancelBtn.setFont(AppFonts.BODY_SMALL);
        cancelBtn.setForeground(AppColors.TEXT_SECONDARY);
        cancelBtn.setBackground(AppColors.BG_TERTIARY);
        cancelBtn.setOpaque(true);
        cancelBtn.setBorder(new EmptyBorder(6, 16, 6, 16));
        cancelBtn.setFocusPainted(false);
        cancelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JButton okBtn = new JButton(isEdit ? "儲存變更" : "新增");
        okBtn.setFont(AppFonts.BODY_SMALL);
        okBtn.setBackground(AppColors.ACCENT);
        okBtn.setForeground(Color.WHITE);
        okBtn.setBorder(new EmptyBorder(6, 18, 6, 18));
        okBtn.setFocusPainted(false);
        okBtn.setOpaque(true);
        okBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btnRow.add(cancelBtn);
        btnRow.add(okBtn);

        root.add(header,  BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        root.add(btnRow,  BorderLayout.SOUTH);

        dlg.pack();
        dlg.setSize(400, dlg.getPreferredSize().height);
        dlg.setLocationRelativeTo(this);

        cancelBtn.addActionListener(e -> dlg.dispose());
        dlg.getRootPane().setDefaultButton(okBtn);

        okBtn.addActionListener(e -> {
            String titleVal = titleField.getText().trim();
            if (titleVal.isEmpty()) {
                titleField.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(AppColors.DANGER, 1, true),
                    new EmptyBorder(6, 10, 6, 10)));
                titleField.requestFocus();
                return;
            }

            String reminder = null;
            if (deadlineCheck.isSelected()) {
                reminder = String.format("%04d-%02d-%02d %02d:%02d",
                    selDate[0].getYear(), selDate[0].getMonthValue(), selDate[0].getDayOfMonth(),
                    selTime[0], selTime[1]);
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

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFonts.LABEL);
        l.setForeground(AppColors.TEXT_SECONDARY);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JLabel styledLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFonts.BODY_SMALL);
        l.setForeground(AppColors.TEXT_SECONDARY);
        return l;
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

    private static JButton pickerBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(AppFonts.BODY_SMALL);
        b.setForeground(AppColors.TEXT_PRIMARY);
        b.setBackground(Color.WHITE);
        b.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
            new EmptyBorder(5, 10, 5, 10)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setOpaque(true);
        return b;
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