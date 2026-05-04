import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

/**
 * Tronclass 待辦事項爬蟲（API 版本 v2）
 *
 * Cookie 來源：F12 → Network → 任一對 tronclass.ntou.edu.tw 的請求
 *              → Headers → Request Headers → Cookie 整行複製
 */
public class TronclassCrawler {

    private static final String BASE_URL = "https://tronclass.ntou.edu.tw";

    // 依序嘗試的待辦 API 候選路徑
    private static final String[] TODO_CANDIDATES = {
        "/api/todos",
        "/api/v2/todos",
        "/api/todo/list",
        "/api/activities/todo",
        "/api/user/todos",
        "/api/lms/todos",
        "/api/course/todos",
        "/api/notifications/todo",
        "/api/homepage/todos",
        "/lms/api/todos",
    };

    private static final List<String> FILTER_KEYWORDS =
        Arrays.asList("工程認證", "COVID", "COVID-19");

    private String cookie = "";

    // =========================================================================
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("========== Tronclass 待辦事項爬蟲 v2 ==========\n");
        System.out.println("Cookie 來源：F12 → Network → 任一對 tronclass.ntou.edu.tw 的請求");
        System.out.println("            → Headers → Request Headers → Cookie 整行複製\n");
        System.out.print("請貼上 Cookie > ");
        String cookieInput = scanner.nextLine().trim();

        if (cookieInput.isEmpty()) {
            System.err.println("✗ 未提供 Cookie，程式結束。");
            scanner.close();
            return;
        }

        TronclassCrawler crawler = new TronclassCrawler();
        crawler.cookie = cookieInput;

        // 步驟 1：找到可用的 todo API
        System.out.println("\n[步驟 1] 搜尋待辦事項 API...");
        String todoEndpoint = crawler.findWorkingEndpoint(TODO_CANDIDATES);

        if (todoEndpoint == null) {
            System.err.println("\n✗ 所有候選 API 路徑均無法取得資料。");
            System.out.println("\n請手動找出 API 路徑：");
            System.out.println("  1. F12 → Network → 勾選右上角【Fetch/XHR】過濾器");
            System.out.println("  2. 重新整理 Tronclass 首頁（F5）");
            System.out.println("  3. 找含有 'todo'、'activity'、'task' 的請求");
            System.out.println("  4. 將完整 URL 傳給我，我幫你加進程式裡");
            scanner.close();
            return;
        }

        System.out.println("  ✓ 找到待辦 API: " + todoEndpoint);

        // 步驟 2：取得並解析資料
        System.out.println("\n[步驟 2] 取得並解析待辦事項...");
        try {
            String json = crawler.get(BASE_URL + todoEndpoint);

            List<TodoItem> todos = parseTodos(json);
            printTodos(todos);
            saveTodosToXml(todos);
        } catch (Exception e) {
            System.err.println("  取得資料失敗: " + e.getMessage());
        }

