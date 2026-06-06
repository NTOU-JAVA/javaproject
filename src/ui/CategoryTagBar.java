package ui;

import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.*;
import service.CategoryManager;

/**
 * CategoryTagBar：頂部分類 Tag 篩選列。
 * 點擊 Tag 切換篩選，右側「管理」按鈕開啟分類管理 Dialog。
 * 受保護分類不顯示刪除／重新命名按鈕。
 * 重新命名使用 [原名稱] → [新名稱] 格式 Dialog。
 */
public class CategoryTagBar extends JPanel {

    private final CategoryManager categoryManager;
    private final Consumer<String> onFilterChanged;
    private String selectedCategory = CategoryManager.ALL;

    private final JPanel tagRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    private JScrollPane tagScroll;

    public CategoryTagBar(CategoryManager categoryManager, Consumer<String> onFilterChanged) {
        this.categoryManager  = categoryManager;
        this.onFilterChanged  = onFilterChanged;

        setLayout(new BorderLayout());
        setBackground(AppColors.BG_SECONDARY);
        setBorder(new MatteBorder(0, 0, 1, 0, AppColors.BORDER_DEFAULT));

        tagRow.setOpaque(false);
        tagRow.setBorder(new EmptyBorder(0, 10, 0, 0));

        JButton manageBtn = new JButton("管理分類") {
            private boolean hovered = false;
            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hovered = true;  setForeground(Color.WHITE); repaint(); }
                    @Override public void mouseExited(MouseEvent e)  { hovered = false; setForeground(AppColors.ACCENT); repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = hovered ? AppColors.ACCENT : AppColors.ACCENT_LIGHT;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        manageBtn.setFont(AppFonts.CAPTION);
        manageBtn.setForeground(AppColors.ACCENT);
        manageBtn.setBorder(new EmptyBorder(3, 10, 3, 10));
        manageBtn.setFocusPainted(false);
        manageBtn.setContentAreaFilled(false);
        manageBtn.setOpaque(false);
        manageBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        manageBtn.addActionListener(e -> openManageDialog());

        // 包在一個帶右邊距的容器裡
        JPanel manageBtnWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        manageBtnWrapper.setOpaque(false);
        manageBtnWrapper.setBorder(new EmptyBorder(0, 0, 0, 10));
        manageBtnWrapper.add(manageBtn);

        tagScroll = new JScrollPane(tagRow,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        tagScroll.setBorder(null);
        tagScroll.setOpaque(false);
        tagScroll.getViewport().setOpaque(false);
        tagScroll.getHorizontalScrollBar().setUnitIncrement(24);
        tagScroll.setWheelScrollingEnabled(false);
        AppUIManager.applySlimScrollBar(tagScroll);
        tagScroll.addMouseWheelListener(e -> {
            JScrollBar bar = tagScroll.getHorizontalScrollBar();
            int next = bar.getValue() + e.getUnitsToScroll() * bar.getUnitIncrement();
            bar.setValue(Math.max(bar.getMinimum(), Math.min(next, bar.getMaximum())));
        });

        add(tagScroll,       BorderLayout.CENTER);
        add(manageBtnWrapper, BorderLayout.EAST);

        categoryManager.addListener(this::rebuildTags);
        rebuildTags();
    }

    // Tag 列重建
    public void rebuildTags() {
        tagRow.removeAll();
        List<String> opts = categoryManager.getAllOptions();

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

    public String getSelectedCategory() { return selectedCategory; }

    // 管理 Dialog
    private void openManageDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(owner, "管理分類", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setUndecorated(true);
        dlg.setLayout(new BorderLayout());
        dlg.setBackground(new Color(0xF0F0F0));

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
        root.setBorder(new EmptyBorder(0,0,9,9));
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
        AppUIManager.enableWindowDrag(dlg, header);

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
                AppUIManager.keepWindowInScreen(dlg);
            }
        };
        refreshList.run();

        JScrollPane listScroll = new JScrollPane(listPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        listScroll.setBorder(new MatteBorder(1,1,1,0, AppColors.BORDER_DEFAULT));
        listScroll.setPreferredSize(new Dimension(0, Math.min(listPanel.getPreferredSize().height + 8, 240)));
        listScroll.getVerticalScrollBar().setUnitIncrement(16);
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
            if (categoryManager.getAllOptions().contains(val)) {
                AppUIManager.showErrorDialog(dlg, "分類已存在", "已經有「" + val + "」這個分類。");
                addField.requestFocus();
                return;
            }
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

        root.add(header,  BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);

        dlg.pack();
        dlg.setSize(340 + 7, dlg.getPreferredSize().height);
        AppUIManager.applyRoundedWindowShape(dlg, 16);
        dlg.setLocationRelativeTo(owner);
        AppUIManager.keepWindowInScreen(dlg);
        dlg.setVisible(true);
    }

    private void showDuplicateCategoryHint(JDialog owner, String categoryName) {
        Window dialogOwner = owner != null ? owner : SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(dialogOwner, "分類已存在", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColors.BORDER_HOVER, 1, true),
                new EmptyBorder(14, 16, 14, 16)));

        JLabel title = new JLabel("分類已存在");
        title.setFont(AppFonts.TITLE_SMALL);
        title.setForeground(AppColors.ACCENT);

        JLabel message = new JLabel("已經有「" + categoryName + "」這個分類。");
        message.setFont(AppFonts.BODY_SMALL);
        message.setForeground(AppColors.TEXT_SECONDARY);

        JButton okBtn = new JButton("確定");
        okBtn.setFont(AppFonts.BODY_SMALL);
        okBtn.setBackground(AppColors.ACCENT);
        okBtn.setForeground(Color.WHITE);
        okBtn.setBorder(new EmptyBorder(6, 18, 6, 18));
        okBtn.setFocusPainted(false);
        okBtn.setOpaque(true);
        okBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
        AppUIManager.keepWindowInScreen(dialog);
        dialog.setVisible(true);
    }

