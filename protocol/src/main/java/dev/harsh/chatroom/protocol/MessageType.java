package dev.harsh.chatroom.protocol;

/**
 * All message types supported by the chat protocol.
 */
public enum MessageType {

    // --- Authentication ---
    AUTH_REQUEST,
    AUTH_RESPONSE,

    // --- Room Management ---
    JOIN,
    LEAVE,
    LIST_ROOMS,
    LIST_ROOMS_RESPONSE,

    // --- Messaging ---
    CHAT,
    HISTORY_REQUEST,
    HISTORY_RESPONSE,

    // --- Connection Control ---
    PING,
    PONG,

    // --- System ---
    ERROR,
    SYSTEM,          // Server announcements (user joined, user left, etc.)
    DISCONNECT;

    /**
     * Case-insensitive lookup.
     */
    public static MessageType fromString(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown message type: " + value);
        }
    }
}
