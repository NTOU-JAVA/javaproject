import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;

/**
 * CalendarPanel 顯示一週七天的代辦行事曆視圖。
 */
public class CalendarPanel extends JPanel {
    private static final String[] WEEK_DAYS = {
        "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"
    };

    private final JPanel[] dayPanels = new JPanel[WEEK_DAYS.length];

    /**
     * 建構一個週視圖面板，為每一天建立可顯示代辦內容的區塊。
     */
    public CalendarPanel() {
        setLayout(new GridLayout(1, WEEK_DAYS.length, 8, 8));
        setBorder(BorderFactory.createTitledBorder("一週行事曆"));

        for (int i = 0; i < WEEK_DAYS.length; i++) {
            JPanel dayPanel = new JPanel();
            dayPanel.setLayout(new BorderLayout());
            dayPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            dayPanel.setOpaque(true);
            dayPanel.setBackground(new Color(250, 250, 250));

            JLabel dayLabel = new JLabel("<html><b>" + WEEK_DAYS[i] + "</b></html>", SwingConstants.CENTER);
            dayPanel.add(dayLabel, BorderLayout.NORTH);

            JTextArea todoArea = new JTextArea();
            todoArea.setEditable(false);
            todoArea.setOpaque(false);
            todoArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            JScrollPane scrollPane = new JScrollPane(todoArea);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            dayPanel.add(scrollPane, BorderLayout.CENTER);

            dayPanels[i] = dayPanel;
            add(dayPanel);
        }
    }

    /**
     * 更新行事曆內容，依據代辦項目的日期將其加入對應天數區塊。
     *
     * @param todos 代辦項目列表
     */
    public void updateCalendar(List<TodoItem> todos) {
        for (int i = 0; i < WEEK_DAYS.length; i++) {
            List<String> dayTodos = new ArrayList<>();
            for (TodoItem todo : todos) {
                if (WEEK_DAYS[i].equals(todo.getDate())) {
                    dayTodos.add("• " + todo.getTime() + " " + todo.getContent());
                }
            }

            JTextArea todoArea = (JTextArea) ((JScrollPane) dayPanels[i].getComponent(1)).getViewport().getView();
            if (dayTodos.isEmpty()) {
                todoArea.setText("(無代辦事項)");
            } else {
                todoArea.setText(String.join("\n", dayTodos));
            }
        }
    }
}