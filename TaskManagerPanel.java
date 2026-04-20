import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * TaskManagerPanel 保留上一個版本的任務管理頁面，包含行事曆與舊版待辦清單切換。
 */
public class TaskManagerPanel extends JPanel {
    private final CalendarPanel calendarPanel;
    private final LegacyTodoPanel legacyTodoPanel;
    private final CardLayout cardLayout;
    private final JPanel cardPanel;
    private final JButton switchButton;
    private final List<TodoItem> todos;

    public TaskManagerPanel(List<TodoItem> todos) {
        this.todos = todos;
        this.calendarPanel = new CalendarPanel();
        this.legacyTodoPanel = new LegacyTodoPanel(todos, this::refreshView, this::showCalendarView);

        setLayout(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        switchButton = new JButton("切換到任務頁面");
        switchButton.addActionListener(e -> toggleView());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(switchButton);
        add(buttonPanel, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.add(legacyTodoPanel, "todo");
        cardPanel.add(calendarPanel, "calendar");
        add(cardPanel, BorderLayout.CENTER);

        showCalendarView();
    }

    private void toggleView() {
        if (switchButton.getText().equals("切換到任務頁面")) {
            showTodoView();
        } else {
            showCalendarView();
        }
    }

    public void showCalendarView() {
        cardLayout.show(cardPanel, "calendar");
        switchButton.setText("切換到任務頁面");
        refreshView();
    }

    public void showTodoView() {
        cardLayout.show(cardPanel, "todo");
        switchButton.setText("切換到行事曆");
        refreshView();
    }

    public void refreshView() {
        legacyTodoPanel.updateTable();
        calendarPanel.updateCalendar(todos);
    }
}
