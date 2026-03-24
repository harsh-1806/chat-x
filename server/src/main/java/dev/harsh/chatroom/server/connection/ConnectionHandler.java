package dev.harsh.chatroom.server.connection;

import dev.harsh.chatroom.protocol.ChatMessage;
import dev.harsh.chatroom.protocol.MessageType;
import dev.harsh.chatroom.protocol.ProtocolConstants;
import dev.harsh.chatroom.server.auth.AuthService;
import dev.harsh.chatroom.server.auth.SessionManager;
import dev.harsh.chatroom.server.cache.MessageHistoryCache;
import dev.harsh.chatroom.server.config.ServerConfig;
import dev.harsh.chatroom.server.metrics.MetricsRegistry;
import dev.harsh.chatroom.server.ratelimit.TokenBucketRateLimiter;
import dev.harsh.chatroom.server.room.ChatRoom;
import dev.harsh.chatroom.server.room.RoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Handles the full lifecycle of a single client connection.
 * Runs on a virtual thread — one instance per connected client.
 * <p>
 * Pipeline: read → decode → authenticate → rate-limit → route → respond
 */
public final class ConnectionHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ConnectionHandler.class);

    private final ClientConnection connection;
    private final ConnectionManager connectionManager;
    private final AuthService authService;
    private final SessionManager sessionManager;
    private final RoomManager roomManager;
    private final MessageHistoryCache historyCache;
    private final MetricsRegistry metrics;
    private final ServerConfig config;

    private TokenBucketRateLimiter rateLimiter;

    public ConnectionHandler(ClientConnection connection,
            ConnectionManager connectionManager,
            AuthService authService,
            SessionManager sessionManager,
            RoomManager roomManager,
            MessageHistoryCache historyCache,
            MetricsRegistry metrics,
            ServerConfig config) {
        this.connection = connection;
        this.connectionManager = connectionManager;
        this.authService = authService;
        this.sessionManager = sessionManager;
        this.roomManager = roomManager;
        this.historyCache = historyCache;
        this.metrics = metrics;
        this.config = config;
    }

    @Override
    public void run() {
        log.info("Handling connection: {} from {}", connection.getConnectionId(), connection.getRemoteAddress());

        try {
            // Main read loop
            while (connection.isConnected()) {
                ChatMessage message = connection.receive();
                if (message == null) {
                    log.debug("Client {} disconnected (EOF)", connection.getConnectionId());
                    break;
                }

                metrics.incrementMessagesReceived();

                try {
                    handleMessage(message);
                } catch (Exception e) {
                    log.error("Error handling message from {}: {}", connection.getConnectionId(), e.getMessage(), e);
                    metrics.incrementErrors();
                    sendError(500, "Internal server error");
                }
            }
        } catch (IOException e) {
            if (connection.isConnected()) {
                log.warn("I/O error for {}: {}", connection.getConnectionId(), e.getMessage());
            }
        } finally {
            cleanup();
        }
    }

    /**
     * Route a message to the appropriate handler based on its type.
     */
    private void handleMessage(ChatMessage message) throws IOException {
        // Allow PING/PONG and AUTH before authentication
        if (message.getType() == MessageType.PONG) {
            return; // Heartbeat response, nothing to do
        }
        if (message.getType() == MessageType.PING) {
            connection.send(ChatMessage.pong());
            return;
        }

        // Authentication gate
        if (connection.getState() == ClientConnection.State.CONNECTED) {
            if (message.getType() == MessageType.AUTH_REQUEST) {
                handleAuth(message);
            } else {
                sendError(401, "Not authenticated. Send AUTH_REQUEST first.");
            }
            return;
        }

        // Rate limiting (only for authenticated users)
        if (rateLimiter != null && !rateLimiter.tryConsume()) {
            metrics.incrementRateLimitHits();
            sendError(429, "Rate limit exceeded. Slow down.");
            log.warn("Rate limited user: {}", connection.getUsername());
            return;
        }

        // Route by message type
        switch (message.getType()) {
            case CHAT -> handleChat(message);
            case JOIN -> handleJoin(message);
            case LEAVE -> handleLeave(message);
            case LIST_ROOMS -> handleListRooms();
            case HISTORY_REQUEST -> handleHistoryRequest(message);
            case DISCONNECT -> {
                log.info("Client {} requested disconnect", connection.getUsername());
                connection.disconnect();
            }
            default -> sendError(400, "Unsupported message type: " + message.getType());
        }
    }

    // --- Message Handlers ---

    private void handleAuth(ChatMessage message) throws IOException {
        String username = message.getSender();
        String password = message.getContent();

        // Validate
        if (username == null || username.length() > ProtocolConstants.MAX_USERNAME_LENGTH) {
            connection.send(ChatMessage.authResponse(false, null, "Invalid username"));
            metrics.incrementAuthFailure();
            return;
        }

        // Check if username is already connected
        if (connectionManager.isUsernameConnected(username)) {
            connection.send(ChatMessage.authResponse(false, null, "Username already in use"));
            metrics.incrementAuthFailure();
            return;
        }

        // Authenticate
        String sessionId = authService.authenticate(username, password);
        if (sessionId == null) {
            connection.send(ChatMessage.authResponse(false, null, "Invalid credentials"));
            metrics.incrementAuthFailure();
            return;
        }

        // Success
        connection.authenticate(username, sessionId);
        connectionManager.bindUsername(username, connection);
        sessionManager.createSession(sessionId, connection);

        // Create per-client rate limiter
        this.rateLimiter = new TokenBucketRateLimiter(
                config.getRateLimitMessagesPerSecond(),
                config.getRateLimitBurstSize());

        connection.send(ChatMessage.authResponse(true, sessionId, "Welcome, " + username + "!"));
        metrics.incrementAuthSuccess();

        // Auto-join default room
        ChatRoom defaultRoom = roomManager.getOrCreateRoom(config.getDefaultRoom());
        if (defaultRoom != null) {
            defaultRoom.addMember(connection);
            // Send recent history
            List<ChatMessage> history = historyCache.getHistory(config.getDefaultRoom(), 20);
            if (!history.isEmpty()) {
                connection.send(ChatMessage.historyResponse(config.getDefaultRoom(), history));
            }
        }

        log.info("User '{}' authenticated and joined '{}'", username, config.getDefaultRoom());
    }

    private void handleChat(ChatMessage message) throws IOException {
        String room = message.getRoom();
        if (room == null || room.isBlank()) {
            sendError(400, "Room name is required for chat messages");
            return;
        }

        // Validate content length
        if (message.getContent() == null || message.getContent().length() > ProtocolConstants.MAX_CONTENT_LENGTH) {
            sendError(400, "Message content too long (max " + ProtocolConstants.MAX_CONTENT_LENGTH + " chars)");
            return;
        }

        ChatRoom chatRoom = roomManager.getRoom(room);
        if (chatRoom == null || !chatRoom.hasMember(connection)) {
            sendError(403, "You are not in room '" + room + "'");
            return;
        }

        // Build the broadcast message with sender info
        ChatMessage broadcastMsg = ChatMessage.chat(room, connection.getUsername(), message.getContent());

        // Cache the message
        historyCache.addMessage(room, broadcastMsg);

        // Broadcast to all room members (including sender for confirmation)
        chatRoom.broadcastToAll(broadcastMsg);
        metrics.incrementMessagesSent();
    }

    private void handleJoin(ChatMessage message) throws IOException {
        String room = message.getRoom();
        if (room == null || room.isBlank()) {
            sendError(400, "Room name is required");
            return;
        }
        if (room.length() > ProtocolConstants.MAX_ROOM_NAME_LENGTH) {
            sendError(400, "Room name too long (max " + ProtocolConstants.MAX_ROOM_NAME_LENGTH + " chars)");
            return;
        }

        ChatRoom chatRoom = roomManager.getOrCreateRoom(room);
        if (chatRoom == null) {
            sendError(503, "Cannot create room: maximum rooms reached");
            return;
        }

        chatRoom.addMember(connection);

        // Send acknowledgement + recent history
        connection.send(ChatMessage.system(room, "You joined '" + room + "'"));
        List<ChatMessage> history = historyCache.getHistory(room, 20);
        if (!history.isEmpty()) {
            connection.send(ChatMessage.historyResponse(room, history));
        }
    }

    private void handleLeave(ChatMessage message) throws IOException {
        String room = message.getRoom();
        if (room == null) {
            sendError(400, "Room name is required");
            return;
        }

        ChatRoom chatRoom = roomManager.getRoom(room);
        if (chatRoom == null || !chatRoom.hasMember(connection)) {
            sendError(404, "You are not in room '" + room + "'");
            return;
        }

        chatRoom.removeMember(connection);
        connection.send(ChatMessage.system(room, "You left '" + room + "'"));

        // Clean up empty non-default rooms
        roomManager.deleteRoom(room, config.getDefaultRoom());
    }

    private void handleListRooms() throws IOException {
        List<String> roomNames = roomManager.listRoomNames();
        connection.send(ChatMessage.listRoomsResponse(roomNames));
    }

    private void handleHistoryRequest(ChatMessage message) throws IOException {
        String room = message.getRoom();
        int count = 50; // Default
        if (message.getMetadata().containsKey("count")) {
            try {
                count = Integer.parseInt(message.getMetadata().get("count"));
                count = Math.min(count, 200); // Cap at 200
            } catch (NumberFormatException ignored) {
                // Use default
            }
        }

        List<ChatMessage> history = historyCache.getHistory(room, count);
        connection.send(ChatMessage.historyResponse(room, history));
    }

    // --- Utilities ---

    private void sendError(int code, String message) {
        try {
            connection.send(ChatMessage.error(code, message));
        } catch (IOException e) {
            log.warn("Failed to send error to {}: {}", connection.getConnectionId(), e.getMessage());
        }
    }

    /**
     * Clean up when a client disconnects.
     */
    private void cleanup() {
        String username = connection.getUsername();
        log.info("Cleaning up connection: {} (user={})", connection.getConnectionId(), username);

        // Leave all rooms
        for (String roomName : connection.getJoinedRooms()) {
            ChatRoom room = roomManager.getRoom(roomName);
            if (room != null) {
                room.removeMember(connection);
                roomManager.deleteRoom(roomName, config.getDefaultRoom());
            }
        }

        // Remove session
        if (connection.getSessionId() != null) {
            sessionManager.removeSession(connection.getSessionId());
        }

        // Unregister connection
        connectionManager.unregister(connection);
    }
}
