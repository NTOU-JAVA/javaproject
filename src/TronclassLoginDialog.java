import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URI;

/**
 * TronclassLoginDialog：Tronclass 登入流程視窗。
 *
 * 流程：
 *  1. 使用者點「開啟 Tronclass 登入頁」→ 瀏覽器跳到登入頁
 *  2. 在瀏覽器完成登入後，F12 複製 Cookie
 *  3. 貼到「Cookie」欄位 → 按「同步資料」
 *  4. 程式呼叫 TronclassService 解析姓名 + 待辦，結果透過 callback 回傳
 */
public class TronclassLoginDialog extends JDialog {

    private static final String TRONCLASS_URL = "https://tronclass.ntou.edu.tw";
    private static final String LOGIN_URL      = TRONCLASS_URL + "/user/index";

    /** 登入成功後的回調 */
    public interface LoginCallback {
        /**
         * @param name   解析到的姓名（可能為 null）
         * @param cookie 已驗證的 Cookie 字串
         * @param added  新增的待辦事項數量
         */
        void onSuccess(String name, String cookie, int added);
    }

    private final LoginCallback callback;
    private final java.util.List<TodoItem> currentTodos;
    private final Runnable saveCallback;

    // UI 元件
    private JTextArea cookieArea;
    private JButton   syncBtn;
    private JLabel    statusLabel;

    public TronclassLoginDialog(Window owner,
                                java.util.List<TodoItem> currentTodos,
                                Runnable saveCallback,
                                LoginCallback callback) {
        super(owner, "", ModalityType.APPLICATION_MODAL);
        this.currentTodos = currentTodos;
        this.saveCallback = saveCallback;
        this.callback     = callback;

        setUndecorated(true);
        setLayout(new BorderLayout());
        setBackground(new Color(0xF0F0F0));

        JPanel root = buildRoot();
        add(root);
        pack();
        setSize(500, getPreferredSize().height);
        setLocationRelativeTo(owner);
    }

    // ── UI 建構 ──────────────────────────────────────────────────────────────

    private JPanel buildRoot() {
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
                // Header 色帶
                Component comp = getComponentCount() > 0 ? getComponent(0) : null;
                if (comp != null) {
                    g2.setColor(AppColors.ACCENT_LIGHT);
                    g2.fillRoundRect(0, 0, W, R + comp.getHeight(), R, R);
                    g2.fillRect(0, R, W, comp.getHeight() - R);
                }
                g2.setColor(AppColors.BORDER_HOVER);
                g2.setStroke(new java.awt.BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, W - 1, H - 1, R, R);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(0, 0, 7, 7));

