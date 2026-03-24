package dev.harsh.chatroom.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the wire protocol codec — encode/decode round-trips,
 * edge cases, and malformed input handling.
 */
class MessageCodecTest {

    @Test
    @DisplayName("Round-trip: CHAT message preserves all fields")
    void roundTripChatMessage() {
        ChatMessage original = ChatMessage.chat("general", "harsh", "Hello, world!");

        String json = MessageCodec.toJson(original);
        ChatMessage decoded = MessageCodec.fromJson(json);

        assertEquals(MessageType.CHAT, decoded.getType());
        assertEquals("general", decoded.getRoom());
        assertEquals("harsh", decoded.getSender());
        assertEquals("Hello, world!", decoded.getContent());
        assertTrue(decoded.getTimestamp() > 0);
    }

    @Test
    @DisplayName("Round-trip: AUTH_REQUEST preserves username and password")
    void roundTripAuthRequest() {
        ChatMessage original = ChatMessage.authRequest("user1", "secret123");

        String json = MessageCodec.toJson(original);
        ChatMessage decoded = MessageCodec.fromJson(json);

        assertEquals(MessageType.AUTH_REQUEST, decoded.getType());
        assertEquals("user1", decoded.getSender());
        assertEquals("secret123", decoded.getContent());
    }

    @Test
    @DisplayName("Round-trip: AUTH_RESPONSE preserves success flag and sessionId")
    void roundTripAuthResponse() {
        ChatMessage original = ChatMessage.authResponse(true, "session-uuid-123", "Welcome!");

        String json = MessageCodec.toJson(original);
        ChatMessage decoded = MessageCodec.fromJson(json);

        assertEquals(MessageType.AUTH_RESPONSE, decoded.getType());
        assertTrue(decoded.isSuccess());
        assertEquals("session-uuid-123", decoded.getSessionId());
        assertEquals("Welcome!", decoded.getContent());
    }

    @Test
    @DisplayName("Round-trip: LIST_ROOMS_RESPONSE preserves room list")
    void roundTripListRooms() {
        ChatMessage original = ChatMessage.listRoomsResponse(List.of("general", "dev", "random"));

        String json = MessageCodec.toJson(original);
        ChatMessage decoded = MessageCodec.fromJson(json);

        assertEquals(MessageType.LIST_ROOMS_RESPONSE, decoded.getType());
        assertEquals(List.of("general", "dev", "random"), decoded.getRooms());
    }

    @Test
    @DisplayName("Round-trip: ERROR message preserves code and message")
    void roundTripError() {
        ChatMessage original = ChatMessage.error(429, "Rate limit exceeded");

        String json = MessageCodec.toJson(original);
        ChatMessage decoded = MessageCodec.fromJson(json);

        assertEquals(MessageType.ERROR, decoded.getType());
        assertEquals(429, decoded.getErrorCode());
        assertEquals("Rate limit exceeded", decoded.getErrorMessage());
    }

    @Test
    @DisplayName("Round-trip: PING and PONG messages")
    void roundTripPingPong() {
        ChatMessage ping = ChatMessage.ping();
        ChatMessage pong = ChatMessage.pong();

        assertEquals(MessageType.PING, MessageCodec.fromJson(MessageCodec.toJson(ping)).getType());
        assertEquals(MessageType.PONG, MessageCodec.fromJson(MessageCodec.toJson(pong)).getType());
    }

    @Test
    @DisplayName("Round-trip: HISTORY_RESPONSE with nested messages")
    void roundTripHistoryResponse() {
        List<ChatMessage> history = List.of(
                ChatMessage.chat("general", "alice", "First message"),
                ChatMessage.chat("general", "bob", "Second message"));
        ChatMessage original = ChatMessage.historyResponse("general", history);

        String json = MessageCodec.toJson(original);
        ChatMessage decoded = MessageCodec.fromJson(json);

        assertEquals(MessageType.HISTORY_RESPONSE, decoded.getType());
        assertEquals("general", decoded.getRoom());
        assertEquals(2, decoded.getHistory().size());
        assertEquals("alice", decoded.getHistory().get(0).getSender());
        assertEquals("bob", decoded.getHistory().get(1).getSender());
    }

