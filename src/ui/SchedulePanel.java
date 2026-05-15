package ui;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import model.Course;
import model.Schedule;
import service.PdfScheduleImporter;

/**
 * SchedulePanel：課表功能主面板。
 * 更新：
 *  - 移除冗餘 gridTitleLabel
 *  - 新增 professor 到課程編輯 Dialog
 *  - 課程卡顯示：課程名稱 → 開課系所 → 開課年班 → 教室
 *  - 懸浮視窗顯示所有欄位，[] 內文字改為與內容相同色
 *  - 課程顏色選擇功能（備註上方）
 *  - 星期/節次改為多時段區塊設計
 *  - 統一自訂下拉選單樣式
 *  - RWD 響應式修復
 *  - 重新命名課表 Dialog 改為原名稱（唯讀）＋新名稱格式
 */
public class SchedulePanel extends JPanel {

    private static final String[] PERIOD_TIMES = {
        "第0節 06:20", "第1節 08:20", "第2節 09:20", "第3節 10:20",
        "第4節 11:15", "第5節 12:10", "第6節 13:10", "第7節 14:10",
        "第8節 15:10", "第9節 16:05", "第10節 17:30", "第11節 18:30",
        "第12節 19:25", "第13節 20:20", "第14節 21:15"
    };
    private static final String[] DAY_NAMES  = {"一", "二", "三", "四", "五", "六", "日"};
    private static final int[]    COL_TO_DAY = {1, 2, 3, 4, 5, 6, 7};

    // 預設色票（bg, border/accent）
    static final Color[][] PRESET_COLORS = {
        { new Color(0xEEF2FF), new Color(0x3B5BDB) }, // 靛藍
        { new Color(0xF0FFF4), new Color(0x2F9E44) }, // 綠
        { new Color(0xFFF9DB), new Color(0xE67700) }, // 黃橙
        { new Color(0xFFF5F5), new Color(0xC92A2A) }, // 紅
        { new Color(0xF3F0FF), new Color(0x7048E8) }, // 紫
        { new Color(0xE3FAFC), new Color(0x0C8599) }, // 青
        { new Color(0xFFF0F6), new Color(0xC2255C) }, // 玫瑰
        { new Color(0xE8F5E9), new Color(0x388E3C) }, // 深綠
        { new Color(0xFFF3E0), new Color(0xF57C00) }, // 橙
        { new Color(0xE1F5FE), new Color(0x0288D1) }, // 天藍
        { new Color(0xFCE4EC), new Color(0xAD1457) }, // 桃紅
        { new Color(0xF3E5F5), new Color(0x7B1FA2) }, // 深紫
    };

    private static final int ROW_HEIGHT = 64;

    private final List<Schedule> schedules;
    private final Runnable       saveCallback;
    private Schedule             activeSchedule = null;

    private final JPanel gridPanel = new JPanel();

    private JComboBox<Schedule> scheduleCombo;
    private JButton             deleteBtn;
    private JButton             renameBtn;
    private JButton             addCourseBtn;
    private JButton             importPdfBtn;
    private JPanel              topControlPanel;
    private CardLayout          topCardLayout;

    private JPanel currentPopover = null;
    private boolean comboUpdating = false;
    private ComponentListener windowResizeListener = null;

    public SchedulePanel(List<Schedule> schedules, Runnable saveCallback) {
        this.schedules    = schedules;
        this.saveCallback = saveCallback;

        for (Schedule s : schedules) {
            if (s.isActive()) { activeSchedule = s; break; }
        }
        if (activeSchedule == null && !schedules.isEmpty()) {
            activeSchedule = schedules.get(0);
            activeSchedule.setActive(true);
        }

        setLayout(new BorderLayout(0, 0));
        setBackground(AppColors.BG_SECONDARY);

        add(buildTopNav(),   BorderLayout.NORTH);
        add(buildGridPane(), BorderLayout.CENTER);

        refreshGrid();
    }

