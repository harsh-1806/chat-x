package dev.harsh.chatroom.server.connection;

import dev.harsh.chatroom.protocol.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of all active client connections.
 */
public final class ConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);

    private final Map<String, ClientConnection> connectionsById = new ConcurrentHashMap<>();
    private final Map<String, ClientConnection> connectionsByUsername = new ConcurrentHashMap<>();
    private final int maxConnections;

    public ConnectionManager(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    /**
     * Register a new connection. Returns false if max connections reached.
     */
    public boolean register(ClientConnection connection) {
        if (connectionsById.size() >= maxConnections) {
            log.warn("Max connections reached ({}), rejecting {}", maxConnections, connection.getRemoteAddress());
            return false;
        }
        connectionsById.put(connection.getConnectionId(), connection);
        log.info("Connection registered: {} from {}", connection.getConnectionId(), connection.getRemoteAddress());
        return true;
    }

    /**
     * Map a username to a connection after authentication.
     */
    public void bindUsername(String username, ClientConnection connection) {
        connectionsByUsername.put(username, connection);
        log.debug("Username '{}' bound to connection {}", username, connection.getConnectionId());
    }

    /**
     * Check if a username is already connected.
     */
    public boolean isUsernameConnected(String username) {
        ClientConnection existing = connectionsByUsername.get(username);
        return existing != null && existing.isConnected();
    }

    /**
     * Remove a connection from all registries and close it.
     */
    public void unregister(ClientConnection connection) {
        connectionsById.remove(connection.getConnectionId());
        if (connection.getUsername() != null) {
            connectionsByUsername.remove(connection.getUsername());
        }
        connection.disconnect();
        log.info("Connection unregistered: {} (user={})", connection.getConnectionId(), connection.getUsername());
    }

    /**
     * Get a connection by username.
     */
    public ClientConnection getByUsername(String username) {
        return connectionsByUsername.get(username);
    }

    /**
     * Get a connection by its ID.
     */
    public ClientConnection getById(String connectionId) {
        return connectionsById.get(connectionId);
    }

    /**
     * All active connections.
     */
    public Collection<ClientConnection> getAllConnections() {
        return connectionsById.values();
    }

    /**
     * Number of active connections.
     */
    public int getActiveCount() {
        return connectionsById.size();
    }

    /**
     * Broadcast a message to a specific set of connections, skipping the sender.
     */
    public void broadcast(ChatMessage message, Collection<ClientConnection> targets, ClientConnection exclude) {
        for (ClientConnection conn : targets) {
            if (conn == exclude || !conn.isConnected())
                continue;
            try {
                conn.send(message);
            } catch (IOException e) {
                log.warn("Failed to send to {}: {}", conn.getConnectionId(), e.getMessage());
                unregister(conn);
            }
        }
    }

    /**
     * Disconnect and unregister all connections. Used during shutdown.
     */
    public void disconnectAll() {
        log.info("Disconnecting all {} connections", connectionsById.size());
        for (ClientConnection conn : connectionsById.values()) {
            try {
                conn.send(ChatMessage.disconnect("Server shutting down"));
            } catch (IOException ignored) {
                // Best effort
            }
            conn.disconnect();
        }
        connectionsById.clear();
        connectionsByUsername.clear();
    }
}
