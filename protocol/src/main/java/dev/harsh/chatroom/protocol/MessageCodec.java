package dev.harsh.chatroom.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encodes and decodes {@link ChatMessage} instances over a length-prefixed wire format.
 * <p>
 * Wire format:
 * <pre>
 * ┌──────────────┬──────────────────────────────────┐
 * │ 4-byte int   │ JSON payload (UTF-8)             │
 * │ (payload len)│                                  │
 * └──────────────┴──────────────────────────────────┘
 * </pre>
 * <p>
 * Uses a hand-rolled lightweight JSON serializer/deserializer to avoid external
 * dependencies in the protocol module. This keeps the protocol jar minimal and
 * dependency-free.
 */
public final class MessageCodec {

    private MessageCodec() {
        // utility class
    }

    // ==================== Encoding ====================

    /**
     * Encode a ChatMessage into a length-prefixed byte array ready for transmission.
     */
    public static byte[] encode(ChatMessage message) {
        String json = toJson(message);
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);

        if (payload.length > ProtocolConstants.MAX_FRAME_SIZE) {
            throw new IllegalArgumentException(
                    "Payload exceeds max frame size: " + payload.length + " > " + ProtocolConstants.MAX_FRAME_SIZE);
        }

        ByteBuffer buffer = ByteBuffer.allocate(ProtocolConstants.HEADER_SIZE + payload.length);
        buffer.putInt(payload.length);
        buffer.put(payload);
        return buffer.array();
    }

    /**
     * Write a ChatMessage directly to an OutputStream (for socket writing).
     */
    public static void writeTo(ChatMessage message, OutputStream out) throws IOException {
        byte[] frame = encode(message);
        out.write(frame);
        out.flush();
    }

    /**
     * Read a single ChatMessage from an InputStream (blocking).
     * Returns null if the stream has ended.
     */
    public static ChatMessage readFrom(InputStream in) throws IOException {
        // Read 4-byte length header
        byte[] header = readExact(in, ProtocolConstants.HEADER_SIZE);
        if (header == null) {
            return null; // Stream closed
        }

        int length = ByteBuffer.wrap(header).getInt();
        if (length <= 0 || length > ProtocolConstants.MAX_FRAME_SIZE) {
            throw new IOException("Invalid frame length: " + length);
        }

        // Read payload
        byte[] payload = readExact(in, length);
        if (payload == null) {
            throw new IOException("Unexpected end of stream while reading payload");
        }

        String json = new String(payload, StandardCharsets.UTF_8);
        return fromJson(json);
    }

    /**
     * Read exactly {@code length} bytes from the input stream.
     * Returns null if the stream is at EOF before any bytes are read.
     */
    private static byte[] readExact(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int totalRead = 0;
        while (totalRead < length) {
            int read = in.read(buffer, totalRead, length - totalRead);
            if (read == -1) {
                if (totalRead == 0) return null;
                throw new IOException("Unexpected end of stream after reading " + totalRead + " of " + length + " bytes");
            }
            totalRead += read;
        }
        return buffer;
    }

    // ==================== JSON Serialization (hand-rolled, no deps) ====================

    /**
     * Serialize a ChatMessage to a JSON string.
     */
    public static String toJson(ChatMessage msg) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');

        sb.append("\"type\":\"").append(msg.getType().name()).append('"');

        if (msg.getSender() != null) {
            sb.append(",\"sender\":\"").append(escapeJson(msg.getSender())).append('"');
        }
        if (msg.getRoom() != null) {
            sb.append(",\"room\":\"").append(escapeJson(msg.getRoom())).append('"');
        }
        if (msg.getContent() != null) {
            sb.append(",\"content\":\"").append(escapeJson(msg.getContent())).append('"');
        }
        if (msg.getTimestamp() > 0) {
            sb.append(",\"timestamp\":").append(msg.getTimestamp());
        }
        if (msg.getSessionId() != null) {
            sb.append(",\"sessionId\":\"").append(escapeJson(msg.getSessionId())).append('"');
        }

        // Only include success for AUTH_RESPONSE
        if (msg.getType() == MessageType.AUTH_RESPONSE) {
            sb.append(",\"success\":").append(msg.isSuccess());
        }

        if (msg.getErrorCode() != 0) {
            sb.append(",\"errorCode\":").append(msg.getErrorCode());
        }
        if (msg.getErrorMessage() != null) {
            sb.append(",\"errorMessage\":\"").append(escapeJson(msg.getErrorMessage())).append('"');
        }

        if (!msg.getRooms().isEmpty()) {
            sb.append(",\"rooms\":[");
            for (int i = 0; i < msg.getRooms().size(); i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(escapeJson(msg.getRooms().get(i))).append('"');
            }
            sb.append(']');
        }

        if (!msg.getHistory().isEmpty()) {
            sb.append(",\"history\":[");
            for (int i = 0; i < msg.getHistory().size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(toJson(msg.getHistory().get(i)));
            }
            sb.append(']');
        }

        if (!msg.getMetadata().isEmpty()) {
            sb.append(",\"metadata\":{");
            boolean first = true;
            for (Map.Entry<String, String> entry : msg.getMetadata().entrySet()) {
                if (!first) sb.append(',');
                sb.append('"').append(escapeJson(entry.getKey())).append("\":\"")
                  .append(escapeJson(entry.getValue())).append('"');
                first = false;
            }
            sb.append('}');
        }

        sb.append('}');
        return sb.toString();
    }

    /**
     * Deserialize a JSON string into a ChatMessage.
     */
    public static ChatMessage fromJson(String json) {
        Map<String, Object> map = parseJsonObject(json.trim());

        String typeStr = (String) map.get("type");
        if (typeStr == null) {
            throw new IllegalArgumentException("Missing 'type' field in message JSON");
        }
        MessageType type = MessageType.fromString(typeStr);

        ChatMessage.Builder builder = ChatMessage.builder(type);

        if (map.containsKey("sender")) builder.sender((String) map.get("sender"));
        if (map.containsKey("room")) builder.room((String) map.get("room"));
        if (map.containsKey("content")) builder.content((String) map.get("content"));
        if (map.containsKey("timestamp")) builder.timestamp(toLong(map.get("timestamp")));
        if (map.containsKey("sessionId")) builder.sessionId((String) map.get("sessionId"));
        if (map.containsKey("success")) builder.success((Boolean) map.get("success"));
        if (map.containsKey("errorCode")) builder.errorCode((int) toLong(map.get("errorCode")));
        if (map.containsKey("errorMessage")) builder.errorMessage((String) map.get("errorMessage"));

        if (map.containsKey("rooms")) {
            @SuppressWarnings("unchecked")
            List<Object> roomList = (List<Object>) map.get("rooms");
            List<String> rooms = new ArrayList<>();
            for (Object r : roomList) rooms.add((String) r);
            builder.rooms(rooms);
        }

        if (map.containsKey("history")) {
            @SuppressWarnings("unchecked")
            List<Object> histList = (List<Object>) map.get("history");
            List<ChatMessage> history = new ArrayList<>();
            for (Object h : histList) {
                @SuppressWarnings("unchecked")
                Map<String, Object> hMap = (Map<String, Object>) h;
                history.add(fromJsonMap(hMap));
            }
            builder.history(history);
        }

        if (map.containsKey("metadata")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) map.get("metadata");
            for (Map.Entry<String, Object> entry : meta.entrySet()) {
                builder.metadata(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        return builder.build();
    }

    private static ChatMessage fromJsonMap(Map<String, Object> map) {
        String typeStr = (String) map.get("type");
        MessageType type = MessageType.fromString(typeStr);
        ChatMessage.Builder builder = ChatMessage.builder(type);

        if (map.containsKey("sender")) builder.sender((String) map.get("sender"));
        if (map.containsKey("room")) builder.room((String) map.get("room"));
        if (map.containsKey("content")) builder.content((String) map.get("content"));
        if (map.containsKey("timestamp")) builder.timestamp(toLong(map.get("timestamp")));
        if (map.containsKey("sessionId")) builder.sessionId((String) map.get("sessionId"));

        return builder.build();
    }

    // ==================== Lightweight JSON Parser ====================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJsonObject(String json) {
        int[] pos = {0};
        Object result = parseValue(json, pos);
        if (result instanceof Map) {
            return (Map<String, Object>) result;
        }
        throw new IllegalArgumentException("Expected JSON object, got: " + result);
    }

    private static Object parseValue(String json, int[] pos) {
        skipWhitespace(json, pos);
        if (pos[0] >= json.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON");
        }

        char c = json.charAt(pos[0]);
        return switch (c) {
            case '{' -> parseObject(json, pos);
            case '[' -> parseArray(json, pos);
            case '"' -> parseString(json, pos);
            case 't', 'f' -> parseBoolean(json, pos);
            case 'n' -> parseNull(json, pos);
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield parseNumber(json, pos);
                }
                throw new IllegalArgumentException("Unexpected char '" + c + "' at position " + pos[0]);
            }
        };
    }

    private static Map<String, Object> parseObject(String json, int[] pos) {
        Map<String, Object> map = new HashMap<>();
        pos[0]++; // skip '{'
        skipWhitespace(json, pos);

        if (json.charAt(pos[0]) == '}') {
            pos[0]++;
            return map;
        }

        while (true) {
            skipWhitespace(json, pos);
            String key = parseString(json, pos);
            skipWhitespace(json, pos);
            expect(json, pos, ':');
            Object value = parseValue(json, pos);
            map.put(key, value);

            skipWhitespace(json, pos);
            char next = json.charAt(pos[0]);
            if (next == '}') {
                pos[0]++;
                return map;
            }
            expect(json, pos, ',');
        }
    }

    private static List<Object> parseArray(String json, int[] pos) {
        List<Object> list = new ArrayList<>();
        pos[0]++; // skip '['
        skipWhitespace(json, pos);

        if (json.charAt(pos[0]) == ']') {
            pos[0]++;
            return list;
        }

        while (true) {
            list.add(parseValue(json, pos));
            skipWhitespace(json, pos);
            char next = json.charAt(pos[0]);
            if (next == ']') {
                pos[0]++;
                return list;
            }
            expect(json, pos, ',');
        }
    }

    private static String parseString(String json, int[] pos) {
        expect(json, pos, '"');
        StringBuilder sb = new StringBuilder();
        while (pos[0] < json.length()) {
            char c = json.charAt(pos[0]++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                if (pos[0] >= json.length()) break;
                char esc = json.charAt(pos[0]++);
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        String hex = json.substring(pos[0], pos[0] + 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos[0] += 4;
                    }
                    default -> sb.append(esc);
                }
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("Unterminated string");
    }

    private static Number parseNumber(String json, int[] pos) {
        int start = pos[0];
        boolean isFloat = false;
        if (json.charAt(pos[0]) == '-') pos[0]++;
        while (pos[0] < json.length() && Character.isDigit(json.charAt(pos[0]))) pos[0]++;
        if (pos[0] < json.length() && json.charAt(pos[0]) == '.') {
            isFloat = true;
            pos[0]++;
            while (pos[0] < json.length() && Character.isDigit(json.charAt(pos[0]))) pos[0]++;
        }
        if (pos[0] < json.length() && (json.charAt(pos[0]) == 'e' || json.charAt(pos[0]) == 'E')) {
            isFloat = true;
            pos[0]++;
            if (pos[0] < json.length() && (json.charAt(pos[0]) == '+' || json.charAt(pos[0]) == '-')) pos[0]++;
            while (pos[0] < json.length() && Character.isDigit(json.charAt(pos[0]))) pos[0]++;
        }
        String numStr = json.substring(start, pos[0]);
        if (isFloat) return Double.parseDouble(numStr);
        long val = Long.parseLong(numStr);
        if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) return (int) val;
        return val;
    }

    private static Boolean parseBoolean(String json, int[] pos) {
        if (json.startsWith("true", pos[0])) {
            pos[0] += 4;
            return Boolean.TRUE;
        } else if (json.startsWith("false", pos[0])) {
            pos[0] += 5;
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("Expected boolean at position " + pos[0]);
    }

    private static Object parseNull(String json, int[] pos) {
        if (json.startsWith("null", pos[0])) {
            pos[0] += 4;
            return null;
        }
        throw new IllegalArgumentException("Expected null at position " + pos[0]);
    }

    private static void skipWhitespace(String json, int[] pos) {
        while (pos[0] < json.length() && Character.isWhitespace(json.charAt(pos[0]))) {
            pos[0]++;
        }
    }

    private static void expect(String json, int[] pos, char expected) {
        if (pos[0] >= json.length() || json.charAt(pos[0]) != expected) {
            throw new IllegalArgumentException(
                    "Expected '" + expected + "' at position " + pos[0] +
                    " but got '" + (pos[0] < json.length() ? json.charAt(pos[0]) : "EOF") + "'");
        }
        pos[0]++;
    }

    private static long toLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Double d) return d.longValue();
        if (value instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("Cannot convert to long: " + value);
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        StringBuilder sb = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
