package service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import model.TodoItem;
import org.jsoup.Jsoup;          // 引入 Jsoup
import org.jsoup.nodes.Document; // 引入 Jsoup 文件物件
import org.jsoup.nodes.Element;  // 引入 Jsoup 元素物件
import org.jsoup.select.Elements;// 引入 Jsoup 元素集合

/**
 * Tronclass 資料同步服務（已整合 Jsoup 優化 HTML 解析）。
 * 使用使用者提供的 Cookie 呼叫 Tronclass API，解析待辦事項後合併到本機 TodoItem 清單。
 */
public class TronclassService {

    private static final String BASE_URL = "https://tronclass.ntou.edu.tw";

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

    public static class SessionExpiredException extends Exception {
        public SessionExpiredException() { super("SESSION_EXPIRED"); }
        public SessionExpiredException(String msg) { super(msg); }
    }

    public static String parseName(String cookie) throws SessionExpiredException, Exception {
        String html = get(BASE_URL + "/user/index", cookie);
        if (html == null || html.length() < 100) return null;

        Pattern p1 = Pattern.compile("userCurrentName\\s*=\\s*'([^']+)'");
        Matcher m1 = p1.matcher(html);
        if (m1.find()) return m1.group(1).trim();

        Pattern p2 = Pattern.compile("\"userCurrentName\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m2 = p2.matcher(html);
        if (m2.find()) return m2.group(1).trim();

        return null;
    }

    public static boolean validateCookie(String cookie) {
        if (cookie == null || cookie.isEmpty()) return false;
        try {
            get(BASE_URL + "/user/index", cookie);
            return true;
        } catch (SessionExpiredException e) {
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    public static int syncTodos(String cookie, java.util.List<TodoItem> currentTodos, 
                                CategoryManager categoryManager, Runnable saveCallback) 
                                throws SessionExpiredException, Exception {
        
        String endpoint = findWorkingEndpoint(cookie);
        if (endpoint == null) {
            return -1;
        }

        String json = get(BASE_URL + endpoint, cookie);
        if (json == null) {
            return -1;
        }

        List<TronclassTodo> fetched = parseTodos(json);

        Set<String> existingTitles = new HashSet<>();
        int maxId = 0;
        for (TodoItem t : currentTodos) {
            existingTitles.add(t.getTitle());
            maxId = Math.max(maxId, t.getId());
        }

        int added = 0;
        int updated = 0;

        for (TronclassTodo tt : fetched) {
            TodoItem existingItem = null;
            for (TodoItem t : currentTodos) {
                if (t.getTitle().equals(tt.title)) {
                    existingItem = t;
                    break;
                }
            }

            // 情況 A：如果事項之前已經匯入過了
            if (existingItem != null) {
                if (existingItem.getDescription().isBlank()) {
                    String desc = "";
                    if (tt.courseId != null && tt.activityId != null) {
                        try {
                            desc = fetchTodoDescription(tt.courseId, tt.activityId, cookie);
                        } catch (Exception ignored) {}
                    }
                    
                    if (!desc.isBlank()) {
                        existingItem.setDescription(desc);
                        updated++;
                    }
                }
                continue; 
            }
            
            // 情況 B：全新事項
            maxId++;
            String mappedCategory = mapToCategory(tt.type);
            
            String desc = "";
            if (tt.courseId != null && tt.activityId != null) {
                try {
                    desc = fetchTodoDescription(tt.courseId, tt.activityId, cookie);
                } catch (Exception ignored) {}
            }
            
            TodoItem item = new TodoItem(maxId, tt.title, desc, formatDeadline(tt.deadline));
            item.setSourceUrl(tt.url != null ? tt.url : "");
            item.setCategory(mappedCategory);
            
            currentTodos.add(item);
            added++;
        }

        if ((added > 0 || updated > 0) && saveCallback != null) saveCallback.run();
        return (added + updated);
    }
    
    private static String mapToCategory(String tronType) {
        if (tronType == null) return "個人";
        String type = tronType.toLowerCase();
        if (type.contains("homework") || type.contains("作業") || type.contains("assignment")) return "作業";
        if (type.contains("exam") || type.contains("考試") || type.contains("quiz")) return "考試";
        if (type.contains("course") || type.contains("learning")) return "學習";
        return "個人"; 
    }

    private static String findWorkingEndpoint(String cookie) throws SessionExpiredException {
        for (String path : TODO_CANDIDATES) {
            try {
                String resp = get(BASE_URL + path, cookie);
                if (resp != null && resp.length() > 10 && (resp.contains("[") || resp.contains("\"data\""))) {
                    return path;
                }
            } catch (SessionExpiredException e) {
                throw e;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static List<TronclassTodo> parseTodos(String json) {
        List<TronclassTodo> result = new ArrayList<>();
        int arrayStart = json.indexOf("[");
        if (arrayStart == -1) return result;
        int depth = 0, objStart = -1;
        for (int i = arrayStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') { if (depth++ == 0) objStart = i; }
            else if (c == '}') {
                if (--depth == 0 && objStart != -1) {
                    TronclassTodo item = parseSingle(json.substring(objStart, i + 1));
                    if (item != null) result.add(item);
                    objStart = -1;
                }
            }
        }
        return result;
    }

    private static TronclassTodo parseSingle(String obj) {
        String title = first(obj, "title", "name", "subject");
        if (title == null) return null;
        for (String kw : FILTER_KEYWORDS) { if (title.contains(kw)) return null; }
        TronclassTodo item = new TronclassTodo();
        item.title    = title;
        item.type     = coalesce(first(obj, "type", "activity_type"), "其他");
        item.deadline = coalesce(first(obj, "deadline", "end_time"), null);
        item.courseId   = first(obj, "course_id");
        item.activityId = first(obj, "id");
        if (item.courseId != null && item.activityId != null) {
            item.url = BASE_URL + "/course/" + item.courseId + "/learning-activity/full-screen#/" + item.activityId;
        }
        return item;
    }

    private static String first(String obj, String... keys) {
        for (String key : keys) {
            String val = extractJsonString(obj, key);
            if (val != null && !val.equals("null")) return val;
        }
        return null;
    }

    private static String coalesce(String val, String fallback) { return val != null ? val : fallback; }

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
        if (fc == 'n') return null;
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
                try {
                    sb.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                    i += 6; continue;
                } catch (NumberFormatException ignored) {}
            }
            sb.append(s.charAt(i++));
        }
        return sb.toString();
    }

    private static String formatDeadline(String deadline) {
        if (deadline == null) return null;
        try {
            String[] parts = deadline.split("T");
            if (parts.length < 2) return deadline;
            String time = parts[1].replaceAll(":00Z$", "").replaceAll("Z$", "");
            if (time.length() > 5) time = time.substring(0, 5);
            return parts[0] + " " + time;
        } catch (Exception e) { return deadline; }
    }

    static String get(String urlString, String cookie) throws SessionExpiredException, Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(12_000);
        conn.setInstanceFollowRedirects(false);
        
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept", "application/json, text/plain, */*");
        conn.setRequestProperty("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7");
        conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        
        if (urlString.contains("/api/")) {
            conn.setRequestProperty("Referer", BASE_URL + "/user/index");
        }
        if (cookie != null && !cookie.isEmpty()) {
            conn.setRequestProperty("Cookie", cookie);
        }

        int code = conn.getResponseCode();

        if (code == 301 || code == 302) {
            String loc = conn.getHeaderField("Location");
            if (loc != null && (loc.contains("cas") || loc.contains("login"))) {
                throw new SessionExpiredException();
            }
            return null;
        }
        if (code == 401 || code == 403) {
            return null;
        }
        if (code != 200) return null;

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }

        return sb.toString();
    }
    
    /**
     * 已使用 Jsoup 優化重構的描述抓取函數
     */
    private static String fetchTodoDescription(String courseId, String activityId, String cookie) throws Exception {
        if (courseId == null || activityId == null) return "";
        
        String[] apiUrlCandidates = {
            BASE_URL + "/api/v2/courses/" + courseId + "/homeworks/" + activityId,
            BASE_URL + "/api/homeworks/" + activityId,
            BASE_URL + "/api/courses/" + courseId + "/homeworks/" + activityId,
            BASE_URL + "/api/activities/" + activityId,
            BASE_URL + "/api/v2/homeworks/" + activityId
        };
        
        String json = null;
        for (String apiUrl : apiUrlCandidates) {
            try {
                String resp = get(apiUrl, cookie);
                if (resp != null && !resp.isBlank() && !resp.contains("404")) {
                    json = resp;
                    break;
                }
            } catch (Exception ignored) {}
        }

        if (json == null || json.isBlank()) return "";

        String htmlContent = null;
        String[] possibleFields = {"\"description\"", "\"content\"", "\"html\"", "\"body\""};
        
        for (String field : possibleFields) {
            int fieldIdx = json.indexOf(field);
            if (fieldIdx == -1) continue;
            
            int colonIdx = json.indexOf(":", fieldIdx + field.length());
            if (colonIdx == -1) continue;
            
            int quoteStart = json.indexOf("\"", colonIdx + 1);
            if (quoteStart == -1) continue;
            
            StringBuilder valueSb = new StringBuilder();
            int p = quoteStart + 1;
            boolean foundEnd = false;
            
            while (p < json.length()) {
                char cur = json.charAt(p);
                if (cur == '"') {
                    int slashCount = 0;
                    int k = p - 1;
                    while (k >= quoteStart && json.charAt(k) == '\\') {
                        slashCount++;
                        k--;
                    }
                    if (slashCount % 2 == 0) {
                        foundEnd = true;
                        break;
                    }
                }
                valueSb.append(cur);
                p++;
            }
            
            if (foundEnd) {
                String candidate = valueSb.toString();
                if (!candidate.isBlank() && !candidate.equals("null")) {
                    htmlContent = candidate;
                    break;
                }
            }
        }

        if (htmlContent == null || htmlContent.isBlank()) return "";

        // 基礎的 JSON 跳脫字元還原
        htmlContent = decodeUnicode(htmlContent);
        htmlContent = htmlContent.replace("\\\"", "\"")
                                 .replace("\\t", "    ")
                                 .replace("\\\\", "\\")
                                 .replace("\\n", "\n");

        // ==================== Jsoup 改寫核心部分 ====================
        
        // 1. 使用 Jsoup 解析 HTML 字串
        Document doc = Jsoup.parse(htmlContent);
        
        // 2. 處理超連結：找出所有的 <a> 標籤，並在它的文字後面加上 (網址: ...)
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            String url = link.attr("href").trim();
            // 過濾掉無效的錨點或 javascript 連結
            if (!url.isEmpty() && !url.startsWith("#") && !url.startsWith("javascript:")) {
                // 在原本的超連結文字後面附註網址
                link.after(" (網址: " + url + ") ");
            }
        }
        
        // 3. 處理換行問題：Jsoup 預設的 text() 會把所有換行壓縮成空白。
        // 我們利用 Jsoup 的 OutputSettings 設定，讓它在輸出純文字時保留基本換行（如 <br>, <p> 等）
        doc.outputSettings(new Document.OutputSettings().prettyPrint(true));
        
        // 4. 取得清洗乾淨的純文字，並把過多的連續換行修正
        String cleanText = doc.text();
        
        // 5. 補償處理：因為 Tronclass 的內容可能包含特殊的 HTML 實體或跳脫，做最後修飾
        cleanText = cleanText.replaceAll("(?i)<br\\s*/?>", "\n") // 防禦性確保換行
                             .replace("\\n", "\n")
                             .replaceAll("\n{3,}", "\n\n");

        return cleanText.trim();
        // ==========================================================
    }

    static class TronclassTodo {
        String title; String type; String deadline; String courseId; String activityId; String url;
    }
}