package dev.harsh.chatroom.protocol;

/**
 * Protocol constants shared between client and server.
 */
public final class ProtocolConstants {

    private ProtocolConstants() {
        // utility class
    }

    /** Maximum size of a single message payload in bytes (64 KB). */
    public static final int MAX_FRAME_SIZE = 64 * 1024;

    /** Size of the length prefix header in bytes. */
    public static final int HEADER_SIZE = 4;

    /** Protocol version. */
    public static final int PROTOCOL_VERSION = 1;

    /** Default server port. */
    public static final int DEFAULT_PORT = 9090;

    /** Default health check port. */
    public static final int DEFAULT_HEALTH_PORT = 8081;

    /** Default heartbeat interval in seconds. */
    public static final int HEARTBEAT_INTERVAL_SECONDS = 30;

    /** Default heartbeat timeout in seconds. */
    public static final int HEARTBEAT_TIMEOUT_SECONDS = 10;

    /** Maximum username length. */
    public static final int MAX_USERNAME_LENGTH = 32;

    /** Maximum room name length. */
    public static final int MAX_ROOM_NAME_LENGTH = 64;

    /** Maximum message content length (in characters). */
    public static final int MAX_CONTENT_LENGTH = 4096;

    /** Default room that all users join on connect. */
    public static final String DEFAULT_ROOM = "general";

    /** Charset used for encoding/decoding. */
    public static final String CHARSET = "UTF-8";
}