        scanner.close();
    }

    // =========================================================================
    // 依序嘗試候選路徑，回傳第一個成功的路徑
    // =========================================================================
    private String findWorkingEndpoint(String[] candidates) {
        for (String path : candidates) {
            try {
                System.out.println("  嘗試: " + path);
                String resp = get(BASE_URL + path);
                if (resp != null && resp.length() > 10 &&
                    (resp.contains("[") || resp.contains("\"data\""))) {
                    return path;
                }
            } catch (Exception e) {
                // 繼續嘗試下一個
            }
        }
        return null;
    }

    // =========================================================================
    // 解析 JSON → TodoItem 列表
    // =========================================================================
    private static List<TodoItem> parseTodos(String json) {
        List<TodoItem> result = new ArrayList<>();
        int arrayStart = json.indexOf("[");
        if (arrayStart == -1) {
            System.out.println("  ⚠ 回傳 JSON 中未找到陣列，請查看 data/todos_raw.json");
            return result;
        }

        int depth = 0, objStart = -1;
        for (int i = arrayStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth++ == 0) objStart = i;
            } else if (c == '}') {
                if (--depth == 0 && objStart != -1) {
                    String obj = json.substring(objStart, i + 1);
                    TodoItem item = parseSingle(obj);
                    if (item != null) result.add(item);
                    objStart = -1;
                }
            }
        }
        return result;
    }

    private static TodoItem parseSingle(String obj) {
        String title = first(obj, "title", "name", "subject");
        if (title == null) return null;

        for (String kw : FILTER_KEYWORDS) {
            if (title.contains(kw)) {
                System.out.println("  ✗ 已過濾: " + title);
                return null;
            }
        }

        TodoItem item = new TodoItem();
        item.title    = title;
        item.type     = coalesce(first(obj, "type", "activity_type", "category"), "其他");
        item.deadline = coalesce(first(obj, "deadline", "end_time", "due_date", "end_at"), "無截止日期");
        return item;
    }

    private static String first(String obj, String... keys) {
        for (String key : keys) {
            String val = extractJsonString(obj, key);
            if (val != null && !val.equals("null")) return val;
        }
        return null;
    }

    private static String coalesce(String val, String fallback) {
        return val != null ? val : fallback;
    }

    private static String extractJsonString(String obj, String key) {
        String search = "\"" + key + "\"";
        int idx = obj.indexOf(search);
        if (idx == -1) return null;
        int colon = obj.indexOf(":", idx + search.length());
        if (colon == -1) return null;
        int vs = colon + 1;
        while (vs < obj.length() && obj.charAt(vs) == ' ') vs++;
        if (vs >= obj.length()) return null;
        char fc = obj.charAt(vs);
        if (fc == '"') {
            int ve = vs + 1;
            while (ve < obj.length()) {
                char c = obj.charAt(ve);
                if (c == '"' && obj.charAt(ve - 1) != '\\') break;
                ve++;
            }
            return decodeUnicode(obj.substring(vs + 1, ve));
        }
        if (fc == 'n') return null; // null
        int ve = vs;
        while (ve < obj.length() && ",}\n ".indexOf(obj.charAt(ve)) == -1) ve++;
        return obj.substring(vs, ve).trim();
    }

    private static String decodeUnicode(String s) {
        if (!s.contains("\\u")) return s;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            if (i + 5 < s.length() && s.charAt(i) == '\\' && s.charAt(i + 1) == 'u') {
                String hex = s.substring(i + 2, i + 6);
                try {
                    sb.append((char) Integer.parseInt(hex, 16));
                    i += 6;
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            sb.append(s.charAt(i++));
        }
        return sb.toString();
    }

    // =========================================================================
    // 輸出
    // =========================================================================
    private static void printTodos(List<TodoItem> todos) {
        System.out.println("\n========== 待辦事項 ==========");
        if (todos.isEmpty()) {
            System.out.println("（無待辦事項，或解析失敗 — 請查看 data/todos_raw.json）");
            return;
        }
        int i = 1;
        for (TodoItem t : todos) {
            System.out.printf("%d. 【%s】%s%n   截止：%s%n%n", i++, t.type, t.title, t.deadline);
        }
        System.out.println("✓ 共 " + todos.size() + " 個待辦事項");
    }

    private static void saveTodosToXml(List<TodoItem> todos) {
        StringBuilder xml = new StringBuilder(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<todoData>\n");
        int id = 1;
        for (TodoItem t : todos) {
            String reminder = formatDeadline(t.deadline);
            xml.append("    <todo>\n")
               .append("        <id>").append(id++).append("</id>\n")
               .append("        <title>").append(escapeXml(t.title)).append("</title>\n")
               .append("        <description/>\n")
               .append("        <reminderTime>").append(reminder).append("</reminderTime>\n")
               .append("        <completed>false</completed>\n")
               .append("    </todo>\n");
        }
        xml.append("</todoData>");
        saveToFile("data/todos.xml", xml.toString());
        System.out.println("✓ 已儲存至 data/todos.xml");
    }

    /** 將 2026-06-08T15:59:00Z 轉為 2026-06-08 15:59 */
    private static String formatDeadline(String deadline) {
        if (deadline == null || deadline.equals("無截止日期")) return "";
        try {
            // 格式：2026-06-08T15:59:00Z
            String[] parts = deadline.split("T");
            if (parts.length < 2) return deadline;
            String date = parts[0];
            String time = parts[1].replaceAll(":00Z$", "").replaceAll("Z$", "");
            // 取 HH:mm
            if (time.length() > 5) time = time.substring(0, 5);
            return date + " " + time;
        } catch (Exception e) {
            return deadline;
        }
    }

    private static void saveToFile(String path, String content) {
        try {
            File f = new File(path);
            f.getParentFile().mkdirs();
            Files.write(Paths.get(f.getAbsolutePath()), content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("儲存失敗 (" + path + "): " + e.getMessage());
        }
    }

    private static String escapeXml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&apos;");
    }

    // =========================================================================
    // HTTP GET（不跟隨重導，302 → CAS = session 失效）
    // =========================================================================
    private String get(String urlString) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8_000);
        conn.setReadTimeout(10_000);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setRequestProperty("Accept", "application/json, */*");
        conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        conn.setRequestProperty("Referer", BASE_URL + "/user/index");
        conn.setRequestProperty("Cookie", cookie);

        int code = conn.getResponseCode();
        if (code == 301 || code == 302) {
            String loc = conn.getHeaderField("Location");
            if (loc != null && loc.contains("cas")) {
                throw new RuntimeException("Session 已失效，被重導向至登入頁");
            }
            return null;
        }
        if (code != 200) return null;

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        return sb.toString();
    }

    // =========================================================================
    static class TodoItem {
        String title, type, deadline;
    }
}