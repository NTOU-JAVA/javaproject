import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * AppUIManager 負責建立與管理整體應用程式介面。
 */
public class AppUIManager {
    private final JFrame frame;
    private final CalendarPanel calendarPanel;
    private final TodoPanel todoPanel;

    /**
     * 建構 UI 管理器。
     *
     * @param frame              主視窗
     * @param tasks              任務項目列表
     * @param todos              代辦項目列表
     * @param saveTasksCallback  儲存任務的回呼
     * @param saveTodosCallback  儲存代辦的回呼
     */
    public AppUIManager(JFrame frame, List<Task> tasks, List<TodoItem> todos,
                        Runnable saveTasksCallback, Runnable saveTodosCallback) {
        this.frame = frame;
        this.calendarPanel = new CalendarPanel(tasks);
        // 傳入 saveTodosCallback 而非 this::updateUI，
        // 避免勾選完成後呼叫 updateUI 造成 updateTable 重排而跳位
        this.todoPanel = new TodoPanel(todos, saveTodosCallback);
        initializeUI(saveTasksCallback, saveTodosCallback);
    }

    private void initializeUI(Runnable saveTasksCallback, Runnable saveTodosCallback) {
        frame.setTitle("學生行程與任務管理系統");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 600);
        frame.setLocationRelativeTo(null);

        JPanel contentPane = new JPanel(new BorderLayout(12, 12));
        contentPane.setBorder(new EmptyBorder(12, 12, 12, 12));
        frame.setContentPane(contentPane);

        JLabel titleLabel = new JLabel("學生行程與任務管理系統");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));
        contentPane.add(titleLabel, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("任務管理", calendarPanel);
        tabs.addTab("代辦事項", todoPanel);
        contentPane.add(tabs, BorderLayout.CENTER);

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                saveTasksCallback.run();
                saveTodosCallback.run();
            }
        });
    }

    /**
     * 更新整體介面（僅更新行事曆，不重建代辦表格以免跳位）。
     */
    public void updateUI() {
        calendarPanel.updateCalendar();
    }

    /**
     * 顯示主視窗。
     */
    public void show() {
        frame.setVisible(true);
    }
}