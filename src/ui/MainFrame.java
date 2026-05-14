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

    private final Runnable saveTodosCallback;

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

        this.saveTodosCallback = saveTodosCallback;

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
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 700);
        setMinimumSize(new Dimension(900, 560));
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                saveTasksCallback.run();
                saveTodosCallback.run();
                saveSchedulesCallback.run();
                if (cookieValidationTimer != null) cookieValidationTimer.stop();
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
        warnLbl.setForeground(AppColors.WARNING);

        JButton reloginBtn = topbarBtn("重新登入", AppColors.WARNING_LIGHT, AppColors.WARNING);
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
        JOptionPane.showMessageDialog(
            this,
            "<html><b>Tronclass 登入已失效</b><br><br>"
            + who + "Cookie 已過期或被登出。<br>"
            + "請點擊右上角「重新登入」以繼續同步資料。</html>",
            "登入已失效",
            JOptionPane.WARNING_MESSAGE
        );
    }

    /** 確認登出彈窗 */
    private void confirmLogout() {
        int choice = JOptionPane.showConfirmDialog(
            this,
            "<html>確定要登出 Tronclass 嗎？<br>"
            + "<span style='color:#6B6A67;font-size:11px'>本機的代辦事項資料不會被刪除。</span></html>",
            "確認登出",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        if (choice == JOptionPane.YES_OPTION) {
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
            this, todos, saveTodosCallback, sessionManager,
            (name, cookie, added) -> {
                SwingUtilities.invokeLater(() -> {
                    todoPanel.refreshList();
                    String displayName = (name != null) ? name : "使用者";
                    String msg = added > 0
                        ? "已同步 " + added + " 筆待辦事項到「代辦事項」頁面。"
                        : "登入成功，無新的待辦事項。";
                    JOptionPane.showMessageDialog(this,
                        "<html><b>" + displayName + "</b> 您好！<br>" + msg + "</html>",
                        "Tronclass 同步完成",
                        JOptionPane.INFORMATION_MESSAGE);
                });
            }
        );
        dlg.setVisible(true);
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

    public void showWindow() { setVisible(true); }
}
