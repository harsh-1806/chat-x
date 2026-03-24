package dev.harsh.chatroom.server.auth;

import dev.harsh.chatroom.server.connection.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active sessions mapping session IDs to connections.
 */
public final class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    /** sessionId -> connection */
    private final Map<String, ClientConnection> sessions = new ConcurrentHashMap<>();

    /**
     * Register a new session.
     */
    public void createSession(String sessionId, ClientConnection connection) {
        sessions.put(sessionId, connection);
        log.debug("Session created: {} for user {}", sessionId, connection.getUsername());
    }

    /**
     * Remove a session.
     */
    public void removeSession(String sessionId) {
        ClientConnection removed = sessions.remove(sessionId);
        if (removed != null) {
            log.debug("Session removed: {} for user {}", sessionId, removed.getUsername());
        }
    }

    /**
     * Get the connection for a session.
     */
    public ClientConnection getConnection(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Check if a session is active.
     */
    public boolean isActive(String sessionId) {
        ClientConnection conn = sessions.get(sessionId);
        return conn != null && conn.isConnected();
    }

    /**
     * Number of active sessions.
     */
    public int getActiveSessionCount() {
        return (int) sessions.values().stream().filter(ClientConnection::isConnected).count();
    }

    /**
     * Clear all sessions (shutdown).
     */
    public void clearAll() {
        sessions.clear();
    }
}
