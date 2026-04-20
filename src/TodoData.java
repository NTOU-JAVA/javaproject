import java.util.ArrayList;
import java.util.List;

/**
 * TodoData 保存整個代辦清單資料。
 */
public class TodoData {
    private List<TodoItem> todos = new ArrayList<>();

    /**
     * 取得目前的代辦清單。
     *
     * @return 代辦項目列表
     */
    public List<TodoItem> getTodos() {
        return todos;
    }

    /**
     * 設定新的代辦清單。
     *
     * @param todos 代辦項目列表
     */
    public void setTodos(List<TodoItem> todos) {
        this.todos = todos;
    }
}