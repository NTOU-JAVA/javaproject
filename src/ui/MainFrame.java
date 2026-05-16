package ui;

import java.awt.*;
import java.awt.event.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;
import model.Schedule;
import model.Task;
import model.TodoItem;
import service.CategoryManager;
import service.ReminderService;
import service.SessionManager;
import service.TronclassService;

/**
 * MainFrame：主視窗，含 Topbar、Sidebar、CardLayout 內容區。
 *
 * v0.6 變更：
 *  - 引入 SessionManager 統一管理登入狀態
 *  - Topbar 右側依狀態顯示：「登入 Tronclass」/ 已登入（名稱＋登出＋重新同步）/ 失效提示
 *  - Cookie 失效時：
 *      1. Topbar 顯示橘色警告列
 *      2. 彈出一次通知 Dialog（僅第一次）
 *      3. 使用者可點「重新登入」再次開啟登入流程
 *  - 背景 Timer（每 15 分鐘）驗證 cookie 是否仍有效
 */
public class MainFrame extends JFrame {

    private final JPanel     contentArea;
    private final CardLayout cardLayout;
    private NavItem          activeNav;

    private final CalendarPanel   calendarPanel;
    private final TodoPanel       todoPanel;
    private final SchoolNewsPanel newsPanel;
    private final SchedulePanel   schedulePanel;
    private NavItem calendarNav;
    private NavItem todoNav;
    private NavItem newsNav;
    private NavItem scheduleNav;

    private final Runnable saveTasksCallback;
    private final Runnable saveTodosCallback;
    private final Runnable saveSchedulesCallback;
    private Runnable closeRequestHandler;

    private CategoryManager categoryManager;

    // ── Session 管理 ────────────────────────────────────────────────────────
    private final SessionManager sessionManager = new SessionManager();

    // ── Topbar 動態元件 ──────────────────────────────────────────────────────
    private JPanel  topbarUserArea;   // 右側動態區域（CardLayout 切換）
    private CardLayout topbarCard;
    private boolean expiredDialogShown = false;

    // 定時背景驗證（每 15 分鐘）
    private Timer cookieValidationTimer;

