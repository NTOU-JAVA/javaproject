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
    private final List<TodoItem> tasks = new ArrayList<>();
    private final List<TodoItem> todos = new ArrayList<>();
    private UIManager uiManager;
    private static final String TASKS_XML_FILE = "tasks.xml";
    private static final String TODOS_XML_FILE = "todos.xml";

    /**
     * 建構 Main 物件，先載入 XML 資料，再建立 UI 管理器。
     */
    public Main() {
        loadTasksFromXML();
        loadTodosFromXML();
        uiManager = new UIManager(new JFrame(), tasks, todos, this::saveTasksToXML, this::saveTodosToXML);
    }

    /**
     * 應用程式主方法，啟動 Swing 事件執行緒並顯示主視窗。
     *
     * @param args 命令列參數
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Main app = new Main();
            app.uiManager.show();
        });
    }

    /**
     * 從 XML 檔案讀取任務資料，並加入內存的任務清單中。
     * 若 XML 檔不存在則保持清單為空。
     */
    private void loadTasksFromXML() {
        loadFromXML(TASKS_XML_FILE, tasks);
    }

    /**
     * 從 XML 檔案讀取代辦資料，並加入內存的代辦清單中。
     * 若 XML 檔不存在則保持清單為空。
     */
    private void loadTodosFromXML() {
        loadFromXML(TODOS_XML_FILE, todos);
    }

    private void loadFromXML(String fileName, List<TodoItem> list) {
        try {
            File file = new File(fileName);
            if (file.exists()) {
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(file);
                doc.getDocumentElement().normalize();

                NodeList nodeList = doc.getElementsByTagName("todo");
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Element element = (Element) nodeList.item(i);
                    int id = Integer.parseInt(element.getElementsByTagName("id").item(0).getTextContent());
                    String date = element.getElementsByTagName("date").item(0).getTextContent();
                    String time = "09:00";
                    if (element.getElementsByTagName("time").getLength() > 0) {
                        time = element.getElementsByTagName("time").item(0).getTextContent();
                    }
                    String content = element.getElementsByTagName("content").item(0).getTextContent();
                    list.add(new TodoItem(id, date, time, content));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 將目前任務清單儲存回 XML 檔案。
     */
    private void saveTasksToXML() {
        saveToXML(TASKS_XML_FILE, tasks);
    }

    /**
     * 將目前代辦清單儲存回 XML 檔案。
     */
    private void saveTodosToXML() {
        saveToXML(TODOS_XML_FILE, todos);
    }

    private void saveToXML(String fileName, List<TodoItem> list) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.newDocument();

            Element rootElement = doc.createElement("todoData");
            doc.appendChild(rootElement);

            for (TodoItem todo : list) {
                Element todoElement = doc.createElement("todo");
                rootElement.appendChild(todoElement);

                Element idElement = doc.createElement("id");
                idElement.appendChild(doc.createTextNode(String.valueOf(todo.getId())));
                todoElement.appendChild(idElement);

                Element dateElement = doc.createElement("date");
                dateElement.appendChild(doc.createTextNode(todo.getDate()));
                todoElement.appendChild(dateElement);

                Element contentElement = doc.createElement("content");
                contentElement.appendChild(doc.createTextNode(todo.getContent()));
                todoElement.appendChild(contentElement);

                Element timeElement = doc.createElement("time");
                timeElement.appendChild(doc.createTextNode(todo.getTime()));
                todoElement.appendChild(timeElement);
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(fileName));
            transformer.transform(source, result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}