        // ── Header ──
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(12, 16, 12, 10));

        JLabel title = new JLabel("Tronclass 登入與資料同步");
        title.setFont(AppFonts.TITLE_SMALL);
        title.setForeground(AppColors.ACCENT);

        JButton closeBtn = makeCloseBtn();
        closeBtn.addActionListener(e -> dispose());

        header.add(title,    BorderLayout.CENTER);
        header.add(closeBtn, BorderLayout.EAST);

        // ── 主內容 ──
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(16, 18, 10, 18));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.weightx = 1.0;
        gc.fill  = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;

        // 步驟 1
        gc.gridy = 0; gc.insets = new Insets(0, 0, 8, 0);
        content.add(stepLabel("步驟 1　在瀏覽器中登入 Tronclass"), gc);

        gc.gridy = 1; gc.insets = new Insets(0, 0, 14, 0);
        JButton openBtn = makeOpenBtn();
        openBtn.addActionListener(e -> openBrowser());
        content.add(openBtn, gc);

        // 步驟 2
        gc.gridy = 2; gc.insets = new Insets(0, 0, 6, 0);
        content.add(stepLabel("步驟 2　複製 Cookie 並貼入下方"), gc);

        gc.gridy = 3; gc.insets = new Insets(0, 0, 4, 0);
        content.add(hintLabel("F12 → Network → 任意請求 → Request Headers → 複製 Cookie 整行"), gc);

        gc.gridy = 4; gc.insets = new Insets(0, 0, 14, 0);
        cookieArea = new JTextArea(4, 0);
        cookieArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        cookieArea.setLineWrap(true);
        cookieArea.setWrapStyleWord(false);
        cookieArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane cookieSp = new JScrollPane(cookieArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        cookieSp.setBorder(new LineBorder(AppColors.BORDER_DEFAULT, 1, true));
        cookieSp.setPreferredSize(new Dimension(0, 90));
        AppUIManager.applySlimScrollBar(cookieSp);
        content.add(cookieSp, gc);

        // 狀態列
        gc.gridy = 5; gc.insets = new Insets(0, 0, 0, 0);
        statusLabel = new JLabel(" ");
        statusLabel.setFont(AppFonts.CAPTION);
        statusLabel.setForeground(AppColors.TEXT_TERTIARY);
        content.add(statusLabel, gc);

        // ── 底部按鈕 ──
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        btnRow.setBackground(new Color(0xFAF9F7));
        btnRow.setOpaque(true);
        btnRow.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));

        JButton cancelBtn = makeTextBtn("取消", AppColors.BG_TERTIARY, AppColors.TEXT_SECONDARY);
        cancelBtn.addActionListener(e -> dispose());

        syncBtn = makeTextBtn("同步資料", AppColors.ACCENT, Color.WHITE);
        syncBtn.addActionListener(e -> startSync());

        btnRow.add(cancelBtn);
        btnRow.add(syncBtn);

        root.add(header,  BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        root.add(btnRow,  BorderLayout.SOUTH);
        return root;
    }

    // ── 事件處理 ─────────────────────────────────────────────────────────────

    private void openBrowser() {
        try {
            Desktop.getDesktop().browse(new URI(LOGIN_URL));
            setStatus("已開啟瀏覽器，請登入後回到這裡貼上 Cookie。", AppColors.TEXT_SECONDARY);
        } catch (Exception e) {
            setStatus("無法開啟瀏覽器，請手動前往：" + LOGIN_URL, AppColors.DANGER);
        }
    }

    private void startSync() {
        String cookie = cookieArea.getText().trim();
        if (cookie.isEmpty()) {
            setStatus("請先貼上 Cookie！", AppColors.DANGER);
            cookieArea.requestFocus();
            return;
        }

        syncBtn.setEnabled(false);
        setStatus("正在驗證 Cookie 並同步資料，請稍候…", AppColors.TEXT_SECONDARY);

        // 在背景執行，避免 UI 凍結
        new Thread(() -> {
            try {
                // 解析姓名
                String name = TronclassService.parseName(cookie);

                // 同步待辦
                int added = TronclassService.syncTodos(cookie, currentTodos, saveCallback);

                SwingUtilities.invokeLater(() -> {
                    syncBtn.setEnabled(true);
                    if (name == null && added == -1) {
                        setStatus("Cookie 已失效或網路異常，請重新複製 Cookie 再試。", AppColors.DANGER);
                        return;
                    }
                    String displayName = name != null ? name : "（姓名解析失敗）";
                    String addedMsg    = added >= 0 ? "，同步 " + added + " 筆待辦" : "，待辦同步失敗";
                    setStatus("✓ 同步成功：" + displayName + addedMsg, AppColors.SUCCESS);

                    // 短暫延遲後關閉並回調
                    new Timer(1200, ev -> {
                        dispose();
                        if (callback != null)
                            callback.onSuccess(name, cookie, Math.max(added, 0));
                    }) {{ setRepeats(false); start(); }};
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    syncBtn.setEnabled(true);
                    String msg = ex.getMessage();
                    if ("SESSION_EXPIRED".equals(msg))
                        setStatus("Session 已失效，請重新登入並複製 Cookie。", AppColors.DANGER);
                    else
                        setStatus("同步失敗：" + msg, AppColors.DANGER);
                });
            }
        }).start();
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text);
        statusLabel.setForeground(color);
    }

    // ── UI 工廠 ───────────────────────────────────────────────────────────────

    private JLabel stepLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFonts.BODY_SMALL.deriveFont(Font.BOLD));
        l.setForeground(AppColors.TEXT_PRIMARY);
        return l;
    }

    private JLabel hintLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFonts.CAPTION);
        l.setForeground(AppColors.TEXT_TERTIARY);
        return l;
    }

    private JButton makeOpenBtn() {
        JButton b = new JButton("🌐  開啟 Tronclass 登入頁");
        b.setFont(AppFonts.BODY_SMALL);
        b.setBackground(AppColors.ACCENT_LIGHT);
        b.setForeground(AppColors.ACCENT);
        b.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(0xC5D0FA), 1, true),
            new EmptyBorder(7, 16, 7, 16)));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JButton makeTextBtn(String text, Color bg, Color fg) {
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

    private JButton makeCloseBtn() {
        JButton b = new JButton("×");
        b.setFont(new Font(AppFonts.BODY_MEDIUM.getFamily(), Font.PLAIN, 16));
        b.setForeground(AppColors.TEXT_TERTIARY);
        b.setBorder(new EmptyBorder(0, 8, 0, 4));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}