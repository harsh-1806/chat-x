package dev.harsh.chatroom.server.cache;

import dev.harsh.chatroom.protocol.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for per-room message history cache.
 */
class MessageHistoryCacheTest {

    private MessageHistoryCache cache;

    @BeforeEach
    void setUp() {
        cache = new MessageHistoryCache(5, 60); // max 5 messages, 60 min TTL
    }

    @Test
    @DisplayName("Empty room returns empty history")
    void emptyRoomReturnsEmpty() {
        List<ChatMessage> history = cache.getHistory("nonexistent", 10);
        assertTrue(history.isEmpty());
    }

    @Test
    @DisplayName("Messages are stored and retrieved in order")
    void messagesInOrder() {
        cache.addMessage("room1", ChatMessage.chat("room1", "alice", "First"));
        cache.addMessage("room1", ChatMessage.chat("room1", "bob", "Second"));
        cache.addMessage("room1", ChatMessage.chat("room1", "alice", "Third"));

        List<ChatMessage> history = cache.getHistory("room1", 10);
        assertEquals(3, history.size());
        assertEquals("First", history.get(0).getContent());
        assertEquals("Second", history.get(1).getContent());
        assertEquals("Third", history.get(2).getContent());
    }

    @Test
    @DisplayName("Evicts oldest messages when capacity exceeded")
    void evictsOldest() {
        for (int i = 1; i <= 7; i++) {
            cache.addMessage("room1", ChatMessage.chat("room1", "user", "Msg " + i));
        }

        List<ChatMessage> history = cache.getHistory("room1", 10);
        assertEquals(5, history.size()); // max is 5
        assertEquals("Msg 3", history.get(0).getContent()); // 1 and 2 evicted
        assertEquals("Msg 7", history.get(4).getContent());
    }

    @Test
    @DisplayName("getHistory respects count limit")
    void respectsCountLimit() {
        for (int i = 0; i < 5; i++) {
            cache.addMessage("room1", ChatMessage.chat("room1", "user", "Msg " + i));
        }

        List<ChatMessage> history = cache.getHistory("room1", 2);
        assertEquals(2, history.size());
        assertEquals("Msg 3", history.get(0).getContent()); // last 2
        assertEquals("Msg 4", history.get(1).getContent());
    }

    @Test
    @DisplayName("Rooms are isolated from each other")
    void roomsIsolated() {
        cache.addMessage("room1", ChatMessage.chat("room1", "alice", "Room 1 msg"));
        cache.addMessage("room2", ChatMessage.chat("room2", "bob", "Room 2 msg"));

        assertEquals(1, cache.getHistory("room1", 10).size());
        assertEquals(1, cache.getHistory("room2", 10).size());
        assertEquals("Room 1 msg", cache.getHistory("room1", 10).get(0).getContent());
        assertEquals("Room 2 msg", cache.getHistory("room2", 10).get(0).getContent());
    }

    @Test
    @DisplayName("clearRoom removes all messages for that room")
    void clearRoom() {
        cache.addMessage("room1", ChatMessage.chat("room1", "user", "msg"));
        cache.clearRoom("room1");

        assertTrue(cache.getHistory("room1", 10).isEmpty());
    }

    @Test
    @DisplayName("getTotalCachedMessages counts across all rooms")
    void totalCachedMessages() {
        cache.addMessage("room1", ChatMessage.chat("room1", "user", "m1"));
        cache.addMessage("room1", ChatMessage.chat("room1", "user", "m2"));
        cache.addMessage("room2", ChatMessage.chat("room2", "user", "m3"));

        assertEquals(3, cache.getTotalCachedMessages());
    }
}
