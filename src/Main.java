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
 * Main 是應用程式進入點，負責初始化資料與 UI。
 */
public class Main {
    private final List<Task> tasks = new ArrayList<>();
    private final List<TodoItem> todos = new ArrayList<>();
    private AppUIManager uiManager;
    private static final String TASKS_XML_FILE = "data/tasks.xml";
    private static final String TODOS_XML_FILE = "data/todos.xml";

    public Main() {
        new File("data").mkdirs();
        loadTasksFromXML();
        loadTodosFromXML();
        uiManager = new AppUIManager(new JFrame(), tasks, todos, this::saveTasksToXML, this::saveTodosToXML);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Main app = new Main();
            app.uiManager.show();
        });
    }

    private void loadTasksFromXML() {
        try {
            File file = new File(TASKS_XML_FILE);
            if (!file.exists()) return;
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(file);
            doc.getDocumentElement().normalize();
            NodeList nodeList = doc.getElementsByTagName("task");
            for (int i = 0; i < nodeList.getLength(); i++) {
                Element el = (Element) nodeList.item(i);
                int id = Integer.parseInt(el.getElementsByTagName("id").item(0).getTextContent());
                String date = el.getElementsByTagName("date").item(0).getTextContent();
                String time = el.getElementsByTagName("time").item(0).getTextContent();
                String content = el.getElementsByTagName("content").item(0).getTextContent();
                Task task = new Task(id, date, time, content);
                // 載入重要標誌
                if (el.getElementsByTagName("important").getLength() > 0) {
                    boolean important = Boolean.parseBoolean(el.getElementsByTagName("important").item(0).getTextContent());
                    task.setImportant(important);
                }
                tasks.add(task);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadTodosFromXML() {
        try {
            File file = new File(TODOS_XML_FILE);
            if (!file.exists()) return;
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(file);
            doc.getDocumentElement().normalize();
            NodeList nodeList = doc.getElementsByTagName("todo");
            for (int i = 0; i < nodeList.getLength(); i++) {
                Element el = (Element) nodeList.item(i);
                int id = Integer.parseInt(el.getElementsByTagName("id").item(0).getTextContent());
                String content = el.getElementsByTagName("content").item(0).getTextContent();
                String reminderTime = null;
                if (el.getElementsByTagName("reminderTime").getLength() > 0) {
                    String rt = el.getElementsByTagName("reminderTime").item(0).getTextContent();
                    if (!rt.isEmpty()) reminderTime = rt;
                }
                boolean completed = false;
                if (el.getElementsByTagName("completed").getLength() > 0) {
                    completed = Boolean.parseBoolean(el.getElementsByTagName("completed").item(0).getTextContent());
                }
                TodoItem todo = new TodoItem(id, content, reminderTime);
                todo.setCompleted(completed);
                todos.add(todo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveTasksToXML() {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.newDocument();
            Element root = doc.createElement("taskData");
            doc.appendChild(root);
            for (Task task : tasks) {
                Element el = doc.createElement("task");
                root.appendChild(el);
                appendText(doc, el, "id", String.valueOf(task.getId()));
                appendText(doc, el, "date", task.getDate());
                appendText(doc, el, "time", task.getTime());
                appendText(doc, el, "content", task.getContent());
                appendText(doc, el, "important", String.valueOf(task.isImportant()));
            }
            writeXML(doc, TASKS_XML_FILE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveTodosToXML() {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.newDocument();
            Element root = doc.createElement("todoData");
            doc.appendChild(root);
            for (TodoItem todo : todos) {
                Element el = doc.createElement("todo");
                root.appendChild(el);
                appendText(doc, el, "id", String.valueOf(todo.getId()));
                appendText(doc, el, "content", todo.getContent());
                appendText(doc, el, "reminderTime", todo.getReminderTime() != null ? todo.getReminderTime() : "");
                appendText(doc, el, "completed", String.valueOf(todo.isCompleted()));
            }
            writeXML(doc, TODOS_XML_FILE);
        } catch (Exception e) {
            e.printStackTrace();
        }
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