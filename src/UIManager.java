import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * UIManager 負責建立與管理整體行事曆任務管理介面。
 */
public class UIManager {
    private final JFrame frame;
    private final List<TodoItem> tasks;
    private final List<TodoItem> todos;
    private final TodoPanel todoPanel;
    private final TaskManagerPanel taskManagerPanel;

    /**
     * 建構 UI 管理器，初始化主要視圖與保存回呼。
     *
     * @param frame 主視窗
     * @param tasks 任務項目列表
     * @param todos 代辦項目列表
     * @param saveTasksCallback 儲存任務的回呼
     * @param saveTodosCallback 儲存代辦的回呼
     */
    public UIManager(JFrame frame, List<TodoItem> tasks, List<TodoItem> todos, Runnable saveTasksCallback, Runnable saveTodosCallback) {
        this.frame = frame;
        this.tasks = tasks;
        this.todos = todos;
        this.todoPanel = new TodoPanel(todos, this::updateUI);
        this.taskManagerPanel = new TaskManagerPanel(tasks);
        initializeUI(saveTasksCallback, saveTodosCallback);
    }

    /**
     * 初始化視窗與元件，建立切換按鈕與卡片式佈局。
     *
     * @param saveTasksCallback 儲存任務的回呼
     * @param saveTodosCallback 儲存代辦的回呼
     */
    private void initializeUI(Runnable saveTasksCallback, Runnable saveTodosCallback) {
        frame.setTitle("行事曆任務管理");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(840, 560);
        frame.setLocationRelativeTo(null);

        JPanel contentPane = new JPanel(new BorderLayout(12, 12));
        contentPane.setBorder(new EmptyBorder(12, 12, 12, 12));
        frame.setContentPane(contentPane);

        JLabel titleLabel = new JLabel("代辦頁面與任務管理");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));
        contentPane.add(titleLabel, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("任務管理頁面", taskManagerPanel);
        tabs.addTab("代辦頁面", todoPanel);
        contentPane.add(tabs, BorderLayout.CENTER);

        updateUI();

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                saveTasksCallback.run();
                saveTodosCallback.run();
            }
        });
    }

    /**
     * 計算下一個代辦項目的唯一編號。
     *
     * @return 下一個可用的 ID
     */
    private int getNextId() {
        return todos.isEmpty() ? 1 : todos.stream().mapToInt(TodoItem::getId).max().orElse(0) + 1;
    }

    /**
     * 更新整體介面，包含代辦頁面與任務管理頁面。
     */
    public void updateUI() {
        todoPanel.updateTable();
        taskManagerPanel.refreshView();
    }

    /**
     * 顯示主視窗。
     */
    public void show() {
        frame.setVisible(true);
    }
}