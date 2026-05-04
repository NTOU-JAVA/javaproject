import java.util.ArrayList;
import java.util.List;

/**
 * Schedule 表示一份課表（例如「1131課表」）。
 */
public class Schedule {
    private int          id;
    private String       name;    // 課表名稱
    private boolean      active;  // 是否為目前顯示的課表
    private List<Course> courses;

    public Schedule() {
        this.courses = new ArrayList<>();
    }

    public Schedule(int id, String name) {
        this.id      = id;
        this.name    = name;
        this.active  = false;
        this.courses = new ArrayList<>();
    }

    public int          getId()                        { return id; }
    public void         setId(int id)                  { this.id = id; }

    public String       getName()                      { return name != null ? name : ""; }
    public void         setName(String name)           { this.name = name; }

    public boolean      isActive()                     { return active; }
    public void         setActive(boolean active)      { this.active = active; }

    public List<Course> getCourses()                   { return courses; }
    public void         setCourses(List<Course> c)     { this.courses = c; }
}