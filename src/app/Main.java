package app;

import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import model.Schedule;
import model.Task;
import model.TodoItem;
import persistence.XmlDataStore;
import service.CategoryManager;
import ui.MainFrame;

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

    public Main() {
        dataStore.loadTasks(tasks);
        dataStore.loadTodos(todos);
        dataStore.loadSchedules(schedules);
        mainFrame = new MainFrame(tasks, todos, schedules, categoryManager,
                this::saveTasksToXML, this::saveTodosToXML, this::saveSchedulesToXML);
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
}
