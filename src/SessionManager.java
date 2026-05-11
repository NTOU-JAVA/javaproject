import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * SessionManager：集中管理 Tronclass 登入狀態。
 *
 * 職責：
 *  - 持有目前的 cookie 與使用者名稱
 *  - 提供 logout()（主動登出）
 *  - 提供 notifySessionExpired()（被動失效，如 API 回傳 SESSION_EXPIRED）
 *  - 讓 UI 元件透過 listener 監聽狀態變化
 */
public class SessionManager {

    public enum State { LOGGED_OUT, LOGGED_IN, EXPIRED }

    public interface SessionListener {
        void onStateChanged(State state, String userName);
    }

    private State  state    = State.LOGGED_OUT;
    private String userName = null;
    private String cookie   = null;

    private final List<SessionListener> listeners = new ArrayList<>();

    // ── 查詢 ────────────────────────────────────────────────────────────────

    public State  getState()    { return state; }
    public String getUserName() { return userName; }
    public String getCookie()   { return cookie; }
    public boolean isLoggedIn() { return state == State.LOGGED_IN; }

    // ── 登入成功時呼叫（由 TronclassLoginDialog 回調） ───────────────────────

    public void onLoginSuccess(String name, String cookie) {
        this.userName = name;
        this.cookie   = cookie;
        setState(State.LOGGED_IN);
    }

    // ── 主動登出 ─────────────────────────────────────────────────────────────

    public void logout() {
        this.userName = null;
        this.cookie   = null;
        setState(State.LOGGED_OUT);
    }

    // ── Cookie 失效（由後台執行緒偵測後，切回 EDT 呼叫） ─────────────────────

    public void notifySessionExpired() {
        if (state != State.LOGGED_IN) return; // 避免重複觸發
        this.cookie = null;
        setState(State.EXPIRED);
    }

    // ── 觀察者 ───────────────────────────────────────────────────────────────

    public void addListener(SessionListener l)    { listeners.add(l); }
    public void removeListener(SessionListener l) { listeners.remove(l); }

    private void setState(State newState) {
        this.state = newState;
        // 確保在 EDT 上通知 UI
        if (SwingUtilities.isEventDispatchThread()) {
            fireListeners();
        } else {
            SwingUtilities.invokeLater(this::fireListeners);
        }
    }

    private void fireListeners() {
        for (SessionListener l : listeners) l.onStateChanged(state, userName);
    }
}