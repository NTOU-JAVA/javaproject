import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * CategoryTagBar：頂部分類 Tag 篩選列。
 * 點擊 Tag 切換篩選，右側「管理」按鈕開啟分類管理 Dialog。
 */
public class CategoryTagBar extends JPanel {

    private final CategoryManager categoryManager;
    private final Consumer<String> onFilterChanged; // 回調：當前選取的分類名稱（或 ALL）
    private String selectedCategory = CategoryManager.ALL;

    // Tag 列容器
    private final JPanel tagRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));

    public CategoryTagBar(CategoryManager categoryManager, Consumer<String> onFilterChanged) {
        this.categoryManager  = categoryManager;
        this.onFilterChanged  = onFilterChanged;

        setLayout(new BorderLayout());
        setBackground(AppColors.BG_SECONDARY);
        setBorder(new MatteBorder(0, 0, 1, 0, AppColors.BORDER_DEFAULT));

        tagRow.setOpaque(false);
        tagRow.setBorder(new EmptyBorder(0, 10, 0, 0));

        JButton manageBtn = new JButton("管理分類");
        manageBtn.setFont(AppFonts.CAPTION);
        manageBtn.setForeground(AppColors.TEXT_TERTIARY);
        manageBtn.setBackground(AppColors.BG_SECONDARY);
        manageBtn.setBorder(new EmptyBorder(4, 10, 4, 12));
        manageBtn.setFocusPainted(false);
        manageBtn.setContentAreaFilled(false);
        manageBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        manageBtn.addActionListener(e -> openManageDialog());

        add(tagRow,    BorderLayout.CENTER);
        add(manageBtn, BorderLayout.EAST);

        // 當分類變動時自動重建 Tag 列
        categoryManager.addListener(this::rebuildTags);
        rebuildTags();
    }

    // ── Tag 列重建 ─────────────────────────────────────────────────────────
    public void rebuildTags() {
        tagRow.removeAll();
        List<String> opts = categoryManager.getAllOptions();

        // 若目前選取的分類已被刪除，退回「全部」
        if (!opts.contains(selectedCategory)) {
            selectedCategory = CategoryManager.ALL;
            onFilterChanged.accept(selectedCategory);
        }

        for (String opt : opts) {
            tagRow.add(buildTag(opt));
        }
        tagRow.revalidate();
        tagRow.repaint();
    }

    private JLabel buildTag(String name) {
        boolean active = name.equals(selectedCategory);

        JLabel tag = new JLabel(name) {
            private boolean hovered = false;
            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        if (!name.equals(selectedCategory)) {
                            selectedCategory = name;
                            onFilterChanged.accept(name);
                            rebuildTags();
                        }
                    }
                    @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                    @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = active  ? AppColors.ACCENT :
                           hovered ? AppColors.ACCENT_LIGHT :
                                     AppColors.BG_TERTIARY;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                if (!active) {
                    g2.setColor(AppColors.BORDER_DEFAULT);
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, getHeight(), getHeight());
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };

        tag.setFont(AppFonts.CAPTION);
        tag.setForeground(active ? Color.WHITE : AppColors.TEXT_SECONDARY);
        tag.setBorder(new EmptyBorder(3, 10, 3, 10));
        tag.setOpaque(false);
        tag.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return tag;
    }

    /** 取得目前選取的分類 */
    public String getSelectedCategory() { return selectedCategory; }

    // ── 管理 Dialog ────────────────────────────────────────────────────────
    private void openManageDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(owner, "管理分類", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setUndecorated(true);
        dlg.setLayout(new BorderLayout());
        dlg.setBackground(new Color(0xF0F0F0));

        // ── 浮動視窗風格（與其他 dialog 一致） ──
        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0xF0F0F0));
                g2.fillRect(0, 0, getWidth(), getHeight());
                int W = getWidth()-7, H = getHeight()-7, R = 14;
                for (int i = 4; i >= 1; i--) {
                    g2.setColor(new Color(0,0,0, 7*i));
                    g2.fillRoundRect(i+1, i+2, getWidth()-i*2-1, getHeight()-i*2-1, R, R);
                }
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, W, H, R, R);
                Component comp = getComponentCount() > 0 ? getComponent(0) : null;
                if (comp != null) {
                    g2.setColor(AppColors.ACCENT_LIGHT);
                    g2.fillRoundRect(0, 0, W, R + comp.getHeight(), R, R);
                    g2.fillRect(0, R, W, comp.getHeight()-R);
                }
                g2.setColor(AppColors.BORDER_HOVER);
                g2.setStroke(new java.awt.BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, W-1, H-1, R, R);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(0,0,7,7));
        dlg.add(root);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(12,16,12,10));
        JLabel title = new JLabel("管理分類");
        title.setFont(AppFonts.TITLE_SMALL);
        title.setForeground(AppColors.ACCENT);
        JButton closeBtn = new JButton("×");
        closeBtn.setFont(new Font(AppFonts.BODY_MEDIUM.getFamily(), Font.PLAIN, 16));
        closeBtn.setForeground(AppColors.TEXT_TERTIARY);
        closeBtn.setBorder(new EmptyBorder(0,8,0,4));
        closeBtn.setFocusPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dlg.dispose());
        header.add(title,    BorderLayout.CENTER);
        header.add(closeBtn, BorderLayout.EAST);

        // 分類清單
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(AppColors.BG_PRIMARY);
        listPanel.setBorder(new EmptyBorder(6, 0, 6, 0));

        Runnable refreshList = new Runnable() {
            @Override public void run() {
                listPanel.removeAll();
                for (String cat : categoryManager.getCategories()) {
                    listPanel.add(buildCategoryRow(cat, this, dlg));
                }
                listPanel.revalidate();
                listPanel.repaint();
                dlg.pack();
                dlg.setSize(340 + 7, dlg.getPreferredSize().height);
            }
        };
        refreshList.run();

        JScrollPane listScroll = new JScrollPane(listPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        listScroll.setBorder(new MatteBorder(1,0,1,0, AppColors.BORDER_DEFAULT));
        listScroll.setPreferredSize(new Dimension(0, Math.min(listPanel.getPreferredSize().height + 8, 240)));
        AppUIManager.applySlimScrollBar(listScroll);

        // 新增分類輸入列
        JTextField addField = new JTextField();
        addField.setFont(AppFonts.BODY_SMALL);
        addField.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
            new EmptyBorder(5,8,5,8)));

        JButton addBtn = new JButton("新增");
        addBtn.setFont(AppFonts.BODY_SMALL);
        addBtn.setBackground(AppColors.ACCENT);
        addBtn.setForeground(Color.WHITE);
        addBtn.setBorder(new EmptyBorder(6,14,6,14));
        addBtn.setFocusPainted(false);
        addBtn.setOpaque(true);
        addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Runnable doAdd = () -> {
            String val = addField.getText().trim();
            if (val.isEmpty()) { addField.requestFocus(); return; }
            if (!categoryManager.addCategory(val)) {
                addField.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(AppColors.DANGER, 1, true),
                    new EmptyBorder(5,8,5,8)));
                return;
            }
            addField.setText("");
            addField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
                new EmptyBorder(5,8,5,8)));
            refreshList.run();
        };
        addBtn.addActionListener(e -> doAdd.run());
        addField.addActionListener(e -> doAdd.run());

        JPanel addRow = new JPanel(new BorderLayout(6,0));
        addRow.setOpaque(false);
        addRow.setBorder(new EmptyBorder(10,16,10,16));
        addRow.add(addField, BorderLayout.CENTER);
        addRow.add(addBtn,   BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(listScroll, BorderLayout.CENTER);
        content.add(addRow,     BorderLayout.SOUTH);

        // 底部提示
        JPanel hint = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 6));
        hint.setBackground(new Color(0xFAF9F7));
        hint.setBorder(new MatteBorder(1,0,0,0, AppColors.BORDER_DEFAULT));
        JLabel hintLbl = new JLabel("點擊分類名稱可重新命名");
        hintLbl.setFont(AppFonts.CAPTION);
        hintLbl.setForeground(AppColors.TEXT_TERTIARY);
        hint.add(hintLbl);

        root.add(header,  BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        root.add(hint,    BorderLayout.SOUTH);

        dlg.pack();
        dlg.setSize(340 + 7, dlg.getPreferredSize().height);
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
    }

    /** 管理 Dialog 裡每一列分類 */
    private JPanel buildCategoryRow(String cat, Runnable refreshList, JDialog dlg) {
        JPanel row = new JPanel(new BorderLayout(0,0)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(AppColors.BORDER_DEFAULT);
                g.drawLine(0, getHeight()-1, getWidth(), getHeight()-1);
            }
        };
        row.setOpaque(true);
        row.setBackground(AppColors.BG_PRIMARY);
        row.setBorder(new EmptyBorder(0,16,0,8));
        row.setPreferredSize(new Dimension(0, 44));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        // 分類名稱（可雙擊編輯）
        JLabel nameLbl = new JLabel(cat);
        nameLbl.setFont(AppFonts.BODY_SMALL);
        nameLbl.setForeground(AppColors.TEXT_PRIMARY);

        // 右側按鈕
        JButton delBtn = new JButton("刪除");
        delBtn.setFont(AppFonts.CAPTION);
        delBtn.setForeground(AppColors.DANGER);
        delBtn.setBackground(AppColors.DANGER_LIGHT);
        delBtn.setBorder(new EmptyBorder(3,8,3,8));
        delBtn.setFocusPainted(false);
        delBtn.setOpaque(true);
        delBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        delBtn.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(dlg,
                "刪除「" + cat + "」分類？\n（現有標記此分類的事項不會被刪除，分類欄位將清空）",
                "確認刪除", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                categoryManager.removeCategory(cat);
                refreshList.run();
            }
        });

        JPanel btnArea = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 8));
        btnArea.setOpaque(false);
        btnArea.add(delBtn);

        // 雙擊重新命名
        nameLbl.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String newName = JOptionPane.showInputDialog(dlg, "重新命名分類：", cat);
                    if (newName != null && !newName.isBlank()) {
                        categoryManager.renameCategory(cat, newName.trim());
                        refreshList.run();
                    }
                }
            }
        });

        row.add(nameLbl, BorderLayout.CENTER);
        row.add(btnArea, BorderLayout.EAST);
        return row;
    }
}