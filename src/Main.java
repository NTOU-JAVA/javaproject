import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Main：應用程式進入點，負責初始化資料與 UI。
 */
public class Main {
    private final List<Task>     tasks = new ArrayList<>();
    private final List<TodoItem> todos = new ArrayList<>();
    private MainFrame mainFrame;
    private static final String TASKS_XML = "data/tasks.xml";
    private static final String TODOS_XML = "data/todos.xml";

    public Main() {
        new File("data").mkdirs();
        loadTasksFromXML();
        loadTodosFromXML();
        mainFrame = new MainFrame(tasks, todos, this::saveTasksToXML, this::saveTodosToXML);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Main app = new Main();
            app.mainFrame.showWindow();
        });
    }

    // ── 載入 ──────────────────────────────────────────────────────────────
    private void loadTasksFromXML() {
        try {
            File file = new File(TASKS_XML);
            if (!file.exists()) return;
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(file);
            doc.getDocumentElement().normalize();
            NodeList nl = doc.getElementsByTagName("task");
            for (int i = 0; i < nl.getLength(); i++) {
                Element el = (Element) nl.item(i);
                int    id      = parseInt(el, "id", 0);
                String title   = getText(el, "title",
                                   getText(el, "content", "")); // 向下相容
                String desc    = getText(el, "description", "");
                String date    = getText(el, "date", "");
                String time    = getText(el, "time", "");
                boolean hasDeadline = !date.isEmpty();
                // 如果 XML 裡有 hasDeadline 欄位以它為準
                if (el.getElementsByTagName("hasDeadline").getLength() > 0)
                    hasDeadline = Boolean.parseBoolean(getText(el, "hasDeadline", "true"));

                Task t = new Task(id, title, desc, date, time, hasDeadline);
                t.setImportant(Boolean.parseBoolean(getText(el, "important", "false")));
                t.setCompleted(Boolean.parseBoolean(getText(el, "completed", "false")));
                tasks.add(t);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadTodosFromXML() {
        try {
            File file = new File(TODOS_XML);
            if (!file.exists()) return;
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(file);
            doc.getDocumentElement().normalize();
            NodeList nl = doc.getElementsByTagName("todo");
            for (int i = 0; i < nl.getLength(); i++) {
                Element el = (Element) nl.item(i);
                int    id      = parseInt(el, "id", 0);
                String title   = getText(el, "title",
                                   getText(el, "content", ""));
                String desc    = getText(el, "description", "");
                String rt      = getText(el, "reminderTime", "");
                if (rt.isEmpty()) rt = null;

                TodoItem item = new TodoItem(id, title, desc, rt);
                item.setCompleted(Boolean.parseBoolean(getText(el, "completed", "false")));
                todos.add(item);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── 儲存 ──────────────────────────────────────────────────────────────
    private void saveTasksToXML() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();
            Element root = doc.createElement("taskData");
            doc.appendChild(root);
            for (Task t : tasks) {
                Element el = doc.createElement("task");
                root.appendChild(el);
                appendText(doc, el, "id",          String.valueOf(t.getId()));
                appendText(doc, el, "title",        t.getTitle());
                appendText(doc, el, "description",  t.getDescription());
                appendText(doc, el, "date",         t.getDate());
                appendText(doc, el, "time",         t.getTime());
                appendText(doc, el, "hasDeadline",  String.valueOf(t.hasDeadline()));
                appendText(doc, el, "important",    String.valueOf(t.isImportant()));
                appendText(doc, el, "completed",    String.valueOf(t.isCompleted()));
            }
            writeXML(doc, TASKS_XML);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void saveTodosToXML() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();
            Element root = doc.createElement("todoData");
            doc.appendChild(root);
            for (TodoItem t : todos) {
                Element el = doc.createElement("todo");
                root.appendChild(el);
                appendText(doc, el, "id",           String.valueOf(t.getId()));
                appendText(doc, el, "title",         t.getTitle());
                appendText(doc, el, "description",   t.getDescription());
                appendText(doc, el, "reminderTime",  t.getReminderTime() != null ? t.getReminderTime() : "");
                appendText(doc, el, "completed",     String.valueOf(t.isCompleted()));
            }
            writeXML(doc, TODOS_XML);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── XML 工具 ──────────────────────────────────────────────────────────
    private String getText(Element el, String tag, String defaultVal) {
        NodeList nl = el.getElementsByTagName(tag);
        if (nl.getLength() == 0) return defaultVal;
        String v = nl.item(0).getTextContent();
        return v != null ? v : defaultVal;
    }

    private int parseInt(Element el, String tag, int defaultVal) {
        try { return Integer.parseInt(getText(el, tag, String.valueOf(defaultVal))); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private void appendText(Document doc, Element parent, String tag, String value) {
        Element el = doc.createElement(tag);
        el.appendChild(doc.createTextNode(value));
        parent.appendChild(el);
    }

    private void writeXML(Document doc, String fileName) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(doc), new StreamResult(new File(fileName)));
    }
}