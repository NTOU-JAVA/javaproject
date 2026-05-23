package ui;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;
import model.Reminder;
import model.TodoItem;
import service.CategoryManager;

/**
 * TodoPanel：代辦事項面板。
 * v0.5：加入分類 Tag 篩選列與新增/編輯時的分類選擇。
 */
public class TodoPanel extends JPanel {

    private static final DateTimeFormatter REMINDER_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final double DIALOG_MAX_HEIGHT_RATIO = 0.68;
    private static final int DIALOG_MIN_WIDTH = 460;
    private static final int DIALOG_MAX_WIDTH = 480;
    private static final int DIALOG_MIN_HEIGHT = 0;

    private final List<TodoItem>         todos;
    private final Runnable               saveCallback;
    private final CategoryManager        categoryManager;

    // 清單容器（BoxLayout 垂直排列）
    private final JPanel listContainer = new JPanel();
    { listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS)); }

    // 目前選取的分類篩選
    private String currentFilter = CategoryManager.ALL;

    public TodoPanel(List<TodoItem> todos, Runnable saveCallback, CategoryManager categoryManager) {
        this.todos           = todos;
        this.saveCallback    = saveCallback;
        this.categoryManager = categoryManager;

        setLayout(new BorderLayout(0, 0));
        setBackground(AppColors.BG_SECONDARY);

        // 頂部：導覽列 + Tag 列
        JPanel topArea = new JPanel(new BorderLayout());
        topArea.setOpaque(false);
        topArea.add(buildTopNav(), BorderLayout.NORTH);
        topArea.add(buildTagBar(), BorderLayout.SOUTH);
        add(topArea,         BorderLayout.NORTH);
        add(buildListArea(), BorderLayout.CENTER);
        add(buildHintBar(),  BorderLayout.SOUTH);

        // 分類刪除時，清空所有有此分類的代辦事項的分類欄位
        categoryManager.addRemoveListener(deletedCat -> {
            for (TodoItem t : todos) {
                if (deletedCat.equals(t.getCategory())) {
                    t.setCategory("");
                }
            }
            saveCallback.run();
        });

        // 分類重新命名時，同步更新事項的分類欄位與目前篩選器
        categoryManager.addRenameListener((oldName, newName) -> {
            for (TodoItem t : todos) {
                if (oldName.equals(t.getCategory())) {
                    t.setCategory(newName);
                }
            }
            if (oldName.equals(currentFilter)) {
                currentFilter = newName;
            }
            saveCallback.run();
        });

        // 分類清單變動時刷新清單顯示
        categoryManager.addListener(this::refreshList);

        refreshList();
    }

    /** 回傳底層 todos 清單（與 Main 共用同一個參考，供登入同步使用）。 */
    public List<TodoItem> getTodos() { return todos; }

    // ── 頂部列 ──────────────────────────────────────────────────────────────
    private JPanel buildTopNav() {
        JPanel nav = new JPanel(new BorderLayout());
        nav.setBackground(AppColors.BG_SECONDARY);
        nav.setBorder(new EmptyBorder(12, 16, 8, 16));

        JLabel title = new JLabel("代辦事項");
        title.setFont(AppFonts.TITLE_MEDIUM);
        title.setForeground(AppColors.TEXT_PRIMARY);

        JButton addBtn = new JButton("+ 新增代辦");
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

    // ── Tag 篩選列 ───────────────────────────────────────────────────────────
    private CategoryTagBar buildTagBar() {
        return new CategoryTagBar(categoryManager, filter -> {
            currentFilter = filter;
            refreshList();
        });
    }

    // ── 清單區 ──────────────────────────────────────────────────────────────
    private JScrollPane buildListArea() {
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setBackground(AppColors.BG_PRIMARY);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(AppColors.BG_PRIMARY);
        wrapper.add(listContainer, BorderLayout.NORTH);

        JScrollPane sp = new JScrollPane(wrapper);
        sp.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));
        // 1. 強制關閉水平滾動條，與 TodoPanel 保持一致
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getViewport().setBackground(AppColors.BG_PRIMARY);
        sp.getVerticalScrollBar().setUnitIncrement(20);
        
        // 2. 套用 Slim ScrollBar 樣式
        AppUIManager.applySlimScrollBar(sp);

        // 3. 監聽視窗縮放，動態調整內部公告列的寬度 (比照 TodoPanel 實現平滑自適應)
        sp.getViewport().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                int vpW = sp.getViewport().getWidth();
                if (vpW <= 0) return;
                for (Component c : listContainer.getComponents()) {
                    Dimension ps = c.getPreferredSize();
                    c.setPreferredSize(new Dimension(vpW, ps.height));
                }
                listContainer.revalidate();
                listContainer.repaint();
            }
        });

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
                g.setColor(AppColors.BORDER_DEFAULT);
                g.drawLine(0, getHeight()-1, getWidth(), getHeight()-1);
            }
        };
        row.setOpaque(true);
        row.setBackground(rowIndex % 2 == 0 ? AppColors.BG_PRIMARY : new Color(0xFBFBF9));
        row.setMinimumSize(new Dimension(0, 52));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.putClientProperty("todoId", item.getId());

        // 左側：可點擊的圓圈
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
                circle.setForeground(item.isCompleted() ? AppColors.SUCCESS.darker() : AppColors.ACCENT);
            }
            @Override public void mouseExited(MouseEvent e) {
                circle.setForeground(item.isCompleted() ? AppColors.SUCCESS : AppColors.TEXT_TERTIARY);
            }
        });

        // 中間：標題 + 說明 + 期限 + 分類標籤
        JPanel center = new JPanel() {
            @Override public Dimension getMaximumSize() {
                Dimension ps = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, ps.height);
            }
        };
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);
        center.setBorder(new EmptyBorder(8, 0, 8, 0));

        JTextArea titleLbl = new JTextArea(item.getTitle());
        titleLbl.setFont(AppFonts.BODY_SMALL);
        titleLbl.setForeground(item.isCompleted() ? new Color(0xA8A7A4) : AppColors.TEXT_PRIMARY);
        titleLbl.setEditable(false);
        titleLbl.setFocusable(false);
        titleLbl.setLineWrap(true);
        titleLbl.setWrapStyleWord(true);
        titleLbl.setOpaque(false);
        titleLbl.setBorder(null);
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        titleLbl.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e)  { row.dispatchEvent(SwingUtilities.convertMouseEvent(titleLbl, e, row)); }
            @Override public void mouseEntered(MouseEvent e)  { row.dispatchEvent(SwingUtilities.convertMouseEvent(titleLbl, e, row)); }
            @Override public void mouseExited(MouseEvent e)   { row.dispatchEvent(SwingUtilities.convertMouseEvent(titleLbl, e, row)); }
            @Override public void mousePressed(MouseEvent e)  { row.dispatchEvent(SwingUtilities.convertMouseEvent(titleLbl, e, row)); }
            @Override public void mouseReleased(MouseEvent e) { row.dispatchEvent(SwingUtilities.convertMouseEvent(titleLbl, e, row)); }
        });

        String rt   = item.getDeadlineTime();
        String desc = item.getDescription();
        center.add(titleLbl);

        if (!item.isCompleted()) {
            if (!desc.isEmpty()) {
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
                String timePrefix = "[期限] ";
                try {
                    LocalDateTime target = LocalDateTime.parse(rt, REMINDER_FMT);
                    long mins = java.time.Duration.between(LocalDateTime.now(), target).toMinutes();
                    if (mins < 0) {
                        timeColor = AppColors.DANGER;
                        timePrefix = "[逾期] ";
                    } else if (mins <= 1440) {
                        timeColor = AppColors.DANGER;
                    }
                } catch (Exception ignored) {}
                JLabel timeLbl = new JLabel(timePrefix + rt.substring(5));
                timeLbl.setFont(AppFonts.CAPTION);
                timeLbl.setForeground(timeColor);
                timeLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
                center.add(Box.createRigidArea(new Dimension(0, 1)));
                center.add(timeLbl);

                LocalDateTime deadline = parseDeadline(rt);
                if (deadline != null && ReminderChips.hasUpcoming(item.getReminders(), deadline)) {
                    center.add(Box.createRigidArea(new Dimension(0, 3)));
                    center.add(ReminderChips.build(item.getReminders(), deadline, 2));
                }
            }
            // 分類標籤（若有）
            if (!item.getCategory().isEmpty()) {
                center.add(Box.createRigidArea(new Dimension(0, 2)));
                center.add(buildInlineCategoryTag(item.getCategory()));
            }
        }

        // 右側：CardLayout 切換正常/確認刪除
        CardLayout actionsCard = new CardLayout();
        JPanel actions = new JPanel(actionsCard);
        actions.setOpaque(false);

        JButton delBtn  = actionBtn("刪除", AppColors.DANGER_LIGHT, AppColors.DANGER);
        JButton editBtn = actionBtn("編輯", AppColors.BG_TERTIARY,  AppColors.TEXT_PRIMARY);
        JPanel normalPane = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 8));
        normalPane.setOpaque(false);
        normalPane.add(delBtn);
        normalPane.add(editBtn);

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
            todos.remove(item);
            refreshList();
            saveCallback.run();
        });

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

    /** 小型分類標籤（顯示在項目內容下方） */
    private JLabel buildInlineCategoryTag(String category) {
        JLabel tag = new JLabel(category) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColors.ACCENT_LIGHT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        tag.setFont(AppFonts.CAPTION);
        tag.setForeground(AppColors.ACCENT);
        tag.setBorder(new EmptyBorder(1, 7, 1, 7));
        tag.setOpaque(false);
        tag.setAlignmentX(Component.LEFT_ALIGNMENT);
        return tag;
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
        dlg.setBackground(new Color(0xF0F0F0));

        Color headerBg = AppColors.ACCENT_LIGHT;
        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int shadow = 9, W = getWidth() - shadow, H = getHeight() - shadow, R = 14;
                for (int i = shadow; i >= 1; i--) {
                    g2.setColor(new Color(0, 0, 0, 2 + i));
                    g2.fillRoundRect(i / 2, i / 2 + 1, getWidth() - i - 1, getHeight() - i - 1, R, R);
                }
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, W, H, R, R);
                JPanel hdr = (JPanel) getComponent(0);
                int hh = hdr.getHeight();
                g2.setColor(headerBg);
                g2.fillRoundRect(0, 0, W, R + hh, R, R);
                g2.fillRect(0, R, W, hh - R);
                g2.setColor(AppColors.BORDER_HOVER);
                g2.setStroke(new java.awt.BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, W-1, H-1, R, R);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(0, 0, 9, 9));
        dlg.add(root);

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

        // ── 分類下拉 ──
        java.util.List<String> catOptions = new java.util.ArrayList<>();
        catOptions.add("（未分類）");
        catOptions.addAll(categoryManager.getCategories());
        JComboBox<String> catCombo = new JComboBox<>(catOptions.toArray(new String[0]));
        catCombo.setFont(AppFonts.BODY_SMALL);
        catCombo.setBackground(Color.WHITE);
        catCombo.setForeground(AppColors.TEXT_PRIMARY);
        catCombo.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
            new EmptyBorder(2, 4, 2, 4)));
        SchedulePanel.applyComboStyle(catCombo);
        // 預選
        if (isEdit && !editItem.getCategory().isEmpty()) {
            catCombo.setSelectedItem(editItem.getCategory());
        }

        // ── 截止時間 ──
        LocalDateTime base = nextFullHour();
        if (isEdit && editItem.getDeadlineTime() != null) {
            try { base = LocalDateTime.parse(editItem.getDeadlineTime(), REMINDER_FMT); }
            catch (DateTimeParseException ignored) {}
        }
        boolean initHasDeadline = isEdit && editItem.getDeadlineTime() != null;
        boolean initCanRemind = initHasDeadline && base.isAfter(LocalDateTime.now());

        JCheckBox deadlineCheck = new JCheckBox("設定截止提醒時間", initHasDeadline);
        deadlineCheck.setFont(AppFonts.BODY_SMALL);
        deadlineCheck.setForeground(AppColors.TEXT_SECONDARY);
        deadlineCheck.setOpaque(false);

        final java.time.LocalDate[] selDate = { base.toLocalDate() };
        final int[] selTime = { base.getHour(), base.getMinute() };

        java.time.format.DateTimeFormatter btnDateFmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd");

        final Runnable[] resizeDialog = new Runnable[1];
        final Runnable[] refreshReminderAvailability = new Runnable[1];
        JButton dateBtn     = pickerBtn(selDate[0].format(btnDateFmt));
        JButton timePickBtn = pickerBtn(String.format("%02d:%02d", selTime[0], selTime[1]));

        dateBtn.addActionListener(e ->
            AppUIManager.showDatePicker(dateBtn, selDate[0], date -> {
                selDate[0] = date;
                dateBtn.setText(date.format(btnDateFmt));
                if (refreshReminderAvailability[0] != null) refreshReminderAvailability[0].run();
            })
        );
        timePickBtn.addActionListener(e ->
            AppUIManager.showTimePicker(timePickBtn, selTime[0], selTime[1], (h, m) -> {
                selTime[0] = h; selTime[1] = m;
                timePickBtn.setText(String.format("%02d:%02d", h, m));
                if (refreshReminderAvailability[0] != null) refreshReminderAvailability[0].run();
            })
        );

        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        dateRow.setOpaque(false);
        dateRow.add(styledLabel("日期")); dateRow.add(dateBtn);

        JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        timeRow.setOpaque(false);
        timeRow.add(styledLabel("時間")); timeRow.add(timePickBtn);

        JPanel dtPanel = new JPanel();
        dtPanel.setLayout(new BoxLayout(dtPanel, BoxLayout.Y_AXIS));
        dtPanel.setOpaque(false);
        dtPanel.add(dateRow);
        dtPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        dtPanel.add(timeRow);
        dtPanel.setVisible(initHasDeadline);

        ReminderEditorPanel reminderPanel = new ReminderEditorPanel(dlg,
                isEdit ? editItem.getReminders() : java.util.Collections.emptyList(),
                base,
                () -> {
                    if (resizeDialog[0] != null) resizeDialog[0].run();
                });
        reminderPanel.setVisible(initCanRemind);

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(14, 16, 10, 16));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;

        gc.gridy = 0; gc.insets = new Insets(0, 0, 4, 0);  content.add(fieldLabel("標題"), gc);
        gc.gridy = 1; gc.insets = new Insets(0, 0, 12, 0); content.add(titleField, gc);
        gc.gridy = 2; gc.insets = new Insets(0, 0, 4, 0);  content.add(fieldLabel("說明"), gc);
        gc.gridy = 3; gc.insets = new Insets(0, 0, 12, 0); content.add(descScroll, gc);
        gc.gridy = 4; gc.insets = new Insets(0, 0, 4, 0);  content.add(fieldLabel("分類"), gc);
        gc.gridy = 5; gc.insets = new Insets(0, 0, 12, 0); content.add(catCombo, gc);
        gc.gridy = 6; gc.insets = new Insets(0, 0, initHasDeadline ? 6 : 0, 0);
        content.add(deadlineCheck, gc);
        gc.gridy = 7; gc.insets = new Insets(0, 0, initHasDeadline ? 10 : 0, 0);  content.add(dtPanel, gc);
        gc.gridy = 8; gc.insets = new Insets(0, 0, 4, 0);  content.add(fieldLabel("提醒"), gc);
        gc.gridy = 9; gc.insets = new Insets(0, 0, 0, 0);  content.add(reminderPanel, gc);
        content.getComponent(8).setVisible(initCanRemind);
        gc.gridy = 10; gc.weighty = 1.0; gc.fill = GridBagConstraints.BOTH;
        gc.insets = new Insets(0, 0, 0, 0);
        content.add(Box.createVerticalGlue(), gc);

        refreshReminderAvailability[0] = () -> {
            boolean hasDeadline = deadlineCheck.isSelected();
            LocalDateTime selectedDeadline = LocalDateTime.of(selDate[0],
                    java.time.LocalTime.of(selTime[0], selTime[1]));
            boolean canRemind = hasDeadline && selectedDeadline.isAfter(LocalDateTime.now());
            reminderPanel.setVisible(canRemind);
            content.getComponent(8).setVisible(canRemind);
            content.revalidate();
            content.repaint();
            if (resizeDialog[0] != null) resizeDialog[0].run();
        };

        deadlineCheck.addActionListener(e -> {
            boolean on = deadlineCheck.isSelected();
            dtPanel.setVisible(on);
            GridBagConstraints updGc = new GridBagConstraints();
            updGc.gridx = 0; updGc.gridy = 6; updGc.weightx = 1.0;
            updGc.fill = GridBagConstraints.HORIZONTAL;
            updGc.anchor = GridBagConstraints.WEST;
            updGc.insets = new Insets(0, 0, on ? 6 : 0, 0);
            ((GridBagLayout) content.getLayout()).setConstraints(deadlineCheck, updGc);
            refreshReminderAvailability[0].run();
        });

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

        JScrollPane contentScroll = new JScrollPane(content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        contentScroll.setBorder(null);
        contentScroll.setOpaque(false);
        contentScroll.getViewport().setOpaque(false);
        contentScroll.getVerticalScrollBar().setUnitIncrement(16);
        AppUIManager.applySlimScrollBar(contentScroll);

        root.add(header,  BorderLayout.NORTH);
        root.add(contentScroll, BorderLayout.CENTER);
        root.add(btnRow,  BorderLayout.SOUTH);

        dlg.pack();
        resizeDialog[0] = () -> {
            dlg.pack();
            int maxH = (int)(GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getMaximumWindowBounds().height * DIALOG_MAX_HEIGHT_RATIO);
            int width = Math.min(Math.max(DIALOG_MIN_WIDTH, dlg.getPreferredSize().width), DIALOG_MAX_WIDTH);
            int height = Math.min(Math.max(DIALOG_MIN_HEIGHT, dlg.getPreferredSize().height), maxH);
            dlg.setSize(width, height);
        };
        resizeDialog[0].run();
        AppUIManager.applyRoundedWindowShape(dlg, 16);
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
            boolean canSaveReminders = reminder != null
                    && LocalDateTime.of(selDate[0], java.time.LocalTime.of(selTime[0], selTime[1]))
                            .isAfter(LocalDateTime.now());
            LocalDateTime deadlineValue = reminder != null
                    ? LocalDateTime.of(selDate[0], java.time.LocalTime.of(selTime[0], selTime[1]))
                    : null;
            // 取得分類
            String selectedCat = (String) catCombo.getSelectedItem();
            if ("（未分類）".equals(selectedCat)) selectedCat = "";

            if (isEdit) {
                editItem.setTitle(titleVal);
                editItem.setDescription(descArea.getText().trim());
                editItem.setDeadlineTime(reminder);
                editItem.setReminders(canSaveReminders
                        ? reminderPanel.getReminders(deadlineValue) : java.util.Collections.emptyList());
                editItem.setCategory(selectedCat);
            } else {
                int nextId = todos.isEmpty() ? 1
                        : todos.stream().mapToInt(TodoItem::getId).max().orElse(0) + 1;
                TodoItem item = new TodoItem(nextId, titleVal,
                        descArea.getText().trim(), reminder);
                item.setDeadlineTime(reminder);
                item.setReminders(canSaveReminders
                        ? reminderPanel.getReminders(deadlineValue) : java.util.Collections.emptyList());
                item.setCategory(selectedCat);
                todos.add(item);
            }
            dlg.dispose();
            refreshList();
            saveCallback.run();
        });

        dlg.setVisible(true);
    }

    private static void liftDialogIntoView(JDialog dlg) {
        Rectangle bounds = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int topPadding = 8;
        int bottomPadding = 16;
        int y = bounds.y + topPadding;
        if (y + dlg.getHeight() > bounds.y + bounds.height - bottomPadding) {
            y = bounds.y + bounds.height - bottomPadding - dlg.getHeight();
        }
        dlg.setLocation(dlg.getX(), Math.max(bounds.y + topPadding, y));
    }

    private static LocalDateTime nextFullHour() {
        LocalDateTime now = LocalDateTime.now();
        return now.withMinute(0).withSecond(0).withNano(0).plusHours(1);
    }

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFonts.BODY_SMALL);
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

    // ── 更新清單顯示（套用篩選） ──────────────────────────────────────────────
    public void refreshList() {
        if (clearOverdueReminders()) {
            saveCallback.run();
        }
        todos.sort((a, b) -> {
            if (a.isCompleted() != b.isCompleted()) return a.isCompleted() ? 1 : -1;
            String ta = a.getDeadlineTime(), tb = b.getDeadlineTime();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return ta.compareTo(tb);
        });

        listContainer.removeAll();

        // 篩選
        java.util.List<TodoItem> filtered = new java.util.ArrayList<>();
        for (TodoItem t : todos) {
            if (CategoryManager.ALL.equals(currentFilter)) {
                filtered.add(t);
            } else if (currentFilter.equals(t.getCategory())) {
                filtered.add(t);
            }
        }

        for (int i = 0; i < filtered.size(); i++) {
            listContainer.add(buildItemRow(filtered.get(i), i));
        }

        if (filtered.isEmpty()) {
            String msg = CategoryManager.ALL.equals(currentFilter)
                ? "目前沒有代辦事項，點擊右上角新增吧！"
                : "此分類下沒有代辦事項。";
            JLabel empty = new JLabel(msg, SwingConstants.CENTER);
            empty.setFont(AppFonts.BODY_SMALL);
            empty.setForeground(AppColors.TEXT_TERTIARY);
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            empty.setBorder(new EmptyBorder(40, 0, 0, 0));
            listContainer.add(empty);
        }
        listContainer.revalidate();
        listContainer.repaint();

        SwingUtilities.invokeLater(() -> {
            Container vp = listContainer.getParent();
            if (vp != null && vp.getParent() instanceof JViewport) {
                int vpW = ((JViewport) vp.getParent()).getWidth();
                if (vpW > 0) {
                    for (Component c : listContainer.getComponents()) {
                        Dimension ps = c.getPreferredSize();
                        c.setPreferredSize(new Dimension(vpW, ps.height));
                    }
                    listContainer.revalidate();
                    listContainer.repaint();
                }
            }
        });
    }

    private boolean clearOverdueReminders() {
        boolean changed = false;
        LocalDateTime now = LocalDateTime.now();
        for (TodoItem todo : todos) {
            if (todo.getReminders().isEmpty()) continue;
            LocalDateTime deadline = parseDeadline(todo.getDeadlineTime());
            if (deadline != null && !deadline.isAfter(now)) {
                todo.setReminders(java.util.Collections.emptyList());
                changed = true;
            }
        }
        return changed;
    }

    public void revealTodo(int todoId) {
        currentFilter = CategoryManager.ALL;
        refreshList();
        SwingUtilities.invokeLater(() -> {
            for (Component component : listContainer.getComponents()) {
                if (Integer.valueOf(todoId).equals(((JComponent) component).getClientProperty("todoId"))) {
                    ((JComponent) component).scrollRectToVisible(component.getBounds());
                    component.setBackground(AppColors.ACCENT_LIGHT);
                    component.repaint();
                    Timer timer = new Timer(1200, e -> {
                        refreshList();
                        ((Timer) e.getSource()).stop();
                    });
                    timer.setRepeats(false);
                    timer.start();
                    return;
                }
            }
        });
    }

    private static LocalDateTime parseDeadline(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value, REMINDER_FMT);
        } catch (DateTimeParseException ex) {
            return null;
        }
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
}

