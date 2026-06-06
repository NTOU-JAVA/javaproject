package service;

import java.io.File;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/**
 * CategoryManager：管理任務與代辦事項的分類。
 * 資料存在 data/categories.xml，首次啟動時自動建立預設分類。
 * 預設四個分類（學習、作業、考試、個人）受保護，不可重新命名或刪除。
 */
public class CategoryManager {

    public static final String ALL   = "全部";    // 虛擬分類，不存入 XML
    public static final String NONE  = "未分類";  // 沒有分類時的 fallback 顯示用

    private static final String XML_PATH = "data/categories.xml";

    static final List<String> PROTECTED_CATEGORIES =
            Collections.unmodifiableList(Arrays.asList("學習", "作業", "考試", "個人"));

    private static final List<String> DEFAULT_CATEGORIES =
            new ArrayList<>(PROTECTED_CATEGORIES);

    // 實際儲存的分類（不含「全部」）
    private final List<String> categories = new ArrayList<>();

    // 觀察者（分類更動時通知 UI）
    private final List<Runnable> listeners = new ArrayList<>();

    // 刪除分類時通知（傳入被刪除的分類名稱）
    private final List<java.util.function.Consumer<String>> removeListeners = new ArrayList<>();

    // 重新命名分類時通知（傳入 oldName→newName）
    private final List<java.util.function.BiConsumer<String,String>> renameListeners = new ArrayList<>();

    public CategoryManager() {
        load();
    }

    // 讀取
    private void load() {
        File f = new File(XML_PATH);
        if (!f.exists()) {
            categories.addAll(DEFAULT_CATEGORIES);
            save();
            return;
        }
        try {
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = db.parse(f);
            doc.getDocumentElement().normalize();
            NodeList nl = doc.getElementsByTagName("category");
            for (int i = 0; i < nl.getLength(); i++) {
                String name = nl.item(i).getTextContent().trim();
                if (!name.isEmpty() && !categories.contains(name)) {
                    categories.add(name);
                }
            }
            // 確保受保護分類一定存在（位置固定在最前面）
            for (int i = PROTECTED_CATEGORIES.size() - 1; i >= 0; i--) {
                String p = PROTECTED_CATEGORIES.get(i);
                categories.remove(p);
                categories.add(0, p);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (categories.isEmpty()) categories.addAll(DEFAULT_CATEGORIES);
        }
    }

    // 儲存
    public void save() {
        try {
            new File("data").mkdirs();
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = db.newDocument();
            Element root = doc.createElement("categories");
            doc.appendChild(root);
            for (String c : categories) {
                Element el = doc.createElement("category");
                el.appendChild(doc.createTextNode(c));
                root.appendChild(el);
            }
            Transformer tf = TransformerFactory.newInstance().newTransformer();
            tf.setOutputProperty(OutputKeys.INDENT, "yes");
            tf.transform(new DOMSource(doc), new StreamResult(new File(XML_PATH)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 查詢

    /** 回傳所有分類（不含「全部」虛擬項） */
    public List<String> getCategories() {
        return Collections.unmodifiableList(categories);
    }

    /** 回傳包含「全部」的清單，供 Tag 列使用 */
    public List<String> getAllOptions() {
        List<String> opts = new ArrayList<>();
        opts.add(ALL);
        opts.addAll(categories);
        return opts;
    }

    /** 判斷某分類是否受保護（不可刪除 / 重新命名） */
    public boolean isProtected(String name) {
        return PROTECTED_CATEGORIES.contains(name);
    }

    // 新增
    public boolean addCategory(String name) {
        name = name.trim();
        if (name.isEmpty() || categories.contains(name) || ALL.equals(name)) return false;
        categories.add(name);
        save();
        notifyListeners();
        return true;
    }

    // 刪除（受保護分類不可刪）
    public boolean removeCategory(String name) {
        if (isProtected(name)) return false;
        if (!categories.remove(name)) return false;
        save();
        for (java.util.function.Consumer<String> c : removeListeners) c.accept(name);
        notifyListeners();
        return true;
    }

    // 重新命名（受保護分類不可改）
    public boolean renameCategory(String oldName, String newName) {
        if (isProtected(oldName)) return false;
        newName = newName.trim();
        int idx = categories.indexOf(oldName);
        if (idx < 0 || newName.isEmpty() || categories.contains(newName)) return false;
        categories.set(idx, newName);
        save();
        for (java.util.function.BiConsumer<String,String> c : renameListeners) c.accept(oldName, newName);
        notifyListeners();
        return true;
    }

    // 觀察者
    public void addListener(Runnable r)    { listeners.add(r); }
    public void removeListener(Runnable r) { listeners.remove(r); }

    /** 當某分類被刪除時呼叫，傳入被刪除的分類名稱（供清空事項分類欄使用）。 */
    public void addRemoveListener(java.util.function.Consumer<String> c) { removeListeners.add(c); }

    /** 當某分類被重新命名時呼叫，傳入 (oldName, newName)。 */
    public void addRenameListener(java.util.function.BiConsumer<String,String> c) { renameListeners.add(c); }

    private void notifyListeners() {
        for (Runnable r : listeners) r.run();
    }
}