    public MainFrame(List<Task> tasks, List<TodoItem> todos, List<Schedule> schedules,
                     CategoryManager categoryManager,
                     Runnable saveTasksCallback, Runnable saveTodosCallback,
                     Runnable saveSchedulesCallback) {

        this.saveTasksCallback = saveTasksCallback;
        this.saveTodosCallback = saveTodosCallback;
        this.saveSchedulesCallback = saveSchedulesCallback;

        categoryManager.addRemoveListener(deletedCat -> {
            for (Task t : tasks) {
                if (deletedCat.equals(t.getCategory())) t.setCategory("");
            }
            saveTasksCallback.run();
        });
        categoryManager.addRenameListener((oldName, newName) -> {
            for (Task t : tasks) {
                if (oldName.equals(t.getCategory())) t.setCategory(newName);
            }
            saveTasksCallback.run();
        });

        calendarPanel = new CalendarPanel(tasks, categoryManager, saveTasksCallback);
        todoPanel     = new TodoPanel(todos, saveTodosCallback, categoryManager);
        newsPanel     = new SchoolNewsPanel();
        schedulePanel = new SchedulePanel(schedules, saveSchedulesCallback);

        setTitle("學生行程與任務管理系統");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1200, 700);
        setMinimumSize(new Dimension(900, 560));
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (closeRequestHandler != null) {
                    closeRequestHandler.run();
                } else {
                    exitApplication();
                }
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppColors.BG_SECONDARY);
        setContentPane(root);

        root.add(buildTopbar(),  BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(AppColors.BG_SECONDARY);
        body.add(buildSidebar(), BorderLayout.WEST);

        cardLayout  = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.setBackground(AppColors.BG_SECONDARY);
        contentArea.add(calendarPanel, "calendar");
        contentArea.add(todoPanel,     "todo");
        contentArea.add(newsPanel,     "news");
        contentArea.add(schedulePanel, "schedule");
        body.add(contentArea, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);

        // 監聽 Session 狀態變化
        sessionManager.addListener((state, userName) -> onSessionStateChanged(state, userName));
        if (sessionManager.restoreSavedSession()) {
            validateCookieInBackground();
        }

        // 啟動背景定時驗證（2 分鐘）
        cookieValidationTimer = new Timer(2 * 60 * 1000, e -> validateCookieInBackground());
        cookieValidationTimer.start();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Topbar
    // ══════════════════════════════════════════════════════════════════════════

    private JPanel buildTopbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(AppColors.TOPBAR_BG);
        bar.setPreferredSize(new Dimension(0, 52));
        bar.setBorder(new MatteBorder(0, 0, 1, 0, AppColors.BORDER_DEFAULT));

        // 左側 logo
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.setBorder(new EmptyBorder(0, 18, 0, 0));

        JLabel logoBox = new JLabel("S", SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColors.ACCENT_LIGHT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(0xC5D0FA));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        logoBox.setFont(AppFonts.TITLE_SMALL);
        logoBox.setForeground(AppColors.ACCENT);
        logoBox.setOpaque(false);
        logoBox.setPreferredSize(new Dimension(36, 36));

        JLabel appName = new JLabel("  學生行程與任務管理系統");
        appName.setFont(AppFonts.TITLE_SMALL);
        appName.setForeground(AppColors.TEXT_PRIMARY);

        left.add(logoBox);
        left.add(appName);
        bar.add(left, BorderLayout.WEST);

        // 右側：CardLayout 動態切換
        topbarCard    = new CardLayout();
        topbarUserArea = new JPanel(topbarCard);
        topbarUserArea.setOpaque(false);

        topbarUserArea.add(buildLoggedOutPanel(),  "logged_out");
        topbarUserArea.add(buildLoggedInPanel(),   "logged_in");
        topbarUserArea.add(buildExpiredPanel(),    "expired");

        topbarCard.show(topbarUserArea, "logged_out");

        JPanel rightWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightWrapper.setOpaque(false);
        rightWrapper.add(topbarUserArea);
        bar.add(rightWrapper, BorderLayout.EAST);

        return bar;
    }

    /** 未登入面板 */
    private JPanel buildLoggedOutPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        p.setOpaque(false);

        JLabel hint = new JLabel("尚未登入");
        hint.setFont(AppFonts.CAPTION);
        hint.setForeground(AppColors.TEXT_TERTIARY);

        JLabel avatar = makeAvatar("?");

        JButton loginBtn = topbarBtn("登入 Tronclass", AppColors.ACCENT, Color.WHITE);
        loginBtn.addActionListener(e -> openLoginDialog());

        p.add(hint);
        p.add(avatar);
        p.add(loginBtn);
        return p;
    }

    /** 已登入面板（含使用者名稱、登出、重新同步） */
    private JPanel buildLoggedInPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        p.setOpaque(false);

        JLabel nameLbl  = new JLabel("—");
        nameLbl.setFont(AppFonts.BODY_SMALL);
        nameLbl.setForeground(AppColors.TEXT_PRIMARY);
        nameLbl.setName("nameLbl");

        JLabel avatar = makeAvatar("?");
        avatar.setName("avatarLbl");

        JButton resyncBtn = topbarBtn("重新同步", AppColors.BG_TERTIARY, AppColors.TEXT_PRIMARY);
        resyncBtn.addActionListener(e -> openLoginDialog());

        JButton logoutBtn = topbarBtn("登出", AppColors.DANGER_LIGHT, AppColors.DANGER);
        logoutBtn.addActionListener(e -> confirmLogout());

        p.add(nameLbl);
        p.add(avatar);
        p.add(resyncBtn);
        p.add(logoutBtn);
        return p;
    }

    /** Cookie 失效面板（橘色警告） */
    private JPanel buildExpiredPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        p.setOpaque(false);

        JLabel warnLbl = new JLabel("登入已失效");
        warnLbl.setFont(AppFonts.BODY_SMALL);
        warnLbl.setForeground(AppColors.DANGER);

        JButton reloginBtn = topbarBtn("重新登入", AppColors.DANGER_LIGHT, AppColors.DANGER);
        reloginBtn.addActionListener(e -> openLoginDialog());

        JButton logoutBtn = topbarBtn("清除登入", AppColors.BG_TERTIARY, AppColors.TEXT_SECONDARY);
        logoutBtn.addActionListener(e -> sessionManager.logout());

        p.add(warnLbl);
        p.add(reloginBtn);
        p.add(logoutBtn);
        return p;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Session 狀態變化處理（在 EDT 上執行）
    // ══════════════════════════════════════════════════════════════════════════

    private void onSessionStateChanged(SessionManager.State state, String userName) {
        switch (state) {
            case LOGGED_IN:
                updateLoggedInPanel(userName);
                topbarCard.show(topbarUserArea, "logged_in");
                expiredDialogShown = false;
                break;

            case LOGGED_OUT:
                topbarCard.show(topbarUserArea, "logged_out");
                expiredDialogShown = false;
                break;

            case EXPIRED:
                topbarCard.show(topbarUserArea, "expired");
                if (!expiredDialogShown) {
                    expiredDialogShown = true;
                    showSessionExpiredNotification(userName);
                }
                break;
        }
        topbarUserArea.revalidate();
        topbarUserArea.repaint();
    }

    /** 把已登入面板中的使用者名稱與頭像更新為實際值 */
    private void updateLoggedInPanel(String userName) {
        // 在 buildLoggedInPanel() 中的元件找到 nameLbl 和 avatarLbl
        JPanel p = (JPanel) topbarUserArea.getComponent(1); // "logged_in" 是第 2 個
        for (Component c : p.getComponents()) {
            if ("nameLbl".equals(c.getName()) && c instanceof JLabel) {
                ((JLabel) c).setText(userName != null ? userName : "已登入");
            }
            if ("avatarLbl".equals(c.getName()) && c instanceof JLabel) {
                String initial = (userName != null && !userName.isEmpty())
                        ? String.valueOf(userName.charAt(0)) : "✓";
                ((JLabel) c).setText(initial);
            }
        }
    }

    /** 顯示 cookie 失效的通知彈窗（每次失效只顯示一次） */
    private void showSessionExpiredNotification(String lastUserName) {
        String who = (lastUserName != null && !lastUserName.isEmpty())
                ? "「" + lastUserName + "」的" : "";
        showTronclassMessageDialog(
            "登入已失效",
            who + "Cookie 已過期或被登出。",
            "請點擊右上角「重新登入」以繼續同步資料。",
            AppColors.DANGER,
            DialogIcon.WARNING
        );
    }

    /** 確認登出彈窗 */
    private void confirmLogout() {
        boolean confirmed = showTronclassConfirmDialog(
            "確認登出",
            "確定要登出 Tronclass 嗎？",
            "本機的代辦事項資料不會被刪除。",
            "登出",
            AppColors.DANGER,
            DialogIcon.WARNING
        );
        if (confirmed) {
            sessionManager.logout();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 背景定時驗證 cookie
    // ══════════════════════════════════════════════════════════════════════════

    private void validateCookieInBackground() {
        if (!sessionManager.isLoggedIn()) return;
        String cookie = sessionManager.getCookie();
        if (cookie == null) return;

        // 在背景執行，不阻塞 EDT
        new Thread(() -> {
            boolean valid = TronclassService.validateCookie(cookie);
            if (!valid) {
                // notifySessionExpired 已經在 EDT 上執行 listener
                sessionManager.notifySessionExpired();
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 開啟登入 Dialog
    // ══════════════════════════════════════════════════════════════════════════

    private void openLoginDialog() {
        List<TodoItem> todos = todoPanel.getTodos();

        TronclassLoginDialog dlg = new TronclassLoginDialog(
            this, todos, saveTodosCallback, sessionManager, categoryManager,
            (name, cookie, added) -> {
                SwingUtilities.invokeLater(() -> {
                    todoPanel.refreshList();
                    String displayName = (name != null) ? name : "使用者";
                    String msg = added > 0
                        ? "已同步 " + added + " 筆待辦事項到「代辦事項」頁面。"
                        : "登入成功，無新的待辦事項。";
                    showTronclassMessageDialog(
                        "Tronclass 同步完成",
                        displayName + " 您好！",
                        msg,
                        AppColors.SUCCESS,
                        DialogIcon.SUCCESS
                    );
                });
            }
        );
        dlg.setVisible(true);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Tronclass 狀態 Dialog
    // ══════════════════════════════════════════════════════════════════════════

    private enum DialogIcon { SUCCESS, WARNING }

    private void showTronclassMessageDialog(String title, String message, String detail,
                                            Color accentColor, DialogIcon iconType) {
        JDialog dlg = new JDialog(this, "", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = buildFloatingDialogRoot(dlg, title, accentColor);

        root.add(buildDialogContent(message, detail, accentColor, iconType), BorderLayout.CENTER);
        root.add(buildDialogButtonRow(dlg, "知道了", accentColor, null), BorderLayout.SOUTH);

        dlg.pack();
        dlg.setSize(Math.max(380, dlg.getWidth()), dlg.getHeight());
        AppUIManager.applyRoundedWindowShape(dlg, 16);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private boolean showTronclassConfirmDialog(String title, String message, String detail,
                                               String okLabel, Color accentColor,
                                               DialogIcon iconType) {
        final boolean[] confirmed = {false};
        JDialog dlg = new JDialog(this, "", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = buildFloatingDialogRoot(dlg, title, accentColor);

        root.add(buildDialogContent(message, detail, accentColor, iconType), BorderLayout.CENTER);
        root.add(buildDialogButtonRow(dlg, okLabel, accentColor, () -> confirmed[0] = true), BorderLayout.SOUTH);

        dlg.pack();
        dlg.setSize(Math.max(380, dlg.getWidth()), dlg.getHeight());
        AppUIManager.applyRoundedWindowShape(dlg, 16);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
        return confirmed[0];
    }

    private JPanel buildFloatingDialogRoot(JDialog dlg, String title, Color accentColor) {
        dlg.setUndecorated(true);
        dlg.setLayout(new BorderLayout());
        dlg.setBackground(new Color(0xF0F0F0));

        Color headerBg = blendWithWhite(accentColor, 0.10f);
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
                    int headerHeight = comp.getHeight();
                    g2.setColor(headerBg);
                    g2.fillRoundRect(0, 0, W, R + headerHeight, R, R);
                    g2.fillRect(0, R, W, headerHeight - R);
                }
                g2.setColor(AppColors.BORDER_HOVER);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, W - 1, H - 1, R, R);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(0, 0, 9, 9));
        dlg.add(root);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(12, 16, 12, 10));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(AppFonts.TITLE_SMALL);
        titleLbl.setForeground(accentColor);

        JButton closeBtn = new JButton("×");
        closeBtn.setFont(new Font(AppFonts.BODY_MEDIUM.getFamily(), Font.PLAIN, 16));
        closeBtn.setForeground(AppColors.TEXT_TERTIARY);
        closeBtn.setBorder(new EmptyBorder(0, 8, 0, 4));
        closeBtn.setFocusPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dlg.dispose());

        header.add(titleLbl, BorderLayout.CENTER);
        header.add(closeBtn, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);
        return root;
    }

    private JPanel buildDialogContent(String message, String detail, Color accentColor, DialogIcon iconType) {
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
                if (iconType == DialogIcon.WARNING) {
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
        textCol.add(dialogTextArea(message, AppFonts.BODY_MEDIUM, AppColors.TEXT_PRIMARY));

        if (detail != null && !detail.isBlank()) {
            textCol.add(Box.createRigidArea(new Dimension(0, 6)));
            textCol.add(dialogTextArea(detail, AppFonts.BODY_SMALL, AppColors.TEXT_SECONDARY));
        }

        content.add(icon, BorderLayout.WEST);
        content.add(textCol, BorderLayout.CENTER);
        return content;
    }

    private JTextArea dialogTextArea(String text, Font font, Color color) {
        JTextArea area = new JTextArea(text);
        area.setFont(font);
        area.setForeground(color);
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(null);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        return area;
    }

    private JPanel buildDialogButtonRow(JDialog dlg, String okLabel, Color okBg, Runnable onOk) {
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        btnRow.setBackground(new Color(0xFAF9F7));
        btnRow.setOpaque(true);
        btnRow.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));

        if (onOk != null) {
            JButton cancelBtn = dialogButton("取消", AppColors.BG_TERTIARY, AppColors.TEXT_SECONDARY);
            cancelBtn.addActionListener(e -> dlg.dispose());
            btnRow.add(cancelBtn);
        }

        JButton okBtn = dialogButton(okLabel, okBg, Color.WHITE);
        okBtn.addActionListener(e -> {
            if (onOk != null) onOk.run();
            dlg.dispose();
        });
        btnRow.add(okBtn);
        dlg.getRootPane().setDefaultButton(okBtn);
        return btnRow;
    }

    private JButton dialogButton(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setFont(AppFonts.BODY_SMALL);
        b.setBackground(bg);
        b.setForeground(fg);
        b.setBorder(new EmptyBorder(6, 18, 6, 18));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private Color blendWithWhite(Color c, float ratio) {
        return new Color(
            (int)(c.getRed()   * ratio + 255 * (1 - ratio)),
            (int)(c.getGreen() * ratio + 255 * (1 - ratio)),
            (int)(c.getBlue()  * ratio + 255 * (1 - ratio))
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Topbar 小工具
    // ══════════════════════════════════════════════════════════════════════════

    private JLabel makeAvatar(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(AppFonts.BODY_SMALL);
        l.setForeground(AppColors.ACCENT_TEXT);
        l.setBackground(AppColors.ACCENT_LIGHT);
        l.setOpaque(true);
        l.setPreferredSize(new Dimension(32, 32));
        l.setBorder(new LineBorder(AppColors.ACCENT, 1));
        return l;
    }

    private JButton topbarBtn(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setFont(AppFonts.CAPTION);
        b.setBackground(bg);
        b.setForeground(fg);
        b.setBorder(new EmptyBorder(5, 12, 5, 12));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Sidebar
    // ══════════════════════════════════════════════════════════════════════════

    private JPanel buildSidebar() {
        JPanel sb = new JPanel();
        sb.setLayout(new BoxLayout(sb, BoxLayout.Y_AXIS));
        sb.setBackground(AppColors.SIDEBAR_BG);
        sb.setPreferredSize(new Dimension(200, 0));
        sb.setBorder(new MatteBorder(0, 0, 0, 1, AppColors.BORDER_DEFAULT));

        sb.add(Box.createRigidArea(new Dimension(0, 16)));
        sb.add(sectionLabel("主要功能"));

        NavItem calNav      = new NavItem("任務行事曆");
        NavItem todoNav     = new NavItem("代辦事項");
        NavItem newsNav     = new NavItem("學校公告");
        NavItem scheduleNav = new NavItem("課程課表");

        this.calendarNav = calNav;
        this.todoNav = todoNav;
        this.newsNav = newsNav;
        this.scheduleNav = scheduleNav;

        calNav.addActionListener(e      -> switchTo("calendar", calNav));
        todoNav.addActionListener(e     -> switchTo("todo",     todoNav));
        newsNav.addActionListener(e     -> switchTo("news",     newsNav));
        scheduleNav.addActionListener(e -> switchTo("schedule", scheduleNav));

        sb.add(calNav);
        sb.add(todoNav);
        sb.add(newsNav);
        sb.add(scheduleNav);

        sb.add(Box.createRigidArea(new Dimension(0, 12)));
        sb.add(Box.createVerticalGlue());

        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setForeground(AppColors.BORDER_DEFAULT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sb.add(sep);
        sb.add(Box.createRigidArea(new Dimension(0, 8)));

        JLabel ver = new JLabel("  v0.6  早期預覽版");
        ver.setFont(AppFonts.CAPTION);
        ver.setForeground(AppColors.TEXT_TERTIARY);
        ver.setAlignmentX(Component.LEFT_ALIGNMENT);
        sb.add(ver);
        sb.add(Box.createRigidArea(new Dimension(0, 10)));

        activeNav = calNav;
        calNav.setActive(true);
        return sb;
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(AppFonts.LABEL);
        l.setForeground(AppColors.TEXT_TERTIARY);
        l.setBorder(new EmptyBorder(4, 16, 4, 16));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        return l;
    }

    private void switchTo(String key, NavItem nav) {
        if (activeNav != null) activeNav.setActive(false);
        nav.setActive(true);
        activeNav = nav;
        cardLayout.show(contentArea, key);
    }

    // ── NavItem ────────────────────────────────────────────────────────────

    static class NavItem extends JPanel {
        private boolean active  = false;
        private boolean hovered = false;
        private final java.util.List<ActionListener> listeners = new java.util.ArrayList<>();
        private final JLabel nameLabel;

        NavItem(String label) {
            setLayout(new FlowLayout(FlowLayout.LEFT, 8, 7));
            setOpaque(false);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(0, 8, 0, 8));

            nameLabel = new JLabel(label);
            nameLabel.setFont(AppFonts.NAV);
            nameLabel.setForeground(AppColors.TEXT_SECONDARY);
            add(nameLabel);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    for (ActionListener l : listeners) l.actionPerformed(null);
                }
            });
        }

        void addActionListener(ActionListener l) { listeners.add(l); }

        void setActive(boolean a) {
            this.active = a;
            nameLabel.setFont(a ? AppFonts.NAV_ACTIVE : AppFonts.NAV);
            nameLabel.setForeground(a ? AppColors.TEXT_PRIMARY : AppColors.TEXT_SECONDARY);
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color bg = active ? AppColors.NAV_ACTIVE_BG : hovered ? AppColors.NAV_HOVER_BG : null;
            if (bg != null) {
                g2.setColor(bg);
                g2.fillRoundRect(6, 2, getWidth() - 12, getHeight() - 4, 8, 8);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public void setCloseRequestHandler(Runnable closeRequestHandler) {
        this.closeRequestHandler = closeRequestHandler;
    }

    public void hideToTray() {
        setVisible(false);
    }

    public void restoreFromTray() {
        setVisible(true);
        setState(Frame.NORMAL);
        setExtendedState(Frame.NORMAL);
        toFront();
        requestFocusInWindow();
        setAlwaysOnTop(true);
        setAlwaysOnTop(false);
    }

    public void refreshReminderViews() {
        todoPanel.refreshList();
        calendarPanel.updateCalendar();
    }

    public void openReminderTarget(ReminderService.TargetType targetType, int targetId) {
        restoreFromTray();
        SwingUtilities.invokeLater(() -> {
            if (targetType == ReminderService.TargetType.TODO) {
                switchTo("todo", todoNav);
                todoPanel.revealTodo(targetId);
            } else if (targetType == ReminderService.TargetType.TASK) {
                switchTo("calendar", calendarNav);
                calendarPanel.revealTask(targetId);
            }
        });
    }

    public void exitApplication() {
        saveTasksCallback.run();
        saveTodosCallback.run();
        saveSchedulesCallback.run();
        if (cookieValidationTimer != null) cookieValidationTimer.stop();
        dispose();
        System.exit(0);
    }

    public void showWindow() { setVisible(true); }
}
