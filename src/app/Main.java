package app;

import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import model.Schedule;
import model.Task;
import model.TodoItem;
import persistence.XmlDataStore;
import service.CategoryManager;
import service.ReminderService;
import ui.MainFrame;
import ui.TrayManager;

/**
 * Main：應用程式進入點，負責初始化資料與 UI。
 */
public class Main {
    private final List<Task>     tasks     = new ArrayList<>();
    private final List<TodoItem> todos     = new ArrayList<>();
    private final List<Schedule> schedules = new ArrayList<>();
    private final CategoryManager categoryManager = new CategoryManager();
    private final XmlDataStore dataStore = new XmlDataStore();
    private MainFrame mainFrame;
    private ReminderService reminderService;
    private TrayManager trayManager;

    public Main() {
        dataStore.loadTasks(tasks);
        dataStore.loadTodos(todos);
        dataStore.loadSchedules(schedules);
        mainFrame = new MainFrame(tasks, todos, schedules, categoryManager,
                this::saveTasksToXML, this::saveTodosToXML, this::saveSchedulesToXML);

        reminderService = new ReminderService(tasks, todos, this::showReminderNotification,
                this::saveAndRefreshReminders);
        trayManager = new TrayManager(mainFrame, this::exitApplication,
                this::toggleReminderPause, reminderService::isPaused);

        boolean trayInstalled = trayManager.install();
        if (trayInstalled) {
            mainFrame.setCloseRequestHandler(this::hideToTray);
        } else {
            mainFrame.setCloseRequestHandler(this::exitApplication);
        }
        reminderService.start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Main app = new Main();
            app.mainFrame.showWindow();
        });
    }

    private void saveTasksToXML() {
        dataStore.saveTasks(tasks);
    }

    private void saveTodosToXML() {
        dataStore.saveTodos(todos);
    }

    private void saveSchedulesToXML() {
        dataStore.saveSchedules(schedules);
    }

    private void hideToTray() {
        saveTasksToXML();
        saveTodosToXML();
        saveSchedulesToXML();
        mainFrame.hideToTray();
        if (trayManager != null && trayManager.isInstalled()) {
            trayManager.displayMessage("程式仍在執行", "提醒功能會在系統匣中持續運作。");
        }
    }

    private void toggleReminderPause() {
        reminderService.togglePaused();
        if (trayManager != null) {
            trayManager.updatePauseState();
        }
    }

    private void saveAndRefreshReminders() {
        saveTasksToXML();
        saveTodosToXML();
        SwingUtilities.invokeLater(() -> {
            mainFrame.refreshReminderViews();
        });
    }

    private void showReminderNotification(ReminderService.ReminderNotification notification) {
        Runnable openTarget = () -> SwingUtilities.invokeLater(() -> {
            mainFrame.openReminderTarget(notification.getTargetType(), notification.getTargetId());
        });
        if (trayManager != null && trayManager.isInstalled()) {
            trayManager.displayMessage(notification.getTitle(), notification.getMessage(), openTarget);
            return;
        }
        SwingUtilities.invokeLater(() ->
            {
                JOptionPane.showMessageDialog(mainFrame, notification.getMessage(),
                        notification.getTitle(), JOptionPane.INFORMATION_MESSAGE);
                openTarget.run();
            }
        );
    }

    private void exitApplication() {
        if (reminderService != null) {
            reminderService.stop();
        }
        if (trayManager != null) {
            trayManager.remove();
        }
        mainFrame.exitApplication();
    }
}
