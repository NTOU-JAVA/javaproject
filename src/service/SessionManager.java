package service;

import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * SessionManager: centralized Tronclass login state management.
 *
 * Responsibilities:
 *  - Keep the current cookie and user name
 *  - Provide logout() for active logout
 *  - Provide notifySessionExpired() for passive expiration from API responses
 *  - Let UI components observe state changes through listeners
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

    public State  getState()    { return state; }
    public String getUserName() { return userName; }
    public String getCookie()   { return cookie; }
    public boolean isLoggedIn() { return state == State.LOGGED_IN; }

    public void onLoginSuccess(String name, String cookie) {
        this.userName = name;
        this.cookie   = cookie;
        setState(State.LOGGED_IN);
    }

    public void logout() {
        this.userName = null;
        this.cookie   = null;
        setState(State.LOGGED_OUT);
    }

    public void notifySessionExpired() {
        if (state != State.LOGGED_IN) return;
        this.cookie = null;
        setState(State.EXPIRED);
    }

    public void addListener(SessionListener l)    { listeners.add(l); }
    public void removeListener(SessionListener l) { listeners.remove(l); }

    private void setState(State newState) {
        this.state = newState;
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