    @Override public void addNotify() {
        super.addNotify();
        Window w = SwingUtilities.getWindowAncestor(this);
        if (w != null) {
            windowResizeListener = new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) { closePopover(); }
            };
            w.addComponentListener(windowResizeListener);
        }
    }

    @Override public void removeNotify() {
        Window w = SwingUtilities.getWindowAncestor(this);
        if (w != null && windowResizeListener != null) {
            w.removeComponentListener(windowResizeListener);
            windowResizeListener = null;
        }
        super.removeNotify();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 頂部列
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel buildTopNav() {
        JPanel nav = new JPanel(new BorderLayout());
        nav.setBackground(AppColors.BG_SECONDARY);
        nav.setBorder(new EmptyBorder(10, 16, 8, 16));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);

        JLabel myLabel = new JLabel("我的課表：");
        myLabel.setFont(AppFonts.BODY_SMALL);
        myLabel.setForeground(AppColors.TEXT_SECONDARY);

        scheduleCombo = createStyledComboBox();
        scheduleCombo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(
                    JList<?> list, Object value, int idx, boolean sel, boolean foc) {
                super.getListCellRendererComponent(list, value, idx, sel, foc);
                setText(value instanceof Schedule ? ((Schedule) value).getName() : "（無課表）");
                setFont(AppFonts.BODY_SMALL);
                setBorder(new EmptyBorder(6, 10, 6, 10));
                setBackground(sel ? AppColors.ACCENT_LIGHT : Color.WHITE);
                setForeground(sel ? AppColors.ACCENT : AppColors.TEXT_PRIMARY);
                return this;
            }
        });
        rebuildCombo();
        scheduleCombo.addActionListener(e -> {
            if (comboUpdating) return;
            Schedule sel = (Schedule) scheduleCombo.getSelectedItem();
            if (sel != null && sel != activeSchedule) switchSchedule(sel);
        });

        JButton addScheduleBtn = topBtn("新增課表", AppColors.ACCENT_LIGHT, AppColors.ACCENT);
        addScheduleBtn.addActionListener(e -> showScheduleDialog(null));

        topCardLayout   = new CardLayout();
        topControlPanel = new JPanel(topCardLayout);
        topControlPanel.setOpaque(false);

        JPanel normalCtrl = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        normalCtrl.setOpaque(false);
        deleteBtn = topBtn("刪除",    AppColors.BG_TERTIARY, AppColors.DANGER);
        renameBtn = topBtn("重新命名", AppColors.BG_TERTIARY, AppColors.TEXT_PRIMARY);
        deleteBtn.addActionListener(e -> {
            if (activeSchedule != null) topCardLayout.show(topControlPanel, "confirm");
        });
        renameBtn.addActionListener(e -> {
            if (activeSchedule != null) showScheduleDialog(activeSchedule);
        });
        normalCtrl.add(deleteBtn);
        normalCtrl.add(renameBtn);

        JPanel confirmCtrl = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        confirmCtrl.setOpaque(false);
        JButton cancelDelBtn  = topBtn("取消",    AppColors.BG_TERTIARY, AppColors.TEXT_SECONDARY);
        JButton confirmDelBtn = topBtn("確認刪除", AppColors.DANGER,      Color.WHITE);
        cancelDelBtn.addActionListener(e -> topCardLayout.show(topControlPanel, "normal"));
        confirmDelBtn.addActionListener(e -> {
            if (activeSchedule != null) {
                schedules.remove(activeSchedule);
                activeSchedule = schedules.isEmpty() ? null : schedules.get(0);
                if (activeSchedule != null) activeSchedule.setActive(true);
                topCardLayout.show(topControlPanel, "normal");
                rebuildCombo();
                refreshScheduleButtons();
                refreshGrid();
                saveCallback.run();
            }
        });
        confirmCtrl.add(cancelDelBtn);
        confirmCtrl.add(confirmDelBtn);

        topControlPanel.add(normalCtrl,  "normal");
        topControlPanel.add(confirmCtrl, "confirm");
        topCardLayout.show(topControlPanel, "normal");

        left.add(myLabel);
        left.add(scheduleCombo);
        left.add(addScheduleBtn);
        left.add(topControlPanel);

        importPdfBtn = new JButton("匯入 PDF");
        importPdfBtn.setFont(AppFonts.BODY_SMALL);
        importPdfBtn.setBackground(AppColors.BG_TERTIARY);
        importPdfBtn.setForeground(AppColors.TEXT_PRIMARY);
        importPdfBtn.setBorder(new EmptyBorder(7, 14, 7, 14));
        importPdfBtn.setFocusPainted(false);
        importPdfBtn.setOpaque(true);
        importPdfBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        importPdfBtn.addActionListener(e -> importScheduleFromPdf());

        addCourseBtn = new JButton("+ 新增課程");
        addCourseBtn.setFont(AppFonts.BODY_SMALL);
        addCourseBtn.setBackground(AppColors.ACCENT);
        addCourseBtn.setForeground(Color.WHITE);
        addCourseBtn.setBorder(new EmptyBorder(7, 16, 7, 16));
        addCourseBtn.setFocusPainted(false);
        addCourseBtn.setOpaque(true);
        addCourseBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addCourseBtn.addActionListener(e -> {
            if (activeSchedule == null) {
                JOptionPane.showMessageDialog(this, "請先新增或選擇一份課表。", "提示",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            showCourseDialog(null);
        });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(importPdfBtn);
        right.add(addCourseBtn);

        nav.add(left,  BorderLayout.WEST);
        nav.add(right, BorderLayout.EAST);
        refreshScheduleButtons();
        return nav;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 自訂下拉選單樣式
    // ══════════════════════════════════════════════════════════════════════════
    @SuppressWarnings("unchecked")
    private <T> JComboBox<T> createStyledComboBox() {
        JComboBox<T> combo = new JComboBox<T>() {
            @Override public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = Math.max(d.width, 120);
                return d;
            }
        };
        applyComboStyle(combo);
        return combo;
    }

    static void applyComboStyle(JComboBox<?> combo) {
        combo.setFont(AppFonts.BODY_SMALL);
        combo.setBackground(Color.WHITE);
        combo.setForeground(AppColors.TEXT_PRIMARY);
        combo.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
            new EmptyBorder(2, 4, 2, 4)));
        combo.setFocusable(false);
        combo.setUI(new StyledComboBoxUI());
    }

    static class StyledComboBoxUI extends BasicComboBoxUI {
        @Override
        protected JButton createArrowButton() {
            JButton btn = new JButton() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getBackground());
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    int cx = getWidth() / 2;
                    int cy = getHeight() / 2;
                    int[] xp = { cx - 4, cx + 4, cx };
                    int[] yp = { cy - 2, cy - 2, cy + 3 };
                    g2.setColor(AppColors.TEXT_SECONDARY);
                    g2.fillPolygon(xp, yp, 3);
                    g2.dispose();
                }
            };
            btn.setBackground(Color.WHITE);
            btn.setBorder(new EmptyBorder(0, 4, 0, 6));
            btn.setFocusPainted(false);
            btn.setContentAreaFilled(false);
            btn.setOpaque(true);
            return btn;
        }

        @Override
        protected ComboPopup createPopup() {
            BasicComboPopup popup = new BasicComboPopup(comboBox) {
                @Override
                protected JScrollPane createScroller() {
                    JScrollPane sp = super.createScroller();
                    AppUIManager.applySlimScrollBar(sp);
                    return sp;
                }
                @Override
                protected void configureList() {
                    super.configureList();
                    list.setBackground(Color.WHITE);
                    list.setSelectionBackground(AppColors.ACCENT_LIGHT);
                    list.setSelectionForeground(AppColors.ACCENT);
                    list.setFont(AppFonts.BODY_SMALL);
                    list.setBorder(new EmptyBorder(4, 0, 4, 0));
                }
                @Override
                protected void configureScroller() {
                    super.configureScroller();
                    scroller.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
                        new EmptyBorder(0, 0, 0, 0)));
                }
            };
            popup.setBorder(new LineBorder(AppColors.BORDER_DEFAULT, 1, true));
            return popup;
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
            g.setColor(Color.WHITE);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }
    }

    private JButton topBtn(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setFont(AppFonts.BODY_SMALL);
        b.setBackground(bg);
        b.setForeground(fg);
        b.setBorder(new EmptyBorder(5, 10, 5, 10));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void rebuildCombo() {
        comboUpdating = true;
        scheduleCombo.removeAllItems();
        for (Schedule s : schedules) scheduleCombo.addItem(s);
        if (activeSchedule != null) scheduleCombo.setSelectedItem(activeSchedule);
        comboUpdating = false;
    }

    private void importScheduleFromPdf() {
        File selectedFile = showPdfFileChooserDialog();
        if (selectedFile == null) return;

        int nextId = schedules.isEmpty() ? 1
                : schedules.stream().mapToInt(Schedule::getId).max().orElse(0) + 1;

        try {
            Schedule imported = new PdfScheduleImporter().importSchedule(selectedFile, nextId);
            schedules.add(imported);
            switchSchedule(imported);
            saveCallback.run();
            showImportResultDialog(
                    "匯入完成",
                    "已建立新課表「" + imported.getName() + "」。",
                    "共匯入 " + imported.getCourses().size() + " 門課程。",
                    AppColors.ACCENT);
        } catch (Exception ex) {
            showImportResultDialog(
                    "無法匯入課表",
                    "PDF 解析時遇到問題。",
                    ex.getMessage(),
                    AppColors.DANGER);
        }
    }

    private File showPdfFileChooserDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(owner, "", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = buildFloatingRoot(dlg, "匯入課表 PDF", AppColors.ACCENT);

        JFileChooser chooser = new JFileChooser();
        chooser.setControlButtonsAreShown(false);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF 檔案", "pdf"));
        styleFileChooser(chooser);

        JPanel chooserWrap = new JPanel(new BorderLayout());
        chooserWrap.setOpaque(false);
        chooserWrap.setBorder(new EmptyBorder(12, 14, 12, 14));
        chooserWrap.add(chooser, BorderLayout.CENTER);

        final File[] selected = { null };
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        btnRow.setBackground(new Color(0xFAF9F7));
        btnRow.setOpaque(true);
        btnRow.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));

        JButton cancelBtn = dialogButton("取消", AppColors.BG_TERTIARY, AppColors.TEXT_SECONDARY);
        JButton importBtn = dialogButton("匯入", AppColors.ACCENT, Color.WHITE);
        cancelBtn.addActionListener(e -> dlg.dispose());
        Runnable approveSelection = () -> {
            File file = chooser.getSelectedFile();
            if (file == null) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }
            selected[0] = file;
            dlg.dispose();
        };
        importBtn.addActionListener(e -> approveSelection.run());
        chooser.addActionListener(e -> {
            if (JFileChooser.APPROVE_SELECTION.equals(e.getActionCommand())) {
                approveSelection.run();
            } else if (JFileChooser.CANCEL_SELECTION.equals(e.getActionCommand())) {
                dlg.dispose();
            }
        });
        btnRow.add(cancelBtn);
        btnRow.add(importBtn);

        root.add(chooserWrap, BorderLayout.CENTER);
        root.add(btnRow, BorderLayout.SOUTH);
        dlg.getRootPane().setDefaultButton(importBtn);
        dlg.pack();
        dlg.setSize(Math.max(760, dlg.getWidth()), Math.max(500, dlg.getHeight()));
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
        return selected[0];
    }

    private void styleFileChooser(JFileChooser chooser) {
        chooser.setBackground(AppColors.BG_SECONDARY);
        chooser.setForeground(AppColors.TEXT_PRIMARY);
        chooser.setFont(AppFonts.BODY_SMALL);
        chooser.setBorder(new EmptyBorder(8, 8, 8, 8));
        styleChooserTree(chooser);
    }

    private void styleChooserTree(Component component) {
        component.setFont(AppFonts.BODY_SMALL);
        if (component instanceof JPanel panel) {
            panel.setBackground(AppColors.BG_SECONDARY);
        } else if (component instanceof JLabel label) {
            label.setForeground(AppColors.TEXT_SECONDARY);
        } else if (component instanceof JButton button) {
            button.setFocusPainted(false);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            String text = button.getText();
            if (text == null || text.isBlank()) {
                button.setBorder(new EmptyBorder(4, 5, 4, 5));
                button.setContentAreaFilled(false);
                button.setForeground(AppColors.TEXT_SECONDARY);
            } else {
                boolean approve = "匯入".equals(text);
                button.setBorder(new EmptyBorder(5, 12, 5, 12));
                button.setBackground(approve ? AppColors.ACCENT : AppColors.BG_TERTIARY);
                button.setForeground(approve ? Color.WHITE : AppColors.TEXT_PRIMARY);
                button.setOpaque(true);
            }
        } else if (component instanceof JTextField field) {
            field.setBackground(Color.WHITE);
            field.setForeground(AppColors.TEXT_PRIMARY);
            field.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
                    new EmptyBorder(5, 8, 5, 8)));
        } else if (component instanceof JList<?> list) {
            list.setBackground(Color.WHITE);
            list.setForeground(AppColors.TEXT_PRIMARY);
            list.setSelectionBackground(AppColors.ACCENT_LIGHT);
            list.setSelectionForeground(AppColors.ACCENT);
        } else if (component instanceof JTable table) {
            table.setBackground(Color.WHITE);
            table.setForeground(AppColors.TEXT_PRIMARY);
            table.setSelectionBackground(AppColors.ACCENT_LIGHT);
            table.setSelectionForeground(AppColors.ACCENT);
        } else if (component instanceof JComboBox<?> combo) {
            applyComboStyle(combo);
        } else if (component instanceof JScrollPane scrollPane) {
            scrollPane.setBorder(new LineBorder(AppColors.BORDER_DEFAULT, 1, true));
            AppUIManager.applySlimScrollBar(scrollPane);
        }

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                styleChooserTree(child);
            }
        }
    }

    private void refreshScheduleButtons() {
        boolean has = activeSchedule != null;
        if (deleteBtn    != null) deleteBtn.setEnabled(has);
        if (renameBtn    != null) renameBtn.setEnabled(has);
        if (addCourseBtn != null) {
            addCourseBtn.setEnabled(has);
            addCourseBtn.setBackground(has ? AppColors.ACCENT : AppColors.BG_TERTIARY);
            addCourseBtn.setForeground(has ? Color.WHITE : AppColors.TEXT_TERTIARY);
            addCourseBtn.setCursor(Cursor.getPredefinedCursor(
                    has ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 課表格子區
    // ══════════════════════════════════════════════════════════════════════════
    private JScrollPane gridScrollPane;

    private JPanel buildGridPane() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.setBackground(AppColors.BG_SECONDARY);
        wrapper.setBorder(new EmptyBorder(0, 16, 16, 16));

        JPanel card = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int W = getWidth() - 7, H = getHeight() - 7, R = 12;
                g2.setColor(new Color(0, 0, 0, 18));
                g2.fillRoundRect(5, 7, getWidth() - 5, getHeight() - 5, R, R);
                g2.setColor(AppColors.BG_PRIMARY);
                g2.fillRoundRect(0, 0, W, H, R, R);
                g2.setColor(AppColors.BORDER_DEFAULT);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, W - 1, H - 1, R, R);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(4, 4, 10, 8));

        gridPanel.setLayout(new BorderLayout(0, 0));
        gridPanel.setOpaque(false);

        gridScrollPane = new JScrollPane(gridPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        gridScrollPane.setBorder(null);
        gridScrollPane.setOpaque(false);
        gridScrollPane.getViewport().setOpaque(false);
        gridScrollPane.getVerticalScrollBar().setUnitIncrement(ROW_HEIGHT);
        AppUIManager.applySlimScrollBar(gridScrollPane);

        gridScrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                if (gridPanel.getComponentCount() > 0) {
                    gridPanel.getComponent(0).revalidate();
                    gridPanel.getComponent(0).repaint();
                }
            }
        });

        card.add(gridScrollPane, BorderLayout.CENTER);
        wrapper.add(card, BorderLayout.CENTER);
        return wrapper;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 更新週課表格子
    // ══════════════════════════════════════════════════════════════════════════
    public void refreshGrid() {
        closePopover();
        gridPanel.removeAll();

        if (activeSchedule == null) {
            JLabel hint = new JLabel("請點擊上方「新增課表」建立課表，再新增課程", SwingConstants.CENTER);
            hint.setFont(AppFonts.BODY_SMALL);
            hint.setForeground(AppColors.TEXT_TERTIARY);
            gridPanel.add(hint, BorderLayout.CENTER);
            gridPanel.revalidate();
            gridPanel.repaint();
            return;
        }

        final int ROWS = PERIOD_TIMES.length;
        final int COLS = DAY_NAMES.length;

        JPanel table = new JPanel(new GridBagLayout()) {
            @Override public Dimension getPreferredSize() {
                if (gridScrollPane != null) {
                    int vpW = gridScrollPane.getViewport().getWidth();
                    if (vpW > 0) {
                        Dimension d = super.getPreferredSize();
                        d.width = vpW;
                        return d;
                    }
                }
                return super.getPreferredSize();
            }
        };
        table.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;

        // 表頭列
        gc.gridy = 0; gc.weighty = 0; gc.gridheight = 1;
        gc.gridx = 0; gc.weightx = 0.055;
        table.add(headerCell(""), gc);
        for (int d = 0; d < COLS; d++) {
            boolean isWeekend = (d == 5 || d == 6);
            gc.gridx = d + 1;
            gc.weightx = (1.0 - 0.055) / COLS;
            JPanel h = headerCell("星期" + DAY_NAMES[d]);
            if (isWeekend) {
                h.setBackground(new Color(0xFFF5F5));
                ((JLabel) h.getComponent(0)).setForeground(AppColors.DANGER);
            }
            table.add(h, gc);
        }

        Course[][] occupiedBy  = new Course[ROWS][COLS];
        boolean[][] slotStartAt = new boolean[ROWS][COLS];

        for (Course c : activeSchedule.getCourses()) {
            List<int[]> allSlots = new ArrayList<>();
            String schedStr = c.getScheduleString();
            if (schedStr != null && !schedStr.isEmpty()) {
                for (String slot : schedStr.split(";")) {
                    int[] parsed = parseSlotString(slot.trim());
                    if (parsed != null) allSlots.add(parsed);
                }
            }
            if (allSlots.isEmpty()) {
                allSlots.add(new int[]{ c.getDayOfWeek(), c.getStartPeriod(), c.getEndPeriod() });
            }
            for (int[] slot : allSlots) {
                int day   = slot[0];
                int start = slot[1];
                int end   = slot[2];
                int col   = day - 1;
                if (col < 0 || col >= COLS) continue;
                if (start < 0 || start >= ROWS) continue;
                slotStartAt[start][col] = true;
                for (int p2 = start; p2 <= end && p2 < ROWS; p2++) {
                    occupiedBy[p2][col] = c;
                }
            }
        }

        boolean[][] rendered = new boolean[ROWS][COLS];
        for (int p = 0; p < ROWS; p++) {
            gc.gridy  = p + 1;
            gc.weighty = 0;

            gc.gridx = 0; gc.weightx = 0.055; gc.gridheight = 1;
            table.add(periodCell(PERIOD_TIMES[p]), gc);

            for (int d = 0; d < COLS; d++) {
                if (rendered[p][d]) continue;
                gc.gridx   = d + 1;
                gc.weightx = (1.0 - 0.055) / COLS;

                Course found = occupiedBy[p][d];

                if (found != null && slotStartAt[p][d]) {
                    int span = 1;
                    while (p + span < ROWS && occupiedBy[p + span][d] == found
                           && !slotStartAt[p + span][d]) {
                        span++;
                    }
                    for (int sp = p; sp < p + span && sp < ROWS; sp++) rendered[sp][d] = true;
                    gc.gridheight = span;
                    table.add(buildCourseCard(found), gc);
                    gc.gridheight = 1;
                } else if (found == null) {
                    rendered[p][d] = true;
                    gc.gridheight = 1;
                    table.add(buildEmptyCell(d == 5 || d == 6), gc);
                } else {
                    rendered[p][d] = true;
                }
            }
        }

        gridPanel.add(table, BorderLayout.NORTH);
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private JPanel buildEmptyCell(boolean weekend) {
        JPanel cell = new JPanel();
        cell.setOpaque(true);
        cell.setBackground(weekend ? new Color(0xFFFCFC) : AppColors.BG_PRIMARY);
        cell.setBorder(new MatteBorder(0, 0, 1, 1, AppColors.BORDER_DEFAULT));
        cell.setMinimumSize(new Dimension(0, ROW_HEIGHT));
        cell.setPreferredSize(new Dimension(0, ROW_HEIGHT));
        return cell;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 課程卡
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel buildCourseCard(Course c) {
        int[] ci = getCourseColorIndex(c);
        Color bgColor  = PRESET_COLORS[ci[0]][0];
        Color accColor = PRESET_COLORS[ci[0]][1];

        JPanel card = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bgColor);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(accColor);
                g2.fillRect(0, 0, 4, getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 1, AppColors.BORDER_DEFAULT),
            new EmptyBorder(4, 8, 4, 5)
        ));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setMinimumSize(new Dimension(0, ROW_HEIGHT));

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);

        JTextArea nameLbl = new JTextArea(c.getName());
        nameLbl.setFont(AppFonts.BODY_SMALL.deriveFont(Font.BOLD));
        nameLbl.setForeground(accColor.darker());
        nameLbl.setOpaque(false);
        nameLbl.setEditable(false);
        nameLbl.setFocusable(false);
        nameLbl.setLineWrap(true);
        nameLbl.setWrapStyleWord(true);
        nameLbl.setBorder(null);
        nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        MouseAdapter fwd = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e)  { card.dispatchEvent(SwingUtilities.convertMouseEvent(nameLbl, e, card)); }
            @Override public void mouseEntered(MouseEvent e)  { card.dispatchEvent(SwingUtilities.convertMouseEvent(nameLbl, e, card)); }
            @Override public void mouseExited(MouseEvent e)   { card.dispatchEvent(SwingUtilities.convertMouseEvent(nameLbl, e, card)); }
            @Override public void mousePressed(MouseEvent e)  { card.dispatchEvent(SwingUtilities.convertMouseEvent(nameLbl, e, card)); }
            @Override public void mouseReleased(MouseEvent e) { card.dispatchEvent(SwingUtilities.convertMouseEvent(nameLbl, e, card)); }
        };
        nameLbl.addMouseListener(fwd);
        inner.add(nameLbl);

        if (!c.getDepartment().isEmpty()) {
            JLabel deptLbl = new JLabel(c.getDepartment());
            deptLbl.setFont(AppFonts.CAPTION);
            deptLbl.setForeground(AppColors.TEXT_SECONDARY);
            deptLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            deptLbl.addMouseListener(fwd);
            inner.add(deptLbl);
        }

        if (!c.getClassYear().isEmpty()) {
            JLabel cyLbl = new JLabel(c.getClassYear());
            cyLbl.setFont(AppFonts.CAPTION);
            cyLbl.setForeground(AppColors.TEXT_SECONDARY);
            cyLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            cyLbl.addMouseListener(fwd);
            inner.add(cyLbl);
        }

        if (!c.getLocation().isEmpty()) {
            JLabel locLbl = new JLabel(c.getLocation());
            locLbl.setFont(AppFonts.CAPTION);
            locLbl.setForeground(AppColors.TEXT_SECONDARY);
            locLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            locLbl.addMouseListener(fwd);
            inner.add(locLbl);
        }

        card.add(inner, BorderLayout.NORTH);
        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { showCoursePopover(c, card); }
        });
        return card;
    }

    private int[] getCourseColorIndex(Course c) {
        int idx = c.getColorIndex();
        if (idx < 0 || idx >= PRESET_COLORS.length) {
            idx = (c.getId() - 1) % PRESET_COLORS.length;
        }
        return new int[]{ idx };
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Popover 管理
    // ══════════════════════════════════════════════════════════════════════════
    private void closePopover() {
        if (currentPopover == null) return;
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root != null) { root.getLayeredPane().remove(currentPopover); root.getLayeredPane().repaint(); }
        currentPopover = null;
    }

    private void showCoursePopover(Course course, Component anchor) {
        closePopover();
        JRootPane rootPane = SwingUtilities.getRootPane(this);
        if (rootPane == null) return;
        JLayeredPane layered = rootPane.getLayeredPane();

        currentPopover = buildCoursePopover(course);
        int popW = 290;
        currentPopover.setSize(popW, 9999);
        currentPopover.validate();
        int popH = Math.min(currentPopover.getPreferredSize().height, layered.getHeight() - 20);

        Point al = SwingUtilities.convertPoint(anchor, 0, 0, layered);
        int x = al.x + anchor.getWidth() + 4;
        int y = al.y;
        if (x + popW > layered.getWidth())  x = al.x - popW - 4;
        if (y + popH > layered.getHeight()) y = Math.max(0, layered.getHeight() - popH - 8);
        x = Math.max(0, x);

        currentPopover.setBounds(x, y, popW, popH);
        layered.add(currentPopover, JLayeredPane.POPUP_LAYER);
        layered.revalidate();
        layered.repaint();

        AWTEventListener closer = new AWTEventListener() {
            @Override public void eventDispatched(AWTEvent event) {
                if (event instanceof MouseEvent) {
                    MouseEvent me = (MouseEvent) event;
                    if (me.getID() == MouseEvent.MOUSE_PRESSED && currentPopover != null) {
                        Point p = SwingUtilities.convertPoint(me.getComponent(), me.getPoint(), currentPopover);
                        if (!currentPopover.contains(p)) {
                            closePopover();
                            Toolkit.getDefaultToolkit().removeAWTEventListener(this);
                        }
                    }
                }
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(closer, AWTEvent.MOUSE_EVENT_MASK);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 課程 Popover
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel buildCoursePopover(Course course) {
        int[] ci = getCourseColorIndex(course);
        Color headerBg  = PRESET_COLORS[ci[0]][0];
        Color accentCol = PRESET_COLORS[ci[0]][1];

        JPanel pop = new JPanel(new BorderLayout(0, 0));
        pop.setBackground(Color.WHITE);
        pop.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
            new EmptyBorder(0, 0, 0, 0)));
        pop.setOpaque(true);

        JPanel header = new JPanel(new BorderLayout(0, 0));
        header.setBackground(headerBg);
        header.setBorder(new EmptyBorder(10, 14, 10, 10));

        JTextArea titleLbl = new JTextArea(course.getName());
        titleLbl.setFont(AppFonts.BODY_MEDIUM.deriveFont(Font.BOLD));
        titleLbl.setForeground(accentCol.darker());
        titleLbl.setBackground(headerBg);
        titleLbl.setEditable(false);
        titleLbl.setFocusable(false);
        titleLbl.setLineWrap(true);
        titleLbl.setWrapStyleWord(true);
        titleLbl.setOpaque(false);
        titleLbl.setBorder(null);

        JButton closeBtn = new JButton("×");
        closeBtn.setFont(new Font(AppFonts.CAPTION.getFamily(), Font.PLAIN, 13));
        closeBtn.setForeground(AppColors.TEXT_TERTIARY);
        closeBtn.setBorder(new EmptyBorder(0, 6, 0, 0));
        closeBtn.setFocusPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> closePopover());

        // closeWrapper 讓 X 固定在右上角，不隨 title 增高而垂直置中
        JPanel closeWrapper = new JPanel(new BorderLayout());
        closeWrapper.setOpaque(false);
        closeWrapper.add(closeBtn, BorderLayout.NORTH);

        header.add(titleLbl,     BorderLayout.CENTER);
        header.add(closeWrapper, BorderLayout.EAST);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Color.WHITE);
        body.setBorder(new EmptyBorder(12, 14, 8, 14));

        String schedStr = course.getScheduleString();
        String[] timeSlots;
        if (schedStr != null && !schedStr.isEmpty()) {
            timeSlots = schedStr.split(";");
            for (int si = 0; si < timeSlots.length; si++) timeSlots[si] = timeSlots[si].trim();
        } else {
            String dayName = (course.getDayOfWeek() >= 1 && course.getDayOfWeek() <= 7)
                    ? DAY_NAMES[course.getDayOfWeek() - 1] : "?";
            timeSlots = new String[]{ "星期" + dayName + "  第" + course.getStartPeriod() + "～" + course.getEndPeriod() + "節" };
        }
        body.add(popTimeRow(timeSlots, accentCol));
        body.add(Box.createRigidArea(new Dimension(0, 6)));

        if (!course.getCode().isEmpty()) {
            body.add(popInfoRow("課代號", course.getCode(), accentCol));
            body.add(Box.createRigidArea(new Dimension(0, 4)));
        }
        if (!course.getProfessor().isEmpty()) {
            body.add(popInfoRow("教授", course.getProfessor(), accentCol));
            body.add(Box.createRigidArea(new Dimension(0, 4)));
        }
        if (!course.getLocation().isEmpty()) {
            body.add(popInfoRow("教室", course.getLocation(), accentCol));
            body.add(Box.createRigidArea(new Dimension(0, 4)));
        }
        if (!course.getDepartment().isEmpty()) {
            body.add(popInfoRow("開課系所", course.getDepartment(), accentCol));
            body.add(Box.createRigidArea(new Dimension(0, 4)));
        }
        if (!course.getClassYear().isEmpty()) {
            body.add(popInfoRow("開課年班", course.getClassYear(), accentCol));
            body.add(Box.createRigidArea(new Dimension(0, 4)));
        }

        if (!course.getNote().isEmpty()) {
            JPanel noteRow = new JPanel(new BorderLayout(0, 0));
            noteRow.setOpaque(false);
            noteRow.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel noteLbl = new JLabel("備註", SwingConstants.RIGHT);
            noteLbl.setFont(AppFonts.CAPTION.deriveFont(Font.BOLD));
            Color noteTagColor = accentCol.darker().darker();
            noteLbl.setForeground(noteTagColor);
            noteLbl.setPreferredSize(new Dimension(48, 16));
            noteLbl.setVerticalAlignment(SwingConstants.TOP);
            JLabel noteSep = new JLabel("  ·  ");
            noteSep.setFont(AppFonts.CAPTION);
            noteSep.setForeground(AppColors.BORDER_HOVER);
            noteSep.setVerticalAlignment(SwingConstants.TOP);
            JPanel noteLeft = new JPanel(new BorderLayout(0, 0));
            noteLeft.setOpaque(false);
            noteLeft.add(noteLbl, BorderLayout.CENTER);
            noteLeft.add(noteSep, BorderLayout.EAST);

            JTextArea noteArea = new JTextArea(course.getNote());
            noteArea.setFont(AppFonts.BODY_SMALL);
            noteArea.setForeground(AppColors.TEXT_PRIMARY);
            noteArea.setBackground(AppColors.BG_SECONDARY);
            noteArea.setEditable(false);
            noteArea.setLineWrap(true);
            noteArea.setWrapStyleWord(true);
            noteArea.setBorder(new EmptyBorder(2, 4, 4, 4));
            JScrollPane noteSp = new JScrollPane(noteArea,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            noteSp.setBorder(null);
            noteSp.setPreferredSize(new Dimension(200, 60));
            noteSp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
            noteSp.getVerticalScrollBar().setUnitIncrement(12);
            AppUIManager.applySlimScrollBar(noteSp);

            noteRow.add(noteLeft, BorderLayout.WEST);
            noteRow.add(noteSp,   BorderLayout.CENTER);
            body.add(noteRow);
            body.add(Box.createRigidArea(new Dimension(0, 5)));
        }

        JScrollPane bodyScroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        bodyScroll.setBorder(null);
        bodyScroll.getVerticalScrollBar().setUnitIncrement(16);
        AppUIManager.applySlimScrollBar(bodyScroll);

        CardLayout btnCard = new CardLayout();
        JPanel btnRow = new JPanel(btnCard);
        btnRow.setBackground(new Color(0xFAF9F7));
        btnRow.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));

        JButton editBtn = popBtn("編輯", AppColors.ACCENT, Color.WHITE);
        JButton delBtn  = popBtn("刪除", AppColors.BG_TERTIARY, AppColors.DANGER);
        JPanel normalPane = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
        normalPane.setOpaque(false); normalPane.add(delBtn); normalPane.add(editBtn);

        JButton cancelDelBtn  = popBtn("取消",    AppColors.BG_TERTIARY, AppColors.TEXT_SECONDARY);
        JButton confirmDelBtn = popBtn("確認刪除", AppColors.DANGER, Color.WHITE);
        JPanel confirmPane = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
        confirmPane.setOpaque(false); confirmPane.add(cancelDelBtn); confirmPane.add(confirmDelBtn);

        btnRow.add(normalPane,  "normal");
        btnRow.add(confirmPane, "confirm");
        btnCard.show(btnRow, "normal");

        editBtn.addActionListener(e -> { closePopover(); showCourseDialog(course); });
        delBtn.addActionListener(e -> btnCard.show(btnRow, "confirm"));
        cancelDelBtn.addActionListener(e -> btnCard.show(btnRow, "normal"));
        confirmDelBtn.addActionListener(e -> {
            activeSchedule.getCourses().remove(course);
            closePopover();
            refreshGrid();
            saveCallback.run();
        });

        pop.add(header,     BorderLayout.NORTH);
        pop.add(bodyScroll, BorderLayout.CENTER);
        pop.add(btnRow,     BorderLayout.SOUTH);
        return pop;
    }

    private JPanel popTimeRow(String[] slots, Color accentCol) {
        return popTwoColRow("時間", slots, accentCol);
    }

    private JPanel popTwoColRow(String tag, String[] lines, Color accentCol) {
        JPanel row = new JPanel(new BorderLayout(0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel tagLbl = new JLabel(tag, SwingConstants.RIGHT);
        tagLbl.setFont(AppFonts.CAPTION.deriveFont(Font.BOLD));
        tagLbl.setForeground(new Color(accentCol.darker().getRGB()).darker());
        tagLbl.setPreferredSize(new Dimension(48, 16));
        tagLbl.setVerticalAlignment(SwingConstants.TOP);

        JLabel sep = new JLabel("  ·  ");
        sep.setFont(AppFonts.CAPTION);
        sep.setForeground(AppColors.BORDER_HOVER);
        sep.setVerticalAlignment(SwingConstants.TOP);

        JPanel contentCol = new JPanel();
        contentCol.setLayout(new BoxLayout(contentCol, BoxLayout.Y_AXIS));
        contentCol.setOpaque(false);

        for (String line : lines) {
            JLabel lbl = new JLabel(escHtml(line));
            lbl.setFont(AppFonts.BODY_SMALL);
            lbl.setForeground(AppColors.TEXT_PRIMARY);
            lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentCol.add(lbl);
        }

        JPanel left = new JPanel(new BorderLayout(0, 0));
        left.setOpaque(false);
        left.add(tagLbl, BorderLayout.CENTER);
        left.add(sep,    BorderLayout.EAST);

        row.add(left,       BorderLayout.WEST);
        row.add(contentCol, BorderLayout.CENTER);
        return row;
    }

    private JPanel popInfoRow(String tag, String content, Color accentCol) {
        return popTwoColRow(tag, new String[]{ content }, accentCol);
    }

    private static String escHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    private JButton popBtn(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setFont(AppFonts.BODY_SMALL);
        b.setBackground(bg); b.setForeground(fg);
        b.setBorder(new EmptyBorder(5, 14, 5, 14));
        b.setFocusPainted(false); b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 懸浮圓角 Dialog 基礎工廠
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel buildFloatingRoot(JDialog dlg, String title, Color accentColor) {
        dlg.setUndecorated(true);
        dlg.setLayout(new BorderLayout());
        dlg.setBackground(new Color(0xF0F0F0));

        Color headerBg = blendWithWhite(accentColor, 0.10f);

        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0xF0F0F0));
                g2.fillRect(0, 0, getWidth(), getHeight());
                int W = getWidth() - 7, H = getHeight() - 7, R = 14;
                for (int i = 4; i >= 1; i--) {
                    g2.setColor(new Color(0, 0, 0, 7 * i));
                    g2.fillRoundRect(i + 1, i + 2, getWidth() - i * 2 - 1, getHeight() - i * 2 - 1, R, R);
                }
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, W, H, R, R);
                Component comp = getComponentCount() > 0 ? getComponent(0) : null;
                if (comp != null) {
                    int hh = comp.getHeight();
                    g2.setColor(headerBg);
                    g2.fillRoundRect(0, 0, W, R + hh, R, R);
                    g2.fillRect(0, R, W, hh - R);
                }
                g2.setColor(AppColors.BORDER_HOVER);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, W - 1, H - 1, R, R);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(0, 0, 7, 7));
        dlg.add(root);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(12, 16, 12, 10));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(AppFonts.TITLE_SMALL);
        titleLbl.setForeground(accentColor);

        JButton xBtn = new JButton("×");
        xBtn.setFont(new Font(AppFonts.BODY_MEDIUM.getFamily(), Font.PLAIN, 16));
        xBtn.setForeground(AppColors.TEXT_TERTIARY);
        xBtn.setBorder(new EmptyBorder(0, 8, 0, 4));
        xBtn.setFocusPainted(false);
        xBtn.setContentAreaFilled(false);
        xBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        xBtn.addActionListener(e -> dlg.dispose());
        header.add(titleLbl, BorderLayout.CENTER);
        header.add(xBtn,     BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        return root;
    }

    private JPanel buildDlgBtnRow(JDialog dlg, String okLabel, Color okBg, Runnable onOk) {
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
        cancelBtn.addActionListener(e -> dlg.dispose());

        JButton okBtn = new JButton(okLabel);
        okBtn.setFont(AppFonts.BODY_SMALL);
        okBtn.setBackground(okBg);
        okBtn.setForeground(Color.WHITE);
        okBtn.setBorder(new EmptyBorder(6, 18, 6, 18));
        okBtn.setFocusPainted(false);
        okBtn.setOpaque(true);
        okBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        okBtn.addActionListener(e -> onOk.run());

        btnRow.add(cancelBtn);
        btnRow.add(okBtn);
        dlg.getRootPane().setDefaultButton(okBtn);
        return btnRow;
    }

    private JButton dialogButton(String text, Color bg, Color fg) {
        JButton button = new JButton(text);
        button.setFont(AppFonts.BODY_SMALL);
        button.setBackground(bg);
        button.setForeground(fg);
        button.setBorder(new EmptyBorder(6, 18, 6, 18));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void showImportResultDialog(String title, String message, String detail, Color accentColor) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(owner, "", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = buildFloatingRoot(dlg, title, accentColor);

        JPanel content = new JPanel(new BorderLayout(12, 0));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel icon = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(blendWithWhite(accentColor, 0.12f));
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.setColor(accentColor);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(1, 1, getWidth() - 3, getHeight() - 3);
                g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int w = getWidth();
                int h = getHeight();
                if (accentColor == AppColors.DANGER) {
                    int cx = w / 2;
                    g2.drawLine(cx, h / 4, cx, h / 2 + 4);
                    g2.fillOval(cx - 1, h - 11, 3, 3);
                } else {
                    g2.drawLine(w / 4 + 1, h / 2 + 1, w / 2 - 3, h * 3 / 4 - 3);
                    g2.drawLine(w / 2 - 3, h * 3 / 4 - 3, w * 3 / 4 + 2, h / 3);
                }
                g2.dispose();
            }
        };
        icon.setOpaque(false);
        icon.setPreferredSize(new Dimension(38, 38));

        JPanel textCol = new JPanel();
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        textCol.setOpaque(false);

        JTextArea messageArea = new JTextArea(message);
        messageArea.setFont(AppFonts.BODY_MEDIUM);
        messageArea.setForeground(AppColors.TEXT_PRIMARY);
        messageArea.setEditable(false);
        messageArea.setFocusable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setOpaque(false);
        messageArea.setBorder(null);
        messageArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        textCol.add(messageArea);

        if (detail != null && !detail.isBlank()) {
            textCol.add(Box.createRigidArea(new Dimension(0, 6)));
            JTextArea detailArea = new JTextArea(detail);
            detailArea.setFont(AppFonts.BODY_SMALL);
            detailArea.setForeground(AppColors.TEXT_SECONDARY);
            detailArea.setEditable(false);
            detailArea.setFocusable(false);
            detailArea.setLineWrap(true);
            detailArea.setWrapStyleWord(true);
            detailArea.setOpaque(false);
            detailArea.setBorder(null);
            detailArea.setAlignmentX(Component.LEFT_ALIGNMENT);
            textCol.add(detailArea);
        }

        content.add(icon, BorderLayout.WEST);
        content.add(textCol, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        btnRow.setBackground(new Color(0xFAF9F7));
        btnRow.setOpaque(true);
        btnRow.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));

        JButton okBtn = new JButton("知道了");
        okBtn.setFont(AppFonts.BODY_SMALL);
        okBtn.setBackground(accentColor);
        okBtn.setForeground(Color.WHITE);
        okBtn.setBorder(new EmptyBorder(6, 18, 6, 18));
        okBtn.setFocusPainted(false);
        okBtn.setOpaque(true);
        okBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        okBtn.addActionListener(e -> dlg.dispose());
        btnRow.add(okBtn);

        root.add(content, BorderLayout.CENTER);
        root.add(btnRow, BorderLayout.SOUTH);
        dlg.getRootPane().setDefaultButton(okBtn);
        dlg.pack();
        dlg.setSize(Math.max(380, dlg.getWidth()), dlg.getHeight());
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private Color blendWithWhite(Color c, float ratio) {
        return new Color(
            (int)(c.getRed()   * ratio + 255 * (1 - ratio)),
            (int)(c.getGreen() * ratio + 255 * (1 - ratio)),
            (int)(c.getBlue()  * ratio + 255 * (1 - ratio))
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 新增 / 重新命名課表 Dialog
    // ══════════════════════════════════════════════════════════════════════════
    private void showScheduleDialog(Schedule editSchedule) {
        boolean isEdit = (editSchedule != null);
        Window owner = SwingUtilities.getWindowAncestor(this);

        JDialog dlg = new JDialog(owner, "", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = buildFloatingRoot(dlg, isEdit ? "重新命名課表" : "新增課表", AppColors.ACCENT);

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(14, 16, 10, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;

        int rowIdx = 0;

        if (isEdit) {
            // ── 重新命名：原名稱（唯讀）＋ 新名稱 ──
            // 原名稱
            gc.gridy = rowIdx++; gc.insets = new Insets(0, 0, 4, 0);
            content.add(dlgFieldLabel("原名稱"), gc);

            JTextField oldField = new JTextField(editSchedule.getName());
            oldField.setFont(AppFonts.BODY_MEDIUM);
            oldField.setEditable(false);
            oldField.setFocusable(false);
            oldField.setBackground(AppColors.BG_TERTIARY);
            oldField.setForeground(AppColors.TEXT_SECONDARY);
            oldField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
                new EmptyBorder(5, 8, 5, 8)));
            gc.gridy = rowIdx++; gc.insets = new Insets(0, 0, 12, 0);
            content.add(oldField, gc);

            // 新名稱
            gc.gridy = rowIdx++; gc.insets = new Insets(0, 0, 4, 0);
            content.add(dlgFieldLabel("新名稱"), gc);

            JTextField newField = new JTextField();
            newField.setFont(AppFonts.BODY_MEDIUM);
            newField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
                new EmptyBorder(5, 8, 5, 8)));
            gc.gridy = rowIdx++; gc.insets = new Insets(0, 0, 0, 0);
            content.add(newField, gc);

            Runnable onOk = () -> {
                String val = newField.getText().trim();
                if (val.isEmpty()) {
                    newField.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(AppColors.DANGER, 1, true),
                        new EmptyBorder(5, 8, 5, 8)));
                    newField.requestFocus();
                    return;
                }
                editSchedule.setName(val);
                rebuildCombo();
                dlg.dispose();
                refreshScheduleButtons();
                saveCallback.run();
            };
            newField.addActionListener(e -> onOk.run());

            root.add(content, BorderLayout.CENTER);
            root.add(buildDlgBtnRow(dlg, "儲存", AppColors.ACCENT, onOk), BorderLayout.SOUTH);

        } else {
            // ── 新增：只有課表名稱欄位 ──
            JTextField nameField = new JTextField();
            nameField.setFont(AppFonts.BODY_MEDIUM);
            nameField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
                new EmptyBorder(5, 8, 5, 8)));

            gc.gridy = rowIdx++; gc.insets = new Insets(0, 0, 4, 0);
            content.add(dlgFieldLabel("課表名稱"), gc);
            gc.gridy = rowIdx++; gc.insets = new Insets(0, 0, 0, 0);
            content.add(nameField, gc);

            Runnable onOk = () -> {
                String val = nameField.getText().trim();
                if (val.isEmpty()) {
                    nameField.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(AppColors.DANGER, 1, true),
                        new EmptyBorder(5, 8, 5, 8)));
                    nameField.requestFocus();
                    return;
                }
                int nextId = schedules.isEmpty() ? 1
                        : schedules.stream().mapToInt(Schedule::getId).max().orElse(0) + 1;
                Schedule s = new Schedule(nextId, val);
                schedules.add(s);
                switchSchedule(s);
                dlg.dispose();
                refreshScheduleButtons();
                saveCallback.run();
            };
            nameField.addActionListener(e -> onOk.run());

            root.add(content, BorderLayout.CENTER);
            root.add(buildDlgBtnRow(dlg, "新增", AppColors.ACCENT, onOk), BorderLayout.SOUTH);
        }

        dlg.pack();
        dlg.setSize(320 + 7, dlg.getHeight());
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 新增 / 編輯課程 Dialog
    // ══════════════════════════════════════════════════════════════════════════
    private void showCourseDialog(Course editCourse) {
        boolean isEdit = (editCourse != null);
        Window owner = SwingUtilities.getWindowAncestor(this);
        Color accentColor = isEdit ? AppColors.ACCENT : AppColors.SUCCESS;

        JDialog dlg = new JDialog(owner, "", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = buildFloatingRoot(dlg, isEdit ? "編輯課程" : "新增課程", accentColor);

        JTextField nameField  = dlgTextField(isEdit ? editCourse.getName()       : "");
        JTextField codeField  = dlgTextField(isEdit ? editCourse.getCode()       : "");
        JTextField locField   = dlgTextField(isEdit ? editCourse.getLocation()   : "");
        JTextField profField  = dlgTextField(isEdit ? editCourse.getProfessor()  : "");
        JTextField deptField  = dlgTextField(isEdit ? editCourse.getDepartment() : "");
        JTextField cyearField = dlgTextField(isEdit ? editCourse.getClassYear()  : "");
        limitLength(codeField,  10);
        limitLength(locField,   10);
        limitLength(deptField,  20);
        limitLength(cyearField, 10);

        JTextArea noteArea = new JTextArea(isEdit ? editCourse.getNote() : "", 3, 0);
        noteArea.setFont(AppFonts.BODY_SMALL);
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        noteArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane noteScroll = new JScrollPane(noteArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        noteScroll.setBorder(new LineBorder(AppColors.BORDER_DEFAULT, 1, true));
        noteScroll.setPreferredSize(new Dimension(0, 68));
        noteScroll.getVerticalScrollBar().setUnitIncrement(12);
        AppUIManager.applySlimScrollBar(noteScroll);

        // ── 顏色選擇器 ──
        int initColorIdx = isEdit ? Math.max(0, editCourse.getColorIndex()) : 0;
        if (initColorIdx < 0 || initColorIdx >= PRESET_COLORS.length)
            initColorIdx = (isEdit ? (editCourse.getId() - 1) % PRESET_COLORS.length : 0);
        final int[] selectedColorIdx = { initColorIdx };

        JPanel colorPanel = buildColorPicker(selectedColorIdx);

        // ── 多時段排程區塊 ──
        List<int[]> initSlots = new ArrayList<>();
        if (isEdit) {
            String schedStr = editCourse.getScheduleString();
            if (schedStr != null && !schedStr.isEmpty()) {
                for (String slot : schedStr.split(";")) {
                    int[] parsed = parseSlotString(slot.trim());
                    if (parsed != null) initSlots.add(parsed);
                }
            }
            if (initSlots.isEmpty()) {
                initSlots.add(new int[]{ editCourse.getDayOfWeek(),
                                         editCourse.getStartPeriod(),
                                         editCourse.getEndPeriod() });
            }
        } else {
            initSlots.add(new int[]{ 1, 1, 2 });
        }

        JPanel slotsContainer = new JPanel();
        slotsContainer.setLayout(new BoxLayout(slotsContainer, BoxLayout.Y_AXIS));
        slotsContainer.setOpaque(false);

        List<SlotRow> slotRows = new ArrayList<>();

        final int[] baseWidth = { 0 };

        Runnable rebuildSlots = new Runnable() {
            @Override public void run() {
                slotsContainer.removeAll();
                boolean hasRemoveBtn = slotRows.size() > 1;
                for (int i = 0; i < slotRows.size(); i++) {
                    final int fi = i;
                    SlotRow sr = slotRows.get(i);
                    JPanel rowPanel = new JPanel(new BorderLayout(4, 0));
                    rowPanel.setOpaque(false);
                    rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
                    rowPanel.add(sr.panel, BorderLayout.CENTER);
                    if (hasRemoveBtn) {
                        JButton removeBtn = new JButton("—");
                        removeBtn.setFont(AppFonts.CAPTION);
                        removeBtn.setForeground(AppColors.DANGER);
                        removeBtn.setBackground(AppColors.DANGER_LIGHT);
                        removeBtn.setBorder(new EmptyBorder(3, 6, 3, 6));
                        removeBtn.setFocusPainted(false);
                        removeBtn.setOpaque(true);
                        removeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        removeBtn.addActionListener(ev -> {
                            slotRows.remove(fi);
                            run();
                        });
                        rowPanel.add(removeBtn, BorderLayout.EAST);
                    }
                    slotsContainer.add(rowPanel);
                    slotsContainer.add(Box.createRigidArea(new Dimension(0, 4)));
                }
                slotsContainer.revalidate();
                slotsContainer.repaint();
                dlg.pack();
                int screenH = Toolkit.getDefaultToolkit().getScreenSize().height;
                int maxH = (int)(screenH * 0.85);
                if (!hasRemoveBtn) {
                    baseWidth[0] = dlg.getWidth();
                    dlg.setSize(baseWidth[0], Math.min(dlg.getPreferredSize().height, maxH));
                } else {
                    int w = Math.max(dlg.getWidth(), baseWidth[0]);
                    dlg.setSize(w, Math.min(dlg.getPreferredSize().height, maxH));
                }
            }
        };

        for (int[] slot : initSlots) {
            slotRows.add(new SlotRow(slot[0], slot[1], slot[2]));
        }
        rebuildSlots.run();

        JButton addSlotBtn = new JButton("+ 新增時段");
        addSlotBtn.setFont(AppFonts.CAPTION);
        addSlotBtn.setForeground(AppColors.ACCENT);
        addSlotBtn.setBackground(AppColors.ACCENT_LIGHT);
        addSlotBtn.setBorder(new EmptyBorder(4, 10, 4, 10));
        addSlotBtn.setFocusPainted(false);
        addSlotBtn.setOpaque(true);
        addSlotBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addSlotBtn.addActionListener(e -> {
            slotRows.add(new SlotRow(1, 1, 2));
            rebuildSlots.run();
        });

        // ── 錯誤 Banner ──
        JPanel errorBanner = new JPanel(new BorderLayout(8, 0));
        errorBanner.setOpaque(true);
        errorBanner.setBackground(AppColors.DANGER_LIGHT);
        errorBanner.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 3, 0, 0, AppColors.DANGER),
            new EmptyBorder(8, 12, 8, 12)));
        errorBanner.setVisible(false);

        JPanel errorIcon = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g2.setColor(AppColors.DANGER);
                g2.fillOval(0, 0, w, h);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = w / 2;
                g2.drawLine(cx, 3, cx, h - 6);
                g2.fillOval(cx - 1, h - 4, 3, 3);
                g2.dispose();
            }
        };
        errorIcon.setOpaque(false);
        errorIcon.setPreferredSize(new Dimension(16, 16));
        errorIcon.setMinimumSize(new Dimension(16, 16));
        errorIcon.setMaximumSize(new Dimension(16, 16));

        JPanel iconWrap = new JPanel(new GridBagLayout());
        iconWrap.setOpaque(false);
        iconWrap.setBorder(new EmptyBorder(0, 0, 0, 8));
        iconWrap.add(errorIcon);

        JLabel errorMsg = new JLabel("");
        errorMsg.setFont(AppFonts.BODY_SMALL);
        errorMsg.setForeground(AppColors.DANGER);

        errorBanner.add(iconWrap,  BorderLayout.WEST);
        errorBanner.add(errorMsg,  BorderLayout.CENTER);

        // ── Content ──
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(14, 16, 10, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL; gc.anchor = GridBagConstraints.WEST;

        int r = 0;
        gc.gridy = r++; gc.insets = new Insets(0,0,8,0);   content.add(errorBanner, gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,4,0);   content.add(dlgFieldLabel("課程名稱 *"), gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,10,0);  content.add(nameField, gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,4,0);   content.add(dlgFieldLabel("課代號（最多 10 字）"), gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,10,0);  content.add(codeField, gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,4,0);   content.add(dlgFieldLabel("教授"), gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,10,0);  content.add(profField, gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,4,0);   content.add(dlgFieldLabel("教室（最多 10 字）"), gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,10,0);  content.add(locField, gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,4,0);   content.add(dlgFieldLabel("開課系所"), gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,10,0);  content.add(deptField, gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,4,0);   content.add(dlgFieldLabel("開課年班"), gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,10,0);  content.add(cyearField, gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,6,0);   content.add(dlgFieldLabel("上課時段"), gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,4,0);   content.add(slotsContainer, gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,10,0);  content.add(addSlotBtn, gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,6,0);   content.add(dlgFieldLabel("課程顏色"), gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,10,0);  content.add(colorPanel, gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,4,0);   content.add(dlgFieldLabel("備註"), gc);
        gc.gridy = r++;  gc.insets = new Insets(0,0,0,0);  content.add(noteScroll, gc);

        JScrollPane contentScroll = new JScrollPane(content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        contentScroll.setBorder(null);
        contentScroll.setOpaque(false);
        contentScroll.getViewport().setOpaque(false);
        contentScroll.getVerticalScrollBar().setUnitIncrement(16);
        AppUIManager.applySlimScrollBar(contentScroll);

        Runnable onOk = () -> {
            String nameVal = nameField.getText().trim();
            if (nameVal.isEmpty()) {
                nameField.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(AppColors.DANGER, 1, true), new EmptyBorder(5, 8, 5, 8)));
                errorMsg.setText("課程名稱不可為空");
                errorBanner.setVisible(true);
                nameField.requestFocus();
                dlg.pack();
                dlg.setSize(dlg.getWidth(), Math.min(dlg.getPreferredSize().height,
                    (int)(Toolkit.getDefaultToolkit().getScreenSize().height * 0.85)));
                return;
            }
            if (slotRows.isEmpty()) {
                errorMsg.setText("請至少新增一個上課時段");
                errorBanner.setVisible(true);
                dlg.pack();
                dlg.setSize(dlg.getWidth(), Math.min(dlg.getPreferredSize().height,
                    (int)(Toolkit.getDefaultToolkit().getScreenSize().height * 0.85)));
                return;
            }

            for (int si = 0; si < slotRows.size(); si++) {
                SlotRow sr = slotRows.get(si);
                if (sr.getEnd() < sr.getStart()) {
                    String dayN = new String[]{"一","二","三","四","五","六","日"}[sr.getDay()-1];
                    errorMsg.setText("<html>時段 " + (si+1) + "（星期" + dayN + "）：結束節次（第" + sr.getEnd() + "節）不能早於開始節次（第" + sr.getStart() + "節）</html>");
                    errorBanner.setVisible(true);
                    dlg.pack();
                    dlg.setSize(dlg.getWidth(), Math.min(dlg.getPreferredSize().height,
                        (int)(Toolkit.getDefaultToolkit().getScreenSize().height * 0.85)));
                    return;
                }
            }

            String conflictMsg = detectOverlap(slotRows, isEdit ? editCourse : null);
            if (conflictMsg != null) {
                errorMsg.setText("<html>" + conflictMsg + "</html>");
                errorBanner.setVisible(true);
                dlg.pack();
                dlg.setSize(dlg.getWidth(), Math.min(dlg.getPreferredSize().height,
                    (int)(Toolkit.getDefaultToolkit().getScreenSize().height * 0.85)));
                return;
            }

            errorBanner.setVisible(false);
            nameField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColors.BORDER_DEFAULT, 1, true), new EmptyBorder(5, 8, 5, 8)));

            SlotRow first = slotRows.get(0);
            int day   = first.getDay();
            int start = first.getStart();
            int end   = first.getEnd();

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < slotRows.size(); i++) {
                SlotRow sr = slotRows.get(i);
                String dayN = DAY_NAMES[sr.getDay() - 1];
                sb.append("星期").append(dayN)
                  .append(" 第").append(sr.getStart()).append("～").append(sr.getEnd()).append("節");
                if (i < slotRows.size() - 1) sb.append(";");
            }

            if (isEdit) {
                editCourse.setName(nameVal);
                editCourse.setCode(codeField.getText().trim());
                editCourse.setLocation(locField.getText().trim());
                editCourse.setProfessor(profField.getText().trim());
                editCourse.setDepartment(deptField.getText().trim());
                editCourse.setClassYear(cyearField.getText().trim());
                editCourse.setDayOfWeek(day);
                editCourse.setStartPeriod(start);
                editCourse.setEndPeriod(end);
                editCourse.setNote(noteArea.getText().trim());
                editCourse.setColorIndex(selectedColorIdx[0]);
                editCourse.setScheduleString(sb.toString());
            } else {
                List<Course> courses = activeSchedule.getCourses();
                int nextId = courses.isEmpty() ? 1 : courses.stream().mapToInt(Course::getId).max().orElse(0) + 1;
                Course newCourse = new Course(nextId, nameVal,
                        codeField.getText().trim(), locField.getText().trim(),
                        profField.getText().trim(), day, start, end,
                        noteArea.getText().trim());
                newCourse.setDepartment(deptField.getText().trim());
                newCourse.setClassYear(cyearField.getText().trim());
                newCourse.setColorIndex(selectedColorIdx[0]);
                newCourse.setScheduleString(sb.toString());
                courses.add(newCourse);
            }
            dlg.dispose(); refreshGrid(); saveCallback.run();
        };

        root.add(contentScroll, BorderLayout.CENTER);
        root.add(buildDlgBtnRow(dlg, isEdit ? "儲存變更" : "新增課程", accentColor, onOk), BorderLayout.SOUTH);
        dlg.pack();
        int screenH = Toolkit.getDefaultToolkit().getScreenSize().height;
        int maxH    = (int)(screenH * 0.85);
        dlg.setSize(dlg.getWidth(), Math.min(dlg.getHeight(), maxH));
        dlg.setLocationRelativeTo(null);
        dlg.setVisible(true);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 顏色選擇器
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel buildColorPicker(int[] selectedColorIdx) {
        JPanel picker = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        picker.setOpaque(false);
        picker.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel[] dots = new JPanel[PRESET_COLORS.length];
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            final int idx = i;
            Color bgC  = PRESET_COLORS[i][0];
            Color accC = PRESET_COLORS[i][1];

            JPanel dot = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(bgC);
                    g2.fillOval(0, 0, getWidth(), getHeight());
                    g2.setColor(accC);
                    g2.setStroke(new BasicStroke(selectedColorIdx[0] == idx ? 3f : 1.5f));
                    g2.drawOval(1, 1, getWidth()-2, getHeight()-2);
                    if (selectedColorIdx[0] == idx) {
                        g2.setColor(accC);
                        g2.setStroke(new BasicStroke(2f));
                        g2.drawLine(getWidth()/2-4, getHeight()/2, getWidth()/2-1, getHeight()/2+3);
                        g2.drawLine(getWidth()/2-1, getHeight()/2+3, getWidth()/2+4, getHeight()/2-3);
                    }
                    g2.dispose();
                }
            };
            dot.setPreferredSize(new Dimension(24, 24));
            dot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            dot.setOpaque(false);
            dot.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    selectedColorIdx[0] = idx;
                    for (JPanel d : dots) d.repaint();
                }
            });
            dots[i] = dot;
            picker.add(dot);
        }
        return picker;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 時段列
    // ══════════════════════════════════════════════════════════════════════════
    class SlotRow {
        JPanel panel;
        private JComboBox<String> dayBox;
        private JComboBox<Integer> startBox, endBox;

        SlotRow(int initDay, int initStart, int initEnd) {
            String[] dayOptions = {"星期一","星期二","星期三","星期四","星期五","星期六","星期日"};
            dayBox = new JComboBox<>(dayOptions);
            applyComboStyle(dayBox);
            dayBox.setSelectedIndex(Math.max(0, initDay - 1));
            dayBox.setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                    super.getListCellRendererComponent(l, v, i, sel, foc);
                    setFont(AppFonts.BODY_SMALL);
                    setBorder(new EmptyBorder(5, 8, 5, 8));
                    setBackground(sel ? AppColors.ACCENT_LIGHT : Color.WHITE);
                    setForeground(sel ? AppColors.ACCENT : AppColors.TEXT_PRIMARY);
                    return this;
                }
            });

            Integer[] periods = new Integer[PERIOD_TIMES.length];
            for (int i = 0; i < periods.length; i++) periods[i] = i;
            startBox = new JComboBox<>(periods);
            endBox   = new JComboBox<>(periods);
            applyComboStyle(startBox);
            applyComboStyle(endBox);
            startBox.setSelectedItem(initStart);
            endBox.setSelectedItem(initEnd);
            startBox.setRenderer(periodRenderer());
            endBox.setRenderer(periodRenderer());

            JLabel toLabel = new JLabel("～");
            toLabel.setFont(AppFonts.BODY_SMALL);
            toLabel.setForeground(AppColors.TEXT_SECONDARY);

            panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            panel.setOpaque(false);
            panel.add(dayBox);
            panel.add(startBox);
            panel.add(toLabel);
            panel.add(endBox);
        }

        int getDay()   { return dayBox.getSelectedIndex() + 1; }
        int getStart() { return (int) startBox.getSelectedItem(); }
        int getEnd()   { return (int) endBox.getSelectedItem(); }
    }

    private String detectOverlap(List<SlotRow> slotRows, Course excludeCourse) {
        if (activeSchedule == null) return null;

        for (int i = 0; i < slotRows.size(); i++) {
            for (int j = i + 1; j < slotRows.size(); j++) {
                SlotRow a = slotRows.get(i);
                SlotRow b = slotRows.get(j);
                if (a.getDay() == b.getDay()) {
                    if (a.getStart() <= b.getEnd() && b.getStart() <= a.getEnd()) {
                        String[] dn = {"一","二","三","四","五","六","日"};
                        return "時段 " + (i+1) + " 與時段 " + (j+1) + " 在星期"
                            + dn[a.getDay()-1] + " 互相重疊";
                    }
                }
            }
        }

        for (SlotRow sr : slotRows) {
            int day   = sr.getDay();
            int start = sr.getStart();
            int end   = sr.getEnd();

            for (Course c : activeSchedule.getCourses()) {
                if (excludeCourse != null && c.getId() == excludeCourse.getId()) continue;

                List<int[]> existingSlots = new ArrayList<>();
                String schedStr = c.getScheduleString();
                if (schedStr != null && !schedStr.isEmpty()) {
                    for (String slot : schedStr.split(";")) {
                        int[] parsed = parseSlotString(slot.trim());
                        if (parsed != null) existingSlots.add(parsed);
                    }
                }
                if (existingSlots.isEmpty()) {
                    existingSlots.add(new int[]{ c.getDayOfWeek(), c.getStartPeriod(), c.getEndPeriod() });
                }

                for (int[] es : existingSlots) {
                    if (es[0] == day && start <= es[2] && es[1] <= end) {
                        String[] dn = {"一","二","三","四","五","六","日"};
                        return "與課程「" + c.getName() + "」在星期" + dn[day-1]
                            + " 第" + es[1] + "～" + es[2] + "節發生時段重疊";
                    }
                }
            }
        }
        return null;
    }

    private int[] parseSlotString(String s) {
        try {
            int dayIdx = -1;
            for (int i = 0; i < DAY_NAMES.length; i++) {
                if (s.contains("星期" + DAY_NAMES[i])) { dayIdx = i + 1; break; }
            }
            if (dayIdx < 0) return null;
            int startIdx = s.indexOf("第") + 1;
            int waveIdx  = s.indexOf("～", startIdx);
            int endIdx   = s.indexOf("節", waveIdx);
            int start = Integer.parseInt(s.substring(startIdx, waveIdx).trim());
            int end   = Integer.parseInt(s.substring(waveIdx + 1, endIdx).trim());
            return new int[]{ dayIdx, start, end };
        } catch (Exception e) { return null; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 切換課表
    // ══════════════════════════════════════════════════════════════════════════
    private void switchSchedule(Schedule s) {
        if (activeSchedule != null) activeSchedule.setActive(false);
        activeSchedule = s;
        s.setActive(true);
        rebuildCombo();
        topCardLayout.show(topControlPanel, "normal");
        refreshScheduleButtons();
        refreshGrid();
        saveCallback.run();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Grid Helpers
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel headerCell(String text) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(AppColors.BG_SECONDARY);
        p.setBorder(new MatteBorder(0, 0, 1, 1, AppColors.BORDER_DEFAULT));
        p.setMinimumSize(new Dimension(0, 36));
        p.setPreferredSize(new Dimension(80, 36));
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(AppFonts.LABEL);
        l.setForeground(AppColors.TEXT_SECONDARY);
        p.add(l, BorderLayout.CENTER);
        return p;
    }

    private JPanel periodCell(String text) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(AppColors.BG_SECONDARY);
        p.setBorder(new MatteBorder(0, 0, 1, 1, AppColors.BORDER_DEFAULT));
        p.setMinimumSize(new Dimension(0, ROW_HEIGHT));
        p.setPreferredSize(new Dimension(58, ROW_HEIGHT));
        String[] parts = text.split(" ");
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);
        inner.setBorder(new EmptyBorder(4, 4, 4, 4));
        JLabel l1 = new JLabel(parts[0]);
        l1.setFont(AppFonts.CAPTION.deriveFont(Font.BOLD));
        l1.setForeground(AppColors.TEXT_SECONDARY);
        l1.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(l1);
        if (parts.length > 1) {
            JLabel l2 = new JLabel(parts[1]);
            l2.setFont(new Font(AppFonts.CAPTION.getFamily(), Font.PLAIN, 10));
            l2.setForeground(AppColors.TEXT_TERTIARY);
            l2.setAlignmentX(Component.CENTER_ALIGNMENT);
            inner.add(l2);
        }
        p.add(inner, BorderLayout.CENTER);
        return p;
    }

    private ListCellRenderer<Object> periodRenderer() {
        return (list, value, index, isSelected, cellHasFocus) -> {
            int v = (Integer) value;
            JLabel l = new JLabel("第" + v + "節  " + PERIOD_TIMES[v].split(" ")[1]);
            l.setFont(AppFonts.BODY_SMALL);
            l.setBorder(new EmptyBorder(5, 8, 5, 8));
            l.setOpaque(true);
            l.setBackground(isSelected ? AppColors.ACCENT_LIGHT : Color.WHITE);
            l.setForeground(isSelected ? AppColors.ACCENT : AppColors.TEXT_PRIMARY);
            return l;
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Dialog 共用工廠
    // ══════════════════════════════════════════════════════════════════════════
    private JLabel dlgFieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFonts.BODY_SMALL);
        l.setForeground(AppColors.TEXT_SECONDARY);
        return l;
    }

    private JTextField dlgTextField(String text) {
        JTextField f = new JTextField(text);
        f.setFont(AppFonts.BODY_MEDIUM);
        f.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
            new EmptyBorder(5, 8, 5, 8)));
        return f;
    }

    private void limitLength(JTextField field, int maxLen) {
        String existing = field.getText();
        field.setDocument(new javax.swing.text.PlainDocument() {
            @Override public void insertString(int offs, String str, javax.swing.text.AttributeSet a)
                    throws javax.swing.text.BadLocationException {
                if (str == null) return;
                String current = getText(0, getLength());
                String candidate = current.substring(0, offs) + str + current.substring(offs);
                if (candidate.length() <= maxLen) super.insertString(offs, str, a);
            }
        });
        if (existing != null && !existing.isEmpty()) {
            field.setText(existing.length() > maxLen ? existing.substring(0, maxLen) : existing);
        }
    }
}