    /** 管理 Dialog 裡每一列分類 */
    private JPanel buildCategoryRow(String cat, Runnable refreshList, JDialog dlg) {
        boolean isProtected = categoryManager.isProtected(cat);

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

        // 分類名稱
        JLabel nameLbl = new JLabel(cat);
        nameLbl.setFont(AppFonts.BODY_SMALL);
        nameLbl.setForeground(isProtected ? AppColors.TEXT_SECONDARY : AppColors.TEXT_PRIMARY);

        // 受保護標記
        if (isProtected) {
            JLabel lockLbl = new JLabel("  預設");
            lockLbl.setFont(AppFonts.CAPTION);
            lockLbl.setForeground(AppColors.TEXT_TERTIARY);
            JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            nameRow.setOpaque(false);
            nameRow.add(nameLbl);
            nameRow.add(lockLbl);
            row.add(nameRow, BorderLayout.CENTER);
        } else {
            row.add(nameLbl, BorderLayout.CENTER);
        }

        // 右側按鈕（僅非受保護分類顯示）
        if (!isProtected) {
            JButton renameBtn = new JButton("重新命名");
            renameBtn.setFont(AppFonts.CAPTION);
            renameBtn.setForeground(AppColors.ACCENT);
            renameBtn.setBackground(AppColors.ACCENT_LIGHT);
            renameBtn.setBorder(new EmptyBorder(3,8,3,8));
            renameBtn.setFocusPainted(false);
            renameBtn.setOpaque(true);
            renameBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            renameBtn.addActionListener(e -> openRenameDialog(cat, refreshList, dlg));

            JButton delBtn = new JButton("刪除");
            delBtn.setFont(AppFonts.CAPTION);
            delBtn.setForeground(AppColors.DANGER);
            delBtn.setBackground(AppColors.DANGER_LIGHT);
            delBtn.setBorder(new EmptyBorder(3,8,3,8));
            delBtn.setFocusPainted(false);
            delBtn.setOpaque(true);
            delBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // 行內確認按鈕（預設隱藏）
            JButton cancelDelBtn = new JButton("取消");
            cancelDelBtn.setFont(AppFonts.CAPTION);
            cancelDelBtn.setForeground(AppColors.TEXT_SECONDARY);
            cancelDelBtn.setBackground(AppColors.BG_TERTIARY);
            cancelDelBtn.setBorder(new EmptyBorder(3,8,3,8));
            cancelDelBtn.setFocusPainted(false);
            cancelDelBtn.setOpaque(true);
            cancelDelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            cancelDelBtn.setVisible(false);

            JButton confirmDelBtn = new JButton("確認刪除");
            confirmDelBtn.setFont(AppFonts.CAPTION);
            confirmDelBtn.setForeground(Color.WHITE);
            confirmDelBtn.setBackground(AppColors.DANGER);
            confirmDelBtn.setBorder(new EmptyBorder(3,8,3,8));
            confirmDelBtn.setFocusPainted(false);
            confirmDelBtn.setOpaque(true);
            confirmDelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            confirmDelBtn.setVisible(false);

            delBtn.addActionListener(e -> {
                delBtn.setVisible(false);
                renameBtn.setVisible(false);
                cancelDelBtn.setVisible(true);
                confirmDelBtn.setVisible(true);
            });

            cancelDelBtn.addActionListener(e -> {
                cancelDelBtn.setVisible(false);
                confirmDelBtn.setVisible(false);
                delBtn.setVisible(true);
                renameBtn.setVisible(true);
            });

            confirmDelBtn.addActionListener(e -> {
                categoryManager.removeCategory(cat);
                refreshList.run();
            });

            JPanel btnArea = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 8));
            btnArea.setOpaque(false);
            btnArea.add(delBtn);
            btnArea.add(renameBtn);
            btnArea.add(cancelDelBtn);
            btnArea.add(confirmDelBtn);
            row.add(btnArea, BorderLayout.EAST);
        }

        return row;
    }

    /**
     * 重新命名 Dialog：顯示原名稱（唯讀）與新名稱輸入欄。
     */
    private void openRenameDialog(String oldName, Runnable refreshList, JDialog parentDlg) {
        Window owner = SwingUtilities.getWindowAncestor(parentDlg);
        JDialog renameDlg = new JDialog(owner, "重新命名分類", Dialog.ModalityType.APPLICATION_MODAL);
        renameDlg.setUndecorated(true);
        renameDlg.setLayout(new BorderLayout());
        renameDlg.setBackground(new Color(0xF0F0F0));

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
        root.setBorder(new EmptyBorder(0,0,9,9));
        renameDlg.add(root);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(12,16,12,10));
        JLabel titleLbl = new JLabel("重新命名分類");
        titleLbl.setFont(AppFonts.TITLE_SMALL);
        titleLbl.setForeground(AppColors.ACCENT);
        JButton xBtn = new JButton("×");
        xBtn.setFont(new Font(AppFonts.BODY_MEDIUM.getFamily(), Font.PLAIN, 16));
        xBtn.setForeground(AppColors.TEXT_TERTIARY);
        xBtn.setBorder(new EmptyBorder(0,8,0,4));
        xBtn.setFocusPainted(false);
        xBtn.setContentAreaFilled(false);
        xBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        xBtn.addActionListener(e -> renameDlg.dispose());
        header.add(titleLbl, BorderLayout.CENTER);
        header.add(xBtn,     BorderLayout.EAST);
        AppUIManager.enableWindowDrag(renameDlg, header);

        // Content：原名稱（唯讀）+ 新名稱（可輸入）
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(14,16,10,16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;

        // 原名稱（唯讀欄位）
        gc.gridy = 0; gc.insets = new Insets(0,0,4,0);
        content.add(renameFieldLabel("原名稱"), gc);
        gc.gridy = 1; gc.insets = new Insets(0,0,12,0);
        JTextField oldField = new JTextField(oldName);
        oldField.setFont(AppFonts.BODY_MEDIUM);
        oldField.setEditable(false);
        oldField.setFocusable(false);
        oldField.setBackground(AppColors.BG_TERTIARY);
        oldField.setForeground(AppColors.TEXT_SECONDARY);
        oldField.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
            new EmptyBorder(5,8,5,8)));
        content.add(oldField, gc);

        // 新名稱
        gc.gridy = 2; gc.insets = new Insets(0,0,4,0);
        content.add(renameFieldLabel("新名稱"), gc);
        gc.gridy = 3; gc.insets = new Insets(0,0,0,0);
        JTextField newField = new JTextField();
        newField.setFont(AppFonts.BODY_MEDIUM);
        newField.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
            new EmptyBorder(5,8,5,8)));
        content.add(newField, gc);

        // 底部按鈕
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        btnRow.setBackground(new Color(0xFAF9F7));
        btnRow.setOpaque(true);
        btnRow.setBorder(new MatteBorder(1,0,0,0, AppColors.BORDER_DEFAULT));

        JButton cancelBtn = new JButton("取消");
        cancelBtn.setFont(AppFonts.BODY_SMALL);
        cancelBtn.setForeground(AppColors.TEXT_SECONDARY);
        cancelBtn.setBackground(AppColors.BG_TERTIARY);
        cancelBtn.setOpaque(true);
        cancelBtn.setBorder(new EmptyBorder(6,16,6,16));
        cancelBtn.setFocusPainted(false);
        cancelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancelBtn.addActionListener(e -> renameDlg.dispose());

        JButton okBtn = new JButton("儲存");
        okBtn.setFont(AppFonts.BODY_SMALL);
        okBtn.setBackground(AppColors.ACCENT);
        okBtn.setForeground(Color.WHITE);
        okBtn.setBorder(new EmptyBorder(6,18,6,18));
        okBtn.setFocusPainted(false);
        okBtn.setOpaque(true);
        okBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Runnable doRename = () -> {
            String newName = newField.getText().trim();
            if (newName.isEmpty()) {
                newField.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(AppColors.DANGER, 1, true),
                    new EmptyBorder(5,8,5,8)));
                newField.requestFocus();
                return;
            }
            if (!categoryManager.renameCategory(oldName, newName)) {
                newField.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(AppColors.DANGER, 1, true),
                    new EmptyBorder(5,8,5,8)));
                return;
            }
            renameDlg.dispose();
            refreshList.run();
        };
        okBtn.addActionListener(e -> doRename.run());
        newField.addActionListener(e -> doRename.run());
        renameDlg.getRootPane().setDefaultButton(okBtn);

        btnRow.add(cancelBtn);
        btnRow.add(okBtn);

        root.add(header,  BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        root.add(btnRow,  BorderLayout.SOUTH);

        renameDlg.pack();
        renameDlg.setSize(300 + 7, renameDlg.getPreferredSize().height);
        AppUIManager.applyRoundedWindowShape(renameDlg, 16);
        renameDlg.setLocationRelativeTo(parentDlg);
        AppUIManager.keepWindowInScreen(renameDlg);
        renameDlg.setVisible(true);
    }

    private static JLabel renameFieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFonts.BODY_SMALL);
        l.setForeground(AppColors.TEXT_SECONDARY);
        return l;
    }
}