    @Test
    @DisplayName("JSON escaping: handles special characters in content")
    void jsonEscapingSpecialChars() {
        ChatMessage original = ChatMessage.chat("general", "user",
                "Hello \"world\"!\nNew line\tand\ttabs\\backslash");

        String json = MessageCodec.toJson(original);
        ChatMessage decoded = MessageCodec.fromJson(json);

        assertEquals("Hello \"world\"!\nNew line\tand\ttabs\\backslash", decoded.getContent());
    }

    @Test
    @DisplayName("Binary encode/decode: length-prefix frame round-trip")
    void binaryFrameRoundTrip() throws IOException {
        ChatMessage original = ChatMessage.chat("dev", "harsh", "Binary test!");

        byte[] frame = MessageCodec.encode(original);

        // Verify header
        int length = ByteBuffer.wrap(frame, 0, 4).getInt();
        assertEquals(frame.length - 4, length);

        // Decode via stream
        ByteArrayInputStream bais = new ByteArrayInputStream(frame);
        ChatMessage decoded = MessageCodec.readFrom(bais);

        assertNotNull(decoded);
        assertEquals("dev", decoded.getRoom());
        assertEquals("harsh", decoded.getSender());
        assertEquals("Binary test!", decoded.getContent());
    }

    @Test
    @DisplayName("Stream: write and read multiple messages in sequence")
    void streamMultipleMessages() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ChatMessage msg1 = ChatMessage.chat("room1", "alice", "Hello");
        ChatMessage msg2 = ChatMessage.chat("room2", "bob", "World");
        ChatMessage msg3 = ChatMessage.ping();

        MessageCodec.writeTo(msg1, baos);
        MessageCodec.writeTo(msg2, baos);
        MessageCodec.writeTo(msg3, baos);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

        ChatMessage d1 = MessageCodec.readFrom(bais);
        ChatMessage d2 = MessageCodec.readFrom(bais);
        ChatMessage d3 = MessageCodec.readFrom(bais);

        assertEquals("Hello", d1.getContent());
        assertEquals("World", d2.getContent());
        assertEquals(MessageType.PING, d3.getType());
    }

    @Test
    @DisplayName("Stream: readFrom returns null on empty stream (EOF)")
    void readFromEmptyStream() throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        assertNull(MessageCodec.readFrom(bais));
    }

    @Test
    @DisplayName("Stream: readFrom throws on invalid frame length")
    void readFromInvalidFrameLength() {
        byte[] badFrame = ByteBuffer.allocate(4).putInt(-1).array();
        ByteArrayInputStream bais = new ByteArrayInputStream(badFrame);

        assertThrows(IOException.class, () -> MessageCodec.readFrom(bais));
    }

    @Test
    @DisplayName("Deserialization: missing type field throws")
    void missingTypeFieldThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageCodec.fromJson("{\"room\":\"general\"}"));
    }

    @Test
    @DisplayName("Encode: payload exceeding max frame size throws")
    void oversizedPayloadThrows() {
        String content = "x".repeat(ProtocolConstants.MAX_FRAME_SIZE + 1);
        ChatMessage msg = ChatMessage.chat("room", "user", content);

        assertThrows(IllegalArgumentException.class, () -> MessageCodec.encode(msg));
    }

    @Test
    @DisplayName("Round-trip: message with metadata")
    void roundTripMetadata() {
        ChatMessage original = ChatMessage.builder(MessageType.HISTORY_REQUEST)
                .room("general")
                .metadata("count", "50")
                .build();

        String json = MessageCodec.toJson(original);
        ChatMessage decoded = MessageCodec.fromJson(json);

        assertEquals("50", decoded.getMetadata().get("count"));
    }

    @Test
    @DisplayName("Round-trip: SYSTEM message")
    void roundTripSystemMessage() {
        ChatMessage original = ChatMessage.system("general", "User joined the room");

        String json = MessageCodec.toJson(original);
        ChatMessage decoded = MessageCodec.fromJson(json);

        assertEquals(MessageType.SYSTEM, decoded.getType());
        assertEquals("SYSTEM", decoded.getSender());
        assertEquals("User joined the room", decoded.getContent());
    }
}
