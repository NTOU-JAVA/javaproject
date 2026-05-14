package persistence;

import java.io.File;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import model.Course;
import model.Schedule;
import model.Task;
import model.TodoItem;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * XmlDataStore：集中處理主要資料模型的 XML 載入與儲存。
 */
public class XmlDataStore {
    private static final String TASKS_XML     = "data/tasks.xml";
    private static final String TODOS_XML     = "data/todos.xml";
    private static final String SCHEDULES_XML = "data/schedules.xml";

    public XmlDataStore() {
        new File("data").mkdirs();
    }

    public void loadTasks(List<Task> tasks) {
        try {
            File file = new File(TASKS_XML);
            if (!file.exists()) return;
            Document doc = parse(file);
            NodeList nl = doc.getElementsByTagName("task");
            for (int i = 0; i < nl.getLength(); i++) {
                Element el = (Element) nl.item(i);
                int    id       = parseInt(el, "id", 0);
                String title    = getText(el, "title", getText(el, "content", ""));
                String desc     = getText(el, "description", "");
                String date     = getText(el, "date", "");
                String time     = getText(el, "time", "");
                String category = getText(el, "category", "");
                boolean hasDeadline = !date.isEmpty();
                if (el.getElementsByTagName("hasDeadline").getLength() > 0) {
                    hasDeadline = Boolean.parseBoolean(getText(el, "hasDeadline", "true"));
                }

                Task t = new Task(id, title, desc, date, time, hasDeadline);
                t.setImportant(Boolean.parseBoolean(getText(el, "important", "false")));
                t.setCompleted(Boolean.parseBoolean(getText(el, "completed", "false")));
                t.setCategory(category);
                tasks.add(t);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadTodos(List<TodoItem> todos) {
        try {
            File file = new File(TODOS_XML);
            if (!file.exists()) return;
            Document doc = parse(file);
            NodeList nl = doc.getElementsByTagName("todo");
            for (int i = 0; i < nl.getLength(); i++) {
                Element el = (Element) nl.item(i);
                int    id       = parseInt(el, "id", 0);
                String title    = getText(el, "title", getText(el, "content", ""));
                String desc     = getText(el, "description", "");
                String rt       = getText(el, "reminderTime", "");
                String category = getText(el, "category", "");
                if (rt.isEmpty()) rt = null;

                TodoItem item = new TodoItem(id, title, desc, rt);
                item.setCompleted(Boolean.parseBoolean(getText(el, "completed", "false")));
                item.setCategory(category);
                todos.add(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadSchedules(List<Schedule> schedules) {
        try {
            File file = new File(SCHEDULES_XML);
            if (!file.exists()) return;
            Document doc = parse(file);
            NodeList schedNl = doc.getElementsByTagName("schedule");
            for (int i = 0; i < schedNl.getLength(); i++) {
                Element sEl  = (Element) schedNl.item(i);
                int    sid   = parseInt(sEl, "id", 0);
                String sName = getText(sEl, "name", "");
                boolean active = Boolean.parseBoolean(getText(sEl, "active", "false"));
                Schedule s = new Schedule(sid, sName);
                s.setActive(active);

                NodeList courseNl = sEl.getElementsByTagName("course");
                for (int j = 0; j < courseNl.getLength(); j++) {
                    Element cEl  = (Element) courseNl.item(j);
                    int    cid   = parseInt(cEl, "id", 0);
                    String cname = getText(cEl, "name", "");
                    String code  = getText(cEl, "code", "");
                    String loc   = getText(cEl, "location", "");
                    String prof  = getText(cEl, "professor", "");
                    String dept  = getText(cEl, "department", "");
                    String cyear = getText(cEl, "classYear", "");
                    int    day   = parseInt(cEl, "dayOfWeek", 1);
                    int    start = parseInt(cEl, "startPeriod", 1);
                    int    end   = parseInt(cEl, "endPeriod", 1);
                    String note  = getText(cEl, "note", "");

                    Course course = new Course(cid, cname, code, loc, prof, day, start, end, note);
                    course.setDepartment(dept);
                    course.setClassYear(cyear);
                    course.setColorIndex(parseInt(cEl, "colorIndex", -1));
                    course.setScheduleString(getText(cEl, "scheduleString", ""));
                    s.getCourses().add(course);
                }
                schedules.add(s);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveTasks(List<Task> tasks) {
        try {
            Document doc = newDocument();
            Element root = doc.createElement("taskData");
            doc.appendChild(root);
            for (Task t : tasks) {
                Element el = doc.createElement("task");
                root.appendChild(el);
                appendText(doc, el, "id",         String.valueOf(t.getId()));
                appendText(doc, el, "title",       t.getTitle());
                appendText(doc, el, "description", t.getDescription());
                appendText(doc, el, "date",        t.getDate());
                appendText(doc, el, "time",        t.getTime());
                appendText(doc, el, "hasDeadline", String.valueOf(t.hasDeadline()));
                appendText(doc, el, "important",   String.valueOf(t.isImportant()));
                appendText(doc, el, "completed",   String.valueOf(t.isCompleted()));
                appendText(doc, el, "category",    t.getCategory());
            }
            writeXML(doc, TASKS_XML);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveTodos(List<TodoItem> todos) {
        try {
            Document doc = newDocument();
            Element root = doc.createElement("todoData");
            doc.appendChild(root);
            for (TodoItem t : todos) {
                Element el = doc.createElement("todo");
                root.appendChild(el);
                appendText(doc, el, "id",          String.valueOf(t.getId()));
                appendText(doc, el, "title",        t.getTitle());
                appendText(doc, el, "description",  t.getDescription());
                appendText(doc, el, "reminderTime", t.getReminderTime() != null ? t.getReminderTime() : "");
                appendText(doc, el, "completed",    String.valueOf(t.isCompleted()));
                appendText(doc, el, "category",     t.getCategory());
            }
            writeXML(doc, TODOS_XML);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveSchedules(List<Schedule> schedules) {
        try {
            Document doc = newDocument();
            Element root = doc.createElement("scheduleData");
            doc.appendChild(root);
            for (Schedule s : schedules) {
                Element sEl = doc.createElement("schedule");
                root.appendChild(sEl);
                appendText(doc, sEl, "id",     String.valueOf(s.getId()));
                appendText(doc, sEl, "name",   s.getName());
                appendText(doc, sEl, "active", String.valueOf(s.isActive()));
                Element coursesEl = doc.createElement("courses");
                sEl.appendChild(coursesEl);
                for (Course c : s.getCourses()) {
                    Element cEl = doc.createElement("course");
                    coursesEl.appendChild(cEl);
                    appendText(doc, cEl, "id",             String.valueOf(c.getId()));
                    appendText(doc, cEl, "name",           c.getName());
                    appendText(doc, cEl, "code",           c.getCode());
                    appendText(doc, cEl, "location",       c.getLocation());
                    appendText(doc, cEl, "professor",      c.getProfessor());
                    appendText(doc, cEl, "department",     c.getDepartment());
                    appendText(doc, cEl, "classYear",      c.getClassYear());
                    appendText(doc, cEl, "dayOfWeek",      String.valueOf(c.getDayOfWeek()));
                    appendText(doc, cEl, "startPeriod",    String.valueOf(c.getStartPeriod()));
                    appendText(doc, cEl, "endPeriod",      String.valueOf(c.getEndPeriod()));
                    appendText(doc, cEl, "note",           c.getNote());
                    appendText(doc, cEl, "colorIndex",     String.valueOf(c.getColorIndex()));
                    appendText(doc, cEl, "scheduleString", c.getScheduleString());
                }
            }
            writeXML(doc, SCHEDULES_XML);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Document parse(File file) throws Exception {
        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = db.parse(file);
        doc.getDocumentElement().normalize();
        return doc;
    }

    private Document newDocument() throws Exception {
        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return db.newDocument();
    }

    private String getText(Element el, String tag, String defaultVal) {
        NodeList nl = el.getElementsByTagName(tag);
        if (nl.getLength() == 0) return defaultVal;
        String v = nl.item(0).getTextContent();
        return v != null ? v : defaultVal;
    }

    private int parseInt(Element el, String tag, int defaultVal) {
        try {
            return Integer.parseInt(getText(el, tag, String.valueOf(defaultVal)));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private void appendText(Document doc, Element parent, String tag, String value) {
        Element el = doc.createElement(tag);
        el.appendChild(doc.createTextNode(value != null ? value : ""));
        parent.appendChild(el);
    }

    private void writeXML(Document doc, String fileName) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(doc), new StreamResult(new File(fileName)));
    }
}
