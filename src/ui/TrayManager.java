package ui;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.AWTEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.AWTEventListener;
import java.awt.image.BufferedImage;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.Timer;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * 管理系統匣圖示、右鍵選單與桌面通知。
 * 主視窗關閉時可隱藏到系統匣，提醒通知被點擊後會回到對應任務或代辦。
 */
public class TrayManager {
    private final MainFrame mainFrame;
    private final Runnable exitCallback;
    private final Runnable toggleReminderCallback;
    private final Supplier<Boolean> pausedSupplier;

    private TrayIcon trayIcon;
    private JWindow menuWindow;
    private AWTEventListener outsideClickListener;
    private Timer autoCloseTimer;
    private int pointerOutsideTicks;
    private Runnable pendingNotificationAction;

    public TrayManager(MainFrame mainFrame, Runnable exitCallback,
                       Runnable toggleReminderCallback, Supplier<Boolean> pausedSupplier) {
        this.mainFrame = mainFrame;
        this.exitCallback = exitCallback;
        this.toggleReminderCallback = toggleReminderCallback;
        this.pausedSupplier = pausedSupplier;
    }

    public boolean install() {
        if (!SystemTray.isSupported()) return false;

        trayIcon = new TrayIcon(createTrayImage(), mainFrame.getTitle());
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> {
            Runnable action = pendingNotificationAction;
            pendingNotificationAction = null;
            if (action != null) {
                action.run();
            } else {
                mainFrame.restoreFromTray();
            }
        });
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    SwingUtilities.invokeLater(TrayManager.this::showTrayMenu);
                }
            }
        });

        try {
            SystemTray.getSystemTray().add(trayIcon);
            updatePauseState();
            return true;
        } catch (AWTException ex) {
            trayIcon = null;
            return false;
        }
    }

    public void displayMessage(String title, String message) {
        displayMessage(title, message, null);
    }

    public void displayMessage(String title, String message, Runnable onClick) {
        if (trayIcon != null) {
            pendingNotificationAction = onClick;
            trayIcon.displayMessage(mainFrame.getTitle() + " - " + title,
                    message, TrayIcon.MessageType.INFO);
        }
    }

    public boolean isInstalled() {
        return trayIcon != null;
    }

    public void updatePauseState() {
        hideTrayMenu();
    }

    public void remove() {
        hideTrayMenu();
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
    }

    private void showTrayMenu() {
        hideTrayMenu();

        menuWindow = new JWindow();
        menuWindow.setAlwaysOnTop(true);
        menuWindow.setFocusableWindowState(true);
        menuWindow.setAutoRequestFocus(true);
        menuWindow.setType(java.awt.Window.Type.POPUP);

        javax.swing.JPanel panel = new javax.swing.JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xD8D6D0)),
                new EmptyBorder(4, 4, 4, 4)));

        panel.add(menuButton("開啟主視窗", () -> {
            hideTrayMenu();
            mainFrame.restoreFromTray();
        }));
        panel.add(menuButton(pausedSupplier.get() ? "恢復提醒" : "暫停提醒", () -> {
            toggleReminderCallback.run();
            hideTrayMenu();
        }));
        panel.add(menuButton("結束程式", () -> {
            hideTrayMenu();
            exitCallback.run();
        }));

        menuWindow.setContentPane(panel);
        menuWindow.pack();

        Point p = MouseInfo.getPointerInfo().getLocation();
        int x = p.x - menuWindow.getWidth() - 12;
        int y = p.y - menuWindow.getHeight() - 12;
        menuWindow.setLocation(Math.max(0, x), Math.max(0, y));
        menuWindow.setVisible(true);
        menuWindow.toFront();
        menuWindow.requestFocusInWindow();
        installOutsideClickCloser();
        startAutoCloseTimer();
        SwingUtilities.invokeLater(() -> {
            if (menuWindow != null) {
                menuWindow.setAlwaysOnTop(true);
                menuWindow.toFront();
            }
        });
        menuWindow.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override public void windowLostFocus(java.awt.event.WindowEvent e) {
                hideTrayMenu();
            }
        });
    }

    private JButton menuButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(AppFonts.BODY_SMALL);
        button.setHorizontalAlignment(JButton.LEFT);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setBackground(Color.WHITE);
        button.setForeground(AppColors.TEXT_PRIMARY);
        button.setBorder(new EmptyBorder(7, 14, 7, 42));
        button.setFocusPainted(false);
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        Dimension preferred = button.getPreferredSize();
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                button.setBackground(AppColors.ACCENT_LIGHT);
                button.setForeground(AppColors.ACCENT);
            }

            @Override public void mouseExited(MouseEvent e) {
                button.setBackground(Color.WHITE);
                button.setForeground(AppColors.TEXT_PRIMARY);
            }

            @Override public void mousePressed(MouseEvent e) {
                button.setBackground(AppColors.ACCENT);
                button.setForeground(Color.WHITE);
            }

            @Override public void mouseReleased(MouseEvent e) {
                button.setBackground(AppColors.ACCENT_LIGHT);
                button.setForeground(AppColors.ACCENT);
            }
        });
        button.addActionListener(e -> action.run());
        return button;
    }

    private void hideTrayMenu() {
        uninstallOutsideClickCloser();
        stopAutoCloseTimer();
        if (menuWindow != null) {
            menuWindow.dispose();
            menuWindow = null;
        }
    }

    private void installOutsideClickCloser() {
        uninstallOutsideClickCloser();
        outsideClickListener = event -> {
            if (!(event instanceof MouseEvent) || menuWindow == null) return;
            MouseEvent mouseEvent = (MouseEvent) event;
            if (mouseEvent.getID() != MouseEvent.MOUSE_PRESSED) return;

            Point screenPoint = mouseEvent.getLocationOnScreen();
            Point menuLocation = menuWindow.getLocationOnScreen();
            java.awt.Rectangle bounds = new java.awt.Rectangle(
                    menuLocation.x, menuLocation.y, menuWindow.getWidth(), menuWindow.getHeight());
            if (!bounds.contains(screenPoint)) {
                SwingUtilities.invokeLater(this::hideTrayMenu);
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(
                outsideClickListener, AWTEvent.MOUSE_EVENT_MASK);
    }

    private void uninstallOutsideClickCloser() {
        if (outsideClickListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(outsideClickListener);
            outsideClickListener = null;
        }
    }

    private void startAutoCloseTimer() {
        stopAutoCloseTimer();
        pointerOutsideTicks = 0;
        autoCloseTimer = new Timer(250, e -> {
            if (menuWindow == null || !menuWindow.isShowing()) {
                stopAutoCloseTimer();
                return;
            }
            Point p = MouseInfo.getPointerInfo().getLocation();
            Point loc = menuWindow.getLocationOnScreen();
            java.awt.Rectangle expanded = new java.awt.Rectangle(
                    loc.x - 12, loc.y - 12, menuWindow.getWidth() + 24, menuWindow.getHeight() + 24);
            if (expanded.contains(p)) {
                pointerOutsideTicks = 0;
            } else if (++pointerOutsideTicks >= 4) {
                hideTrayMenu();
            }
        });
        autoCloseTimer.start();
    }

    private void stopAutoCloseTimer() {
        if (autoCloseTimer != null) {
            autoCloseTimer.stop();
            autoCloseTimer = null;
        }
    }

    private Image createTrayImage() {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x3B5BDB));
        g.fillRoundRect(1, 1, 14, 14, 4, 4);
        g.setColor(Color.WHITE);
        g.fillOval(4, 3, 8, 8);
        g.fillRect(7, 10, 2, 3);
        g.dispose();
        return image;
    }
}
