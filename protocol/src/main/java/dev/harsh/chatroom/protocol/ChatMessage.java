package dev.harsh.chatroom.protocol;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable chat message that flows through the protocol.
 * <p>
 * Uses a builder pattern for flexible construction since different message types
 * require different fields.
 */
public final class ChatMessage {

    private final MessageType type;
    private final String sender;
    private final String room;
    private final String content;
    private final long timestamp;
    private final String sessionId;
    private final boolean success;
    private final int errorCode;
    private final String errorMessage;
    private final List<String> rooms;
    private final List<ChatMessage> history;
    private final Map<String, String> metadata;

    private ChatMessage(Builder builder) {
        this.type = builder.type;
        this.sender = builder.sender;
        this.room = builder.room;
        this.content = builder.content;
        this.timestamp = builder.timestamp;
        this.sessionId = builder.sessionId;
        this.success = builder.success;
        this.errorCode = builder.errorCode;
        this.errorMessage = builder.errorMessage;
        this.rooms = builder.rooms != null ? List.copyOf(builder.rooms) : List.of();
        this.history = builder.history != null ? List.copyOf(builder.history) : List.of();
        this.metadata = builder.metadata != null
                ? Collections.unmodifiableMap(new HashMap<>(builder.metadata))
                : Map.of();
    }

    // --- Getters ---

    public MessageType getType() { return type; }
    public String getSender() { return sender; }
    public String getRoom() { return room; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
    public String getSessionId() { return sessionId; }
    public boolean isSuccess() { return success; }
    public int getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public List<String> getRooms() { return rooms; }
    public List<ChatMessage> getHistory() { return history; }
    public Map<String, String> getMetadata() { return metadata; }

    // --- Factory Methods for common messages ---

    public static ChatMessage authRequest(String username, String password) {
        return new Builder(MessageType.AUTH_REQUEST)
                .sender(username)
                .content(password)
                .build();
    }

    public static ChatMessage authResponse(boolean success, String sessionId, String message) {
        return new Builder(MessageType.AUTH_RESPONSE)
                .success(success)
                .sessionId(sessionId)
                .content(message)
                .build();
    }

    public static ChatMessage chat(String room, String sender, String content) {
        return new Builder(MessageType.CHAT)
                .room(room)
                .sender(sender)
                .content(content)
                .build();
    }

    public static ChatMessage join(String room) {
        return new Builder(MessageType.JOIN)
                .room(room)
                .build();
    }

    public static ChatMessage leave(String room) {
        return new Builder(MessageType.LEAVE)
                .room(room)
                .build();
    }

    public static ChatMessage listRooms() {
        return new Builder(MessageType.LIST_ROOMS).build();
    }

    public static ChatMessage listRoomsResponse(List<String> rooms) {
        return new Builder(MessageType.LIST_ROOMS_RESPONSE)
                .rooms(rooms)
                .build();
    }

    public static ChatMessage historyRequest(String room, int count) {
        return new Builder(MessageType.HISTORY_REQUEST)
                .room(room)
                .metadata("count", String.valueOf(count))
                .build();
    }

    public static ChatMessage historyResponse(String room, List<ChatMessage> history) {
        return new Builder(MessageType.HISTORY_RESPONSE)
                .room(room)
                .history(history)
                .build();
    }

    public static ChatMessage ping() {
        return new Builder(MessageType.PING).build();
    }

    public static ChatMessage pong() {
        return new Builder(MessageType.PONG).build();
    }

    public static ChatMessage error(int code, String message) {
        return new Builder(MessageType.ERROR)
                .errorCode(code)
                .errorMessage(message)
                .build();
    }

    public static ChatMessage system(String room, String content) {
        return new Builder(MessageType.SYSTEM)
                .room(room)
                .content(content)
                .sender("SYSTEM")
                .build();
    }

    public static ChatMessage disconnect(String reason) {
        return new Builder(MessageType.DISCONNECT)
                .content(reason)
                .build();
    }

    // --- Builder ---

    public static Builder builder(MessageType type) {
        return new Builder(type);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ChatMessage{type=").append(type);
        if (sender != null) sb.append(", sender='").append(sender).append('\'');
        if (room != null) sb.append(", room='").append(room).append('\'');
        if (content != null) sb.append(", content='").append(content).append('\'');
        if (timestamp > 0) sb.append(", timestamp=").append(timestamp);
        sb.append('}');
        return sb.toString();
    }

    public static final class Builder {
        private final MessageType type;
        private String sender;
        private String room;
        private String content;
        private long timestamp;
        private String sessionId;
        private boolean success;
        private int errorCode;
        private String errorMessage;
        private List<String> rooms;
        private List<ChatMessage> history;
        private Map<String, String> metadata;

        public Builder(MessageType type) {
            this.type = type;
            this.timestamp = Instant.now().toEpochMilli();
        }

        public Builder sender(String sender) { this.sender = sender; return this; }
        public Builder room(String room) { this.room = room; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder success(boolean success) { this.success = success; return this; }
        public Builder errorCode(int errorCode) { this.errorCode = errorCode; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder rooms(List<String> rooms) { this.rooms = rooms; return this; }
        public Builder history(List<ChatMessage> history) { this.history = history; return this; }

        public Builder metadata(String key, String value) {
            if (this.metadata == null) {
                this.metadata = new HashMap<>();
            }
            this.metadata.put(key, value);
            return this;
        }

        public ChatMessage build() {
            return new ChatMessage(this);
        }
    }
}